import os, re, secrets, time, uuid
from functools import wraps
from pathlib import Path

from flask import (
    Flask, render_template, request, redirect, url_for, session, abort,
    jsonify, Response, flash
)
from PIL import Image
from werkzeug.security import generate_password_hash, check_password_hash
from db import db, init_db

APP_DIR = Path(__file__).resolve().parent
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
ALLOWED_STATUS = {"checking", "ok", "alert", "possibly_detained", "confirmed_detained"}
_login_fail = {}

def csrf_token():
    token = session.get("_csrf")
    if not token:
        token = secrets.token_urlsafe(24)
        session["_csrf"] = token
    return token

@app.context_processor
def globals_for_templates():
    return {"me": current_user(), "csrf_token": csrf_token}

@app.before_request
def csrf_guard():
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
      (SELECT COUNT(*) FROM posts r WHERE r.parent_id=p.id) replies_count
      FROM posts p JOIN users u ON u.id=p.user_id
      {where}
      ORDER BY p.created_at DESC
    """
    with db() as con:
        return con.execute(sql, params).fetchall()

@app.get("/")
def home():
    with db() as con:
        st = con.execute("SELECT * FROM status WHERE id=1").fetchone()
        events = con.execute(
            "SELECT * FROM monitor_events ORDER BY COALESCE(published_at,created_at) DESC LIMIT 7"
        ).fetchall()
        posts = con.execute("""
          SELECT p.*,u.username,
          (SELECT COUNT(*) FROM likes l WHERE l.post_id=p.id) likes_count,
          (SELECT COUNT(*) FROM posts r WHERE r.parent_id=p.id) replies_count
          FROM posts p JOIN users u ON u.id=p.user_id
          WHERE p.parent_id IS NULL
          ORDER BY p.created_at DESC LIMIT 15
        """).fetchall()
    return render_template("home.html", st=st, events=events, posts=posts)

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
        con.execute(
            "INSERT INTO posts(user_id,body,image_path,parent_id) VALUES(?,?,?,?)",
            (session["uid"], body, image_path, parent)
        )
    session["last_post_at"] = time.time()
    if parent:
        return redirect(url_for("post_detail", post_id=parent))
    return redirect(url_for("home") + "#forum")

@app.get("/forum")
def forum():
    return redirect(url_for("home") + "#forum")

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
          (SELECT COUNT(*) FROM posts r WHERE r.parent_id=p.id) replies_count
          FROM posts p JOIN users u ON u.id=p.user_id WHERE p.id=?
        """, (post_id,)).fetchone()
        if not post:
            abort(404)
        replies = con.execute("""
          SELECT p.*,u.username,
          (SELECT COUNT(*) FROM likes l WHERE l.post_id=p.id) likes_count,
          (SELECT COUNT(*) FROM posts r WHERE r.parent_id=p.id) replies_count
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
          (SELECT COUNT(*) FROM posts r WHERE r.parent_id=p.id) replies_count
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

bootstrap_admin()
