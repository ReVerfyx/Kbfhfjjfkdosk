import os, re, secrets, time, uuid, requests, hashlib, base64
from functools import wraps
from pathlib import Path
from io import BytesIO

from flask import (
    Flask, render_template, request, redirect, url_for, session, abort,
    jsonify, Response, flash
)
from PIL import Image, ImageDraw, ImageFont
from werkzeug.security import generate_password_hash, check_password_hash
from db import db, init_db

APP_DIR = Path(__file__).resolve().parent
PUBLIC_URL = os.getenv("MW_PUBLIC_URL", "https://mellstroy.work.gd").rstrip("/")
UPLOAD_DIR = APP_DIR / "static" / "uploads"
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

app = Flask(__name__)
app.secret_key = os.environ["MW_SECRET_KEY"]
app.config.update(
    SESSION_COOKIE_HTTPONLY=True,
    SESSION_COOKIE_SAMESITE="Lax",
    SESSION_COOKIE_SECURE=os.getenv("MW_HTTPS", "0") == "1",
    MAX_CONTENT_LENGTH=6 * 1024 * 1024,
)
init_db()

USERNAME_RE = re.compile(r"^[A-Za-zА-Яа-я0-9_.-]{3,24}$")
ALLOWED_STATUS = {"checking", "ok", "alert", "possibly_detained", "confirmed_detained", "storm", "strange"}
MELLAI_KEYS = ("mellstroy", "меллстрой", "мелл", "бурим", "андрей")
_login_fail = {}

def csrf_token():
    token = session.get("_csrf")
    if not token:
        token = secrets.token_urlsafe(24)
        session["_csrf"] = token
    return token

@app.context_processor
def is_mobile_request():
    forced = request.args.get("view")
    if forced == "mobile":
        return True
    if forced == "desktop":
        return False
    ua = (request.headers.get("User-Agent") or "").lower()
    return any(x in ua for x in ("android", "iphone", "ipod", "mobile"))

def device_template(name):
    suffix = "mobile" if is_mobile_request() else "desktop"
    return f"{name}_{suffix}.html"

@app.context_processor
def globals_for_templates():
    with db() as con:
        site_status = con.execute("SELECT * FROM status WHERE id=1").fetchone()
    canonical = PUBLIC_URL + (request.path if request.path.startswith("/") else "/" + request.path)
    return {
        "me": current_user(),
        "csrf_token": csrf_token,
        "site_status": site_status,
        "public_url": PUBLIC_URL,
        "canonical_url": canonical,
        "is_mobile": is_mobile_request(),
        "base_template": "base_mobile.html" if is_mobile_request() else "base_desktop.html",
    }

@app.after_request
def security_headers(response):
    response.headers.setdefault("X-Content-Type-Options", "nosniff")
    response.headers.setdefault("X-Frame-Options", "SAMEORIGIN")
    response.headers.setdefault("Referrer-Policy", "strict-origin-when-cross-origin")
    response.headers.setdefault("Permissions-Policy", "geolocation=(), camera=(), microphone=()")
    return response

@app.before_request
def csrf_guard():
    if request.path.startswith("/api/v1/"):
        return
    if request.method == "POST":
        sent = request.form.get("_csrf") or request.headers.get("X-CSRF-Token")
        expected = session.get("_csrf")
        if not expected or not sent or not secrets.compare_digest(expected, sent):
            abort(400, "CSRF token mismatch")

def current_user():
    uid = session.get("uid")
    if not uid:
        return None
    with db() as con:
        return con.execute(
            "SELECT id,username,is_admin,created_at FROM users WHERE id=?", (uid,)
        ).fetchone()

def api_token_hash(token):
    return hashlib.sha256(token.encode("utf-8")).hexdigest()

def issue_api_token(user_id):
    token = secrets.token_urlsafe(36)
    now = int(time.time())
    expires = now + 60 * 60 * 24 * 30
    with db() as con:
        con.execute("DELETE FROM api_tokens WHERE expires_at < ?", (now,))
        con.execute(
            "INSERT INTO api_tokens(user_id,token_hash,created_at,expires_at) VALUES(?,?,?,?)",
            (user_id, api_token_hash(token), now, expires)
        )
    return token, expires

def api_user():
    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        return None
    token = header[7:].strip()
    if not token:
        return None
    now = int(time.time())
    with db() as con:
        return con.execute(
            """SELECT u.id,u.username,u.is_admin,u.created_at,t.expires_at
               FROM api_tokens t JOIN users u ON u.id=t.user_id
               WHERE t.token_hash=? AND t.expires_at>?""",
            (api_token_hash(token), now)
        ).fetchone()

def api_auth_required(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        u = api_user()
        if not u:
            return jsonify(error="unauthorized"), 401
        return fn(u, *args, **kwargs)
    return wrapper

def post_to_json(row):
    image_url = None
    if row["image_path"]:
        image_url = PUBLIC_URL + "/static/" + row["image_path"]
    keys = set(row.keys())
    return {
        "id": row["id"],
        "username": row["username"],
        "body": row["body"],
        "image_url": image_url,
        "parent_id": row["parent_id"],
        "views": row["views"],
        "likes": row["likes_count"],
        "replies": row["replies_count"],
        "reposts": row["reposts_count"] if "reposts_count" in keys else 0,
        "liked": bool(row["liked"]) if "liked" in keys else False,
        "reposted": bool(row["reposted"]) if "reposted" in keys else False,
        "created_at": row["created_at"],
    }

def api_feed_rows(where_sql="WHERE p.parent_id IS NULL", params=(), viewer_id=-1, limit=60):
    sql = f"""
      SELECT p.*,u.username,
      (SELECT COUNT(*) FROM likes l WHERE l.post_id=p.id) likes_count,
      (SELECT COUNT(*) FROM posts r WHERE r.parent_id=p.id) replies_count,
      (SELECT COUNT(*) FROM reposts rp WHERE rp.post_id=p.id) reposts_count,
      EXISTS(SELECT 1 FROM likes l2 WHERE l2.post_id=p.id AND l2.user_id=?) liked,
      EXISTS(SELECT 1 FROM reposts rp2 WHERE rp2.post_id=p.id AND rp2.user_id=?) reposted
      FROM posts p JOIN users u ON u.id=p.user_id
      {where_sql}
      ORDER BY p.created_at DESC LIMIT ?
    """
    with db() as con:
        return con.execute(sql, (viewer_id, viewer_id, *params, limit)).fetchall()

def make_api_captcha():
    now = int(time.time())
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    answer = "".join(secrets.choice(alphabet) for _ in range(5))
    challenge_id = secrets.token_urlsafe(18)
    answer_hash = hashlib.sha256(answer.encode("utf-8")).hexdigest()

    with db() as con:
        con.execute("DELETE FROM api_captchas WHERE expires_at < ?", (now,))
        con.execute(
            "INSERT INTO api_captchas(id,answer_hash,expires_at) VALUES(?,?,?)",
            (challenge_id, answer_hash, now + 300)
        )

    img = Image.new("RGB", (520, 160), (15, 18, 28))
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 72)
    except Exception:
        font = ImageFont.load_default()

    for _ in range(24):
        x1, y1 = secrets.randbelow(520), secrets.randbelow(160)
        x2, y2 = secrets.randbelow(520), secrets.randbelow(160)
        shade = 55 + secrets.randbelow(70)
        draw.line((x1, y1, x2, y2), fill=(shade, shade, 100 + secrets.randbelow(90)), width=2)

    for i, ch in enumerate(answer):
        x = 55 + i * 82 + secrets.randbelow(14)
        y = 26 + secrets.randbelow(24)
        draw.text((x, y), ch, font=font, fill=(235, 237, 255))

    buf = BytesIO()
    img.save(buf, "PNG", optimize=True)
    encoded = base64.b64encode(buf.getvalue()).decode("ascii")
    return challenge_id, "data:image/png;base64," + encoded

def api_captcha_ok(challenge_id, answer):
    challenge_id = challenge_id or ""
    now = int(time.time())
    with db() as con:
        stored = con.execute(
            "SELECT answer_hash,expires_at FROM api_captchas WHERE id=?",
            (challenge_id,)
        ).fetchone()
        con.execute("DELETE FROM api_captchas WHERE id=?", (challenge_id,))
    if not stored or stored["expires_at"] < now:
        return False
    provided_hash = hashlib.sha256((answer or "").strip().upper().encode("utf-8")).hexdigest()
    return secrets.compare_digest(stored["answer_hash"], provided_hash)

def login_required(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        if not session.get("uid"):
            return redirect(url_for("login", next=request.path))
        return fn(*args, **kwargs)
    return wrapper

def admin_required(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        u = current_user()
        if not u or not u["is_admin"]:
            abort(403)
        return fn(*args, **kwargs)
    return wrapper

def make_captcha():
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    answer = "".join(secrets.choice(alphabet) for _ in range(5))
    session["captcha"] = answer
    session["captcha_ts"] = int(time.time())
    return answer

def captcha_ok(value):
    answer = session.pop("captcha", None)
    ts = session.pop("captcha_ts", 0)
    return bool(
        answer and value and time.time() - ts < 300
        and secrets.compare_digest(answer.upper(), value.strip().upper())
    )

def save_post_image(storage):
    if not storage or not storage.filename:
        return None
    if not storage.mimetype.startswith("image/"):
        raise ValueError("Можно загрузить только изображение.")
    try:
        img = Image.open(storage.stream)
        img.verify()
        storage.stream.seek(0)
        img = Image.open(storage.stream).convert("RGB")
        img.thumbnail((1800, 1800))
    except Exception:
        raise ValueError("Файл не похож на корректное изображение.")
    name = f"{uuid.uuid4().hex}.webp"
    img.save(UPLOAD_DIR / name, "WEBP", quality=82, method=6)
    return f"uploads/{name}"

def feed_query(where="", params=()):
    sql = f"""
      SELECT p.*,u.username,
      (SELECT COUNT(*) FROM likes l WHERE l.post_id=p.id) likes_count,
      (SELECT COUNT(*) FROM posts r WHERE r.parent_id=p.id) replies_count,
      (SELECT COUNT(*) FROM reposts rp WHERE rp.post_id=p.id) reposts_count
      FROM posts p JOIN users u ON u.id=p.user_id
      {where}
      ORDER BY COALESCE(
        (SELECT MAX(rp2.created_at) FROM reposts rp2 WHERE rp2.post_id=p.id),
        p.created_at
      ) DESC
    """
    with db() as con:
        return con.execute(sql, params).fetchall()

def ensure_mellai():
    with db() as con:
        if not con.execute("SELECT 1 FROM users WHERE username='mellai'").fetchone():
            con.execute(
                "INSERT INTO users(username,password_hash,is_admin) VALUES(?,?,0)",
                ("mellai", generate_password_hash(secrets.token_hex(24), method="scrypt"))
            )

def mellai_reply(body):
    low = (body or "").lower()
    if "@mellai" not in low:
        return None
    if not any(k in low for k in MELLAI_KEYS):
        return "Я отвечаю только на вопросы о Mellstroy."
    with db() as con:
        st = con.execute("SELECT * FROM status WHERE id=1").fetchone()
        events = con.execute(
            "SELECT source,title,url,summary,published_at,official,urgent FROM monitor_events "
            "ORDER BY COALESCE(published_at,created_at) DESC LIMIT 12"
        ).fetchall()
    context = [
        f"Статус: {st['label']}",
        f"Описание: {st['detail']}",
        f"Публично сообщаемое местоположение: {st['location']}",
        f"Обновлено: {st['updated_at']}",
    ]
    for i, e in enumerate(events, 1):
        summary = (e["summary"] or "").replace("\n", " ").strip()[:420]
        context.append(
            f"Сигнал {i}: {e['title']} | источник: {e['source']} | "
            f"официальный={bool(e['official'])} | срочный={bool(e['urgent'])} | "
            f"{summary} | {e['url']}"
        )
    prompt = (
        "Ты @mellai — русскоязычный ИИ-помощник фан-сайта о Mellstroy. "
        "Отвечай естественно, содержательно и по делу, а не шаблонными двумя фразами. "
        "Можно объяснять контекст, сопоставлять несколько последних сигналов и отдельно отмечать, "
        "что подтверждено, что является сообщением СМИ/соцсетей, а что пока неизвестно. "
        "Текущие события бери прежде всего из КОНТЕКСТА ниже. Для устойчивых общеизвестных фактов "
        "о Mellstroy можешь использовать собственные знания, но не выдавай догадку за свежий факт. "
        "Если данных недостаточно — скажи конкретно, чего именно не хватает. "
        "Не выдумывай арест, освобождение, местонахождение или стрим. "
        "Не давай точные адреса, GPS, частную геолокацию или способы отследить человека. "
        "Отвечай только на тему Mellstroy и связанную с ним публичную интернет-культуру. "
        "Поддерживай обычный разговор и уточняющие вопросы, а не только сухую выдачу фактов. "
        "Не помогай с действиями, которые явно нарушают законодательство РФ: взломом, доксингом, "
        "преследованием, угрозами, обходом ограничений или получением чужих закрытых данных. "
        "Обычно 3–8 предложений; на простой вопрос можно короче.\n\n"
        "КОНТЕКСТ МОНИТОРИНГА:\n" + "\n".join(context) +
        "\n\nВОПРОС ПОЛЬЗОВАТЕЛЯ:\n" + re.sub(r"@mellai", "", body, flags=re.I).strip()
    )
    try:
        base = os.getenv("MW_OLLAMA_URL", "http://127.0.0.1:11434").rstrip("/")
        model = os.getenv("MW_MELLAI_MODEL", "qwen2.5:1.5b")
        r = requests.post(
            base + "/api/generate",
            json={"model": model, "prompt": prompt, "stream": False,
                  "options": {"temperature": 0.35, "num_predict": 320, "num_ctx": 4096}},
            timeout=45,
        )
        if r.ok:
            out = (r.json().get("response") or "").strip()
            if out:
                return out[:1200]
    except Exception as e:
        print("mellai fallback:", e)
    if "где" in low or "наход" in low:
        return f"Публично сообщаемое местоположение: {st['location']}. Точные адреса сайт не отслеживает."
    if "новост" in low or "послед" in low or "стрим" in low:
        if events:
            return f"Последний сигнал: {events[0]['title']} — {events[0]['source']}."
        return "Нет подтверждённых свежих данных в мониторе."
    return f"Текущий статус: {st['label']}. {st['detail']}"

def maybe_mellai(parent_post_id, body):
    answer = mellai_reply(body)
    if not answer:
        return
    with db() as con:
        bot = con.execute("SELECT id FROM users WHERE username='mellai'").fetchone()
        if bot:
            con.execute(
                "INSERT INTO posts(user_id,body,parent_id) VALUES(?,?,?)",
                (bot["id"], answer, parent_post_id),
            )

@app.get("/")
def home():
    with db() as con:
        st = con.execute("SELECT * FROM status WHERE id=1").fetchone()
        events = con.execute(
            "SELECT * FROM monitor_events ORDER BY COALESCE(published_at,created_at) DESC LIMIT 8"
        ).fetchall()
        posts = con.execute("""
          SELECT p.*,u.username,
          (SELECT COUNT(*) FROM likes l WHERE l.post_id=p.id) likes_count,
          (SELECT COUNT(*) FROM posts r WHERE r.parent_id=p.id) replies_count,
          (SELECT COUNT(*) FROM reposts rp WHERE rp.post_id=p.id) reposts_count
          FROM posts p JOIN users u ON u.id=p.user_id
          WHERE p.parent_id IS NULL
          ORDER BY p.created_at DESC LIMIT 4
        """).fetchall()
    with db() as con:
        stream = con.execute("SELECT * FROM stream_status WHERE id=1").fetchone()
    return render_template(device_template("home"), st=st, events=events, posts=posts, stream=stream)

@app.get("/status")
def status_page():
    with db() as con:
        st = con.execute("SELECT * FROM status WHERE id=1").fetchone()
        events = con.execute(
            "SELECT * FROM monitor_events ORDER BY COALESCE(published_at,created_at) DESC LIMIT 40"
        ).fetchall()
    return render_template(device_template("status"), st=st, events=events)

@app.get("/api/status")
def api_status():
    with db() as con:
        st = con.execute(
            "SELECT code,label,detail,location,source_url,manual_lock,updated_at FROM status WHERE id=1"
        ).fetchone()
        ev = con.execute(
            "SELECT source,title,url,summary,published_at,official,urgent,created_at "
            "FROM monitor_events ORDER BY COALESCE(published_at,created_at) DESC LIMIT 30"
        ).fetchall()
    return jsonify(status=dict(st), events=[dict(x) for x in ev])

@app.get("/health")
def health():
    return jsonify(ok=True, site="Спасаем маму-птицу", url=PUBLIC_URL)

@app.get("/robots.txt")
def robots():
    body = "User-agent: *\nAllow: /\nSitemap: " + PUBLIC_URL + "/sitemap.xml\n"
    return Response(body, mimetype="text/plain")

@app.get("/sitemap.xml")
def sitemap():
    urls = ["/", "/status", "/forum", "/u/mellai"]
    body = ['<?xml version="1.0" encoding="UTF-8"?>',
            '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">']
    for path in urls:
        body.append(f"<url><loc>{PUBLIC_URL}{path}</loc></url>")
    body.append("</urlset>")
    return Response("\n".join(body), mimetype="application/xml")

@app.get("/site.webmanifest")
def webmanifest():
    return jsonify({
        "name": "Спасаем маму-птицу",
        "short_name": "Мама-птица",
        "start_url": "/",
        "scope": "/",
        "display": "standalone",
        "background_color": "#07080c",
        "theme_color": "#07080c",
        "lang": "ru",
    })


@app.get("/api/v1/captcha")
def api_v1_captcha():
    challenge_id, image = make_api_captcha()
    return jsonify(id=challenge_id, image=image, expires_in=300)

@app.post("/api/v1/auth/register")
def api_v1_register():
    data = request.get_json(silent=True) or {}
    username = (data.get("username") or "").strip()
    password = data.get("password") or ""
    if not api_captcha_ok(data.get("captcha_id"), data.get("captcha")):
        return jsonify(error="captcha", message="Неверная CAPTCHA."), 400
    if not USERNAME_RE.fullmatch(username):
        return jsonify(error="username", message="Логин: 3–24 символа, буквы/цифры/._-"), 400
    if username.lower() == "mellai":
        return jsonify(error="username", message="Этот юзернейм зарезервирован."), 400
    if len(password) < 8:
        return jsonify(error="password", message="Пароль должен быть не короче 8 символов."), 400
    try:
        with db() as con:
            cur = con.execute(
                "INSERT INTO users(username,password_hash) VALUES(?,?)",
                (username, generate_password_hash(password, method="scrypt"))
            )
            uid = cur.lastrowid
    except Exception:
        return jsonify(error="username", message="Такой логин уже занят."), 409
    token, expires = issue_api_token(uid)
    return jsonify(token=token, expires_at=expires, user={"id": uid, "username": username})

@app.post("/api/v1/auth/login")
def api_v1_login():
    data = request.get_json(silent=True) or {}
    username = (data.get("username") or "").strip()
    password = data.get("password") or ""
    if not api_captcha_ok(data.get("captcha_id"), data.get("captcha")):
        return jsonify(error="captcha", message="Неверная CAPTCHA."), 400
    with db() as con:
        u = con.execute(
            "SELECT * FROM users WHERE username=? COLLATE NOCASE", (username,)
        ).fetchone()
    if not u or not check_password_hash(u["password_hash"], password):
        return jsonify(error="credentials", message="Неверный логин или пароль."), 401
    token, expires = issue_api_token(u["id"])
    return jsonify(
        token=token,
        expires_at=expires,
        user={"id": u["id"], "username": u["username"], "is_admin": bool(u["is_admin"])}
    )

@app.post("/api/v1/auth/logout")
@api_auth_required
def api_v1_logout(user):
    header = request.headers.get("Authorization", "")
    token = header[7:].strip() if header.startswith("Bearer ") else ""
    if token:
        with db() as con:
            con.execute("DELETE FROM api_tokens WHERE token_hash=?", (api_token_hash(token),))
    return jsonify(ok=True)

@app.get("/api/v1/me")
@api_auth_required
def api_v1_me(user):
    return jsonify(user=dict(user))

@app.get("/api/v1/status")
def api_v1_status():
    with db() as con:
        st = con.execute(
            "SELECT code,label,detail,location,source_url,updated_at FROM status WHERE id=1"
        ).fetchone()
        events = con.execute(
            """SELECT id,source,title,url,summary,published_at,official,urgent,created_at
               FROM monitor_events
               ORDER BY COALESCE(published_at,created_at) DESC LIMIT 40"""
        ).fetchall()
    return jsonify(status=dict(st), events=[dict(x) for x in events])

@app.get("/api/v1/feed")
def api_v1_feed():
    user = api_user()
    viewer = user["id"] if user else -1
    rows = api_feed_rows(viewer_id=viewer, limit=80)
    return jsonify(posts=[post_to_json(x) for x in rows])

@app.get("/api/v1/posts/<int:post_id>")
def api_v1_post(post_id):
    user = api_user()
    viewer = user["id"] if user else -1
    with db() as con:
        con.execute("UPDATE posts SET views=views+1 WHERE id=?", (post_id,))
        post = con.execute(
            """SELECT p.*,u.username,
               (SELECT COUNT(*) FROM likes l WHERE l.post_id=p.id) likes_count,
               (SELECT COUNT(*) FROM posts r WHERE r.parent_id=p.id) replies_count,
               (SELECT COUNT(*) FROM reposts rp WHERE rp.post_id=p.id) reposts_count,
               EXISTS(SELECT 1 FROM likes l2 WHERE l2.post_id=p.id AND l2.user_id=?) liked,
               EXISTS(SELECT 1 FROM reposts rp2 WHERE rp2.post_id=p.id AND rp2.user_id=?) reposted
               FROM posts p JOIN users u ON u.id=p.user_id WHERE p.id=?""",
            (viewer, viewer, post_id)
        ).fetchone()
        replies = con.execute(
            """SELECT p.*,u.username,
               (SELECT COUNT(*) FROM likes l WHERE l.post_id=p.id) likes_count,
               (SELECT COUNT(*) FROM posts r WHERE r.parent_id=p.id) replies_count,
               (SELECT COUNT(*) FROM reposts rp WHERE rp.post_id=p.id) reposts_count,
               EXISTS(SELECT 1 FROM likes l2 WHERE l2.post_id=p.id AND l2.user_id=?) liked,
               EXISTS(SELECT 1 FROM reposts rp2 WHERE rp2.post_id=p.id AND rp2.user_id=?) reposted
               FROM posts p JOIN users u ON u.id=p.user_id
               WHERE p.parent_id=? ORDER BY p.created_at ASC""",
            (viewer, viewer, post_id)
        ).fetchall()
    if not post:
        return jsonify(error="not_found"), 404
    return jsonify(post=post_to_json(post), replies=[post_to_json(x) for x in replies])

@app.post("/api/v1/posts")
@api_auth_required
def api_v1_create_post(user):
    body = (request.form.get("body") or "").strip()
    parent_raw = (request.form.get("parent_id") or "").strip()
    parent_id = int(parent_raw) if parent_raw.isdigit() else None
    try:
        image_path = save_post_image(request.files.get("image"))
    except ValueError as e:
        return jsonify(error="image", message=str(e)), 400
    if not body and not image_path:
        return jsonify(error="empty", message="Напиши текст или прикрепи фото."), 400
    if len(body) > 500:
        return jsonify(error="body", message="Максимум 500 символов."), 400
    with db() as con:
        cur = con.execute(
            "INSERT INTO posts(user_id,body,image_path,parent_id) VALUES(?,?,?,?)",
            (user["id"], body, image_path, parent_id)
        )
        post_id = cur.lastrowid
    if body:
        maybe_mellai(parent_id or post_id, body)
    return jsonify(ok=True, post_id=post_id), 201

@app.post("/api/v1/posts/<int:post_id>/like")
@api_auth_required
def api_v1_like(user, post_id):
    with db() as con:
        exists = con.execute(
            "SELECT 1 FROM likes WHERE user_id=? AND post_id=?", (user["id"], post_id)
        ).fetchone()
        if exists:
            con.execute("DELETE FROM likes WHERE user_id=? AND post_id=?", (user["id"], post_id))
            liked = False
        else:
            con.execute("INSERT OR IGNORE INTO likes(user_id,post_id) VALUES(?,?)", (user["id"], post_id))
            liked = True
        count = con.execute("SELECT COUNT(*) c FROM likes WHERE post_id=?", (post_id,)).fetchone()["c"]
    return jsonify(liked=liked, likes=count)

@app.post("/api/v1/posts/<int:post_id>/repost")
@api_auth_required
def api_v1_repost(user, post_id):
    with db() as con:
        exists = con.execute(
            "SELECT 1 FROM reposts WHERE user_id=? AND post_id=?", (user["id"], post_id)
        ).fetchone()
        if exists:
            con.execute("DELETE FROM reposts WHERE user_id=? AND post_id=?", (user["id"], post_id))
            reposted = False
        else:
            con.execute(
                "INSERT OR IGNORE INTO reposts(user_id,post_id) VALUES(?,?)",
                (user["id"], post_id)
            )
            reposted = True
        count = con.execute("SELECT COUNT(*) c FROM reposts WHERE post_id=?", (post_id,)).fetchone()["c"]
    return jsonify(reposted=reposted, reposts=count)

@app.get("/api/v1/streams")
def api_v1_streams():
    with db() as con:
        st = con.execute("SELECT * FROM stream_status WHERE id=1").fetchone()
        history = con.execute(
            "SELECT * FROM stream_history ORDER BY COALESCE(started_at,ended_at,created_at) DESC LIMIT 80"
        ).fetchall()
    return jsonify(stream=dict(st), history=[dict(x) for x in history])

@app.get("/api/v1/profile/<username>")
def api_v1_profile(username):
    viewer = api_user()
    viewer_id = viewer["id"] if viewer else -1
    with db() as con:
        user = con.execute(
            "SELECT id,username,is_admin,created_at FROM users WHERE username=? COLLATE NOCASE",
            (username,)
        ).fetchone()
    if not user:
        return jsonify(error="not_found"), 404
    posts = api_feed_rows("WHERE p.user_id=? AND p.parent_id IS NULL", (user["id"],), viewer_id, 80)
    return jsonify(user=dict(user), posts=[post_to_json(x) for x in posts])

@app.post("/api/v1/mellai")
@api_auth_required
def api_v1_mellai(user):
    data = request.get_json(silent=True) or {}
    message = (data.get("message") or "").strip()
    if not message:
        return jsonify(error="empty", message="Напиши сообщение."), 400
    if len(message) > 1000:
        return jsonify(error="too_long", message="Сообщение слишком длинное."), 400
    reply = mellai_reply("@mellai Mellstroy " + message)
    return jsonify(reply=reply)

@app.get("/captcha.svg")
def captcha_svg():
    a = make_captcha()
    noise = "".join(
        f'<line x1="{secrets.randbelow(260)}" y1="{secrets.randbelow(76)}" '
        f'x2="{secrets.randbelow(260)}" y2="{secrets.randbelow(76)}" '
        f'stroke="rgba(255,255,255,.17)" stroke-width="1"/>'
        for _ in range(16)
    )
    chars = []
    for i, ch in enumerate(a):
        x = 27 + i * 42 + secrets.randbelow(7)
        y = 48 + secrets.randbelow(8)
        rot = secrets.randbelow(27) - 13
        chars.append(
            f'<text x="{x}" y="{y}" transform="rotate({rot} {x} {y})" '
            f'fill="white" font-size="34" font-family="monospace" font-weight="900">{ch}</text>'
        )
    svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" width="260" height="76" viewBox="0 0 260 76">'
        '<defs><linearGradient id="g"><stop stop-color="#6e56cf"/>'
        '<stop offset="1" stop-color="#ff4ecd"/></linearGradient></defs>'
        '<rect width="260" height="76" rx="18" fill="#10131d"/>'
        '<rect width="260" height="76" rx="18" fill="url(#g)" opacity=".22"/>'
        + noise + "".join(chars) + "</svg>"
    )
    return Response(svg, mimetype="image/svg+xml", headers={"Cache-Control": "no-store"})

@app.route("/register", methods=["GET", "POST"])
def register():
    if request.method == "POST":
        username = request.form.get("username", "").strip()
        password = request.form.get("password", "")
        if not captcha_ok(request.form.get("captcha", "")):
            flash("CAPTCHA введена неверно.")
        elif not USERNAME_RE.fullmatch(username):
            flash("Логин: 3–24 символа, буквы/цифры/._-")
        elif username.lower() == "mellai":
            flash("Этот юзернейм зарезервирован.")
        elif len(password) < 8:
            flash("Пароль должен быть не короче 8 символов.")
        else:
            try:
                with db() as con:
                    con.execute(
                        "INSERT INTO users(username,password_hash) VALUES(?,?)",
                        (username, generate_password_hash(password, method="scrypt"))
                    )
                flash("Аккаунт создан. Теперь войди.")
                return redirect(url_for("login"))
            except Exception:
                flash("Такой логин уже занят.")
    return render_template("auth.html", mode="register")

@app.route("/login", methods=["GET", "POST"])
def login():
    ip = request.headers.get("X-Forwarded-For", request.remote_addr or "").split(",")[0].strip()
    if request.method == "POST":
        state = _login_fail.get(ip, [0, 0])
        if state[0] >= 8 and time.time() - state[1] < 900:
            flash("Слишком много попыток. Подожди 15 минут.")
            return render_template("auth.html", mode="login"), 429
        username = request.form.get("username", "").strip()
        password = request.form.get("password", "")
        if not captcha_ok(request.form.get("captcha", "")):
            flash("CAPTCHA введена неверно.")
        else:
            with db() as con:
                u = con.execute(
                    "SELECT * FROM users WHERE username=? COLLATE NOCASE", (username,)
                ).fetchone()
            if u and check_password_hash(u["password_hash"], password):
                csrf = session.get("_csrf")
                session.clear()
                session["uid"] = u["id"]
                session["_csrf"] = csrf or secrets.token_urlsafe(24)
                _login_fail.pop(ip, None)
                target = request.args.get("next")
                if not target or not target.startswith("/") or target.startswith("//"):
                    target = url_for("home")
                return redirect(target)
            _login_fail[ip] = [state[0] + 1, time.time()]
            flash("Неверный логин или пароль.")
    return render_template("auth.html", mode="login")

@app.post("/logout")
def logout():
    session.clear()
    return redirect(url_for("home"))

@app.post("/post")
@login_required
def create_post():
    last = session.get("last_post_at", 0)
    if time.time() - last < 4:
        flash("Слишком быстро. Подожди несколько секунд.")
        return redirect(request.referrer or url_for("home"))
    body = request.form.get("body", "").strip()
    parent_id = request.form.get("parent_id", "").strip()
    parent = int(parent_id) if parent_id.isdigit() else None
    image_path = None
    try:
        image_path = save_post_image(request.files.get("image"))
    except ValueError as e:
        flash(str(e))
        return redirect(request.referrer or url_for("home"))
    if not body and not image_path:
        flash("Напиши текст или прикрепи фото.")
        return redirect(request.referrer or url_for("home"))
    if len(body) > 500:
        flash("Максимум 500 символов.")
        return redirect(request.referrer or url_for("home"))
    with db() as con:
        cur = con.execute(
            "INSERT INTO posts(user_id,body,image_path,parent_id) VALUES(?,?,?,?)",
            (session["uid"], body, image_path, parent)
        )
        post_id = cur.lastrowid
    session["last_post_at"] = time.time()
    if body:
        maybe_mellai(parent or post_id, body)
    if parent:
        return redirect(url_for("post_detail", post_id=parent))
    return redirect(url_for("forum"))

@app.get("/forum")
def forum():
    posts = feed_query("WHERE p.parent_id IS NULL")
    return render_template(device_template("forum"), posts=posts)

@app.get("/streams")
def streams():
    with db() as con:
        st = con.execute("SELECT * FROM stream_status WHERE id=1").fetchone()
        history = con.execute(
            "SELECT * FROM stream_history ORDER BY COALESCE(started_at,ended_at,created_at) DESC LIMIT 80"
        ).fetchall()
    return render_template(device_template("streams"), stream=st, history=history)

@app.get("/p/<int:post_id>")
def post_detail(post_id):
    sid = session.setdefault("viewed_posts", [])
    if post_id not in sid[-80:]:
        with db() as con:
            con.execute("UPDATE posts SET views=views+1 WHERE id=?", (post_id,))
        sid.append(post_id)
        session["viewed_posts"] = sid[-80:]
        session.modified = True
    with db() as con:
        post = con.execute("""
          SELECT p.*,u.username,
          (SELECT COUNT(*) FROM likes l WHERE l.post_id=p.id) likes_count,
          (SELECT COUNT(*) FROM posts r WHERE r.parent_id=p.id) replies_count,
          (SELECT COUNT(*) FROM reposts rp WHERE rp.post_id=p.id) reposts_count
          FROM posts p JOIN users u ON u.id=p.user_id WHERE p.id=?
        """, (post_id,)).fetchone()
        if not post:
            abort(404)
        replies = con.execute("""
          SELECT p.*,u.username,
          (SELECT COUNT(*) FROM likes l WHERE l.post_id=p.id) likes_count,
          (SELECT COUNT(*) FROM posts r WHERE r.parent_id=p.id) replies_count,
          (SELECT COUNT(*) FROM reposts rp WHERE rp.post_id=p.id) reposts_count
          FROM posts p JOIN users u ON u.id=p.user_id
          WHERE p.parent_id=? ORDER BY p.created_at ASC
        """, (post_id,)).fetchall()
    return render_template("post.html", post=post, replies=replies)

@app.post("/p/<int:post_id>/like")
@login_required
def like_post(post_id):
    with db() as con:
        exists = con.execute(
            "SELECT 1 FROM likes WHERE user_id=? AND post_id=?",
            (session["uid"], post_id)
        ).fetchone()
        if exists:
            con.execute("DELETE FROM likes WHERE user_id=? AND post_id=?", (session["uid"], post_id))
        else:
            con.execute("INSERT OR IGNORE INTO likes(user_id,post_id) VALUES(?,?)", (session["uid"], post_id))
    return redirect(request.referrer or url_for("post_detail", post_id=post_id))

@app.post("/p/<int:post_id>/repost")
@login_required
def repost_post(post_id):
    with db() as con:
        exists = con.execute(
            "SELECT 1 FROM reposts WHERE user_id=? AND post_id=?",
            (session["uid"], post_id)
        ).fetchone()
        if exists:
            con.execute("DELETE FROM reposts WHERE user_id=? AND post_id=?", (session["uid"], post_id))
        else:
            con.execute(
                "INSERT OR IGNORE INTO reposts(user_id,post_id) VALUES(?,?)",
                (session["uid"], post_id)
            )
    return redirect(request.referrer or url_for("forum"))

@app.get("/u/<username>")
def profile(username):
    with db() as con:
        user = con.execute(
            "SELECT id,username,created_at FROM users WHERE username=? COLLATE NOCASE",
            (username,)
        ).fetchone()
        if not user:
            abort(404)
        posts = con.execute("""
          SELECT p.*,u.username,
          (SELECT COUNT(*) FROM likes l WHERE l.post_id=p.id) likes_count,
          (SELECT COUNT(*) FROM posts r WHERE r.parent_id=p.id) replies_count,
          (SELECT COUNT(*) FROM reposts rp WHERE rp.post_id=p.id) reposts_count
          FROM posts p JOIN users u ON u.id=p.user_id
          WHERE p.user_id=? AND p.parent_id IS NULL ORDER BY p.created_at DESC
        """, (user["id"],)).fetchall()
    return render_template("profile.html", user=user, posts=posts)

@app.route("/admin", methods=["GET", "POST"])
@admin_required
def admin():
    if request.method == "POST":
        code = request.form.get("code", "checking")
        if code not in ALLOWED_STATUS:
            abort(400)
        label = request.form.get("label", "").strip()[:120]
        detail = request.form.get("detail", "").strip()[:1200]
        location = request.form.get("location", "").strip()[:120] or "Не установлено"
        source = request.form.get("source_url", "").strip()[:500] or None
        lock = 1 if request.form.get("manual_lock") == "1" else 0
        with db() as con:
            con.execute(
                "UPDATE status SET code=?,label=?,detail=?,location=?,source_url=?,manual_lock=?,"
                "updated_at=CURRENT_TIMESTAMP WHERE id=1",
                (code, label, detail, location, source, lock)
            )
        flash("Статус обновлён.")
    with db() as con:
        st = con.execute("SELECT * FROM status WHERE id=1").fetchone()
        events = con.execute(
            "SELECT * FROM monitor_events ORDER BY created_at DESC LIMIT 80"
        ).fetchall()
    return render_template("admin.html", st=st, events=events)

def bootstrap_admin():
    username = os.getenv("MW_ADMIN_USER")
    password = os.getenv("MW_ADMIN_PASSWORD")
    if not username or not password:
        return
    with db() as con:
        if not con.execute("SELECT 1 FROM users WHERE is_admin=1").fetchone():
            con.execute(
                "INSERT OR IGNORE INTO users(username,password_hash,is_admin) VALUES(?,?,1)",
                (username, generate_password_hash(password, method="scrypt"))
            )

ensure_mellai()
bootstrap_admin()
