import os, json, re, hashlib, html
from datetime import datetime, timezone, timedelta
from email.utils import parsedate_to_datetime
from urllib.parse import quote_plus

import feedparser, requests
from bs4 import BeautifulSoup
from db import db, init_db

BASE = os.path.dirname(__file__)
CFG = json.load(open(os.getenv("MW_SOURCES", os.path.join(BASE, "sources.json")), encoding="utf-8"))
UA = {"User-Agent": "Mozilla/5.0 (compatible; SpasaemMamuPtitsu/4.0; public-source-monitor)"}
TIMEOUT = 18

URGENT = (
    "задерж", "арест", "полици", "экстрадиц", "интерпол", "обыск", "штурм",
    "detain", "arrest", "police", "extrad"
)
LOCATION_CYPRUS = ("кипр", "cyprus")

def now():
    return datetime.now(timezone.utc)

def fingerprint(*parts):
    return hashlib.sha256("|".join(str(x) for x in parts).encode()).hexdigest()

def clean_text(value):
    if not value:
        return ""
    value = BeautifulSoup(str(value), "html.parser").get_text(" ", strip=True)
    return re.sub(r"\s+", " ", html.unescape(value)).strip()

def normalize_dt(value):
    if not value:
        return None
    try:
        dt = parsedate_to_datetime(value)
        if not dt.tzinfo:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.astimezone(timezone.utc).isoformat()
    except Exception:
        pass
    try:
        dt = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
        if not dt.tzinfo:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.astimezone(timezone.utc).isoformat()
    except Exception:
        return None

def dt_value(value):
    if not value:
        return None
    try:
        return datetime.fromisoformat(str(value).replace("Z", "+00:00")).astimezone(timezone.utc)
    except Exception:
        try:
            return parsedate_to_datetime(value).astimezone(timezone.utc)
        except Exception:
            return None

def is_urgent(title, summary):
    s = f"{title} {summary}".lower()
    return any(k in s for k in URGENT)

def add_event(source, title, url, summary="", published_at=None, official=False):
    title = clean_text(title)[:500]
    summary = clean_text(summary)[:1800]
    published_at = normalize_dt(published_at)
    if not title or not url:
        return None
    urgent = is_urgent(title, summary)
    fp = fingerprint(source, title, url, published_at or "", summary[:700])
    with db() as con:
        con.execute(
            "INSERT OR IGNORE INTO monitor_events"
            "(source,title,url,summary,published_at,official,urgent,fingerprint) "
            "VALUES(?,?,?,?,?,?,?,?)",
            (source, title, url, summary, published_at, int(official), int(urgent), fp)
        )
    return {
        "source": source, "title": title, "url": url, "summary": summary,
        "published_at": published_at, "official": bool(official), "urgent": urgent
    }

def google_news():
    out = []
    for query in CFG.get("news_queries", []):
        try:
            url = "https://news.google.com/rss/search?q=" + quote_plus(query) + "&hl=ru&gl=RU&ceid=RU:ru"
            feed = feedparser.parse(requests.get(url, headers=UA, timeout=TIMEOUT).content)
            for e in feed.entries[:35]:
                outlet = clean_text((e.get("source") or {}).get("title", ""))
                title = clean_text(e.get("title", ""))
                if not outlet and " - " in title:
                    outlet = title.rsplit(" - ", 1)[-1]
                src = f"Google News · {outlet}" if outlet else "Google News"
                item = add_event(
                    src, title, e.get("link", ""), e.get("summary", ""),
                    e.get("published"), False
                )
                if item:
                    out.append(item)
        except Exception as exc:
            print("google_news", query, exc)
    return out

def rss():
    out = []
    for src in CFG.get("rss", []):
        try:
            feed = feedparser.parse(requests.get(src["url"], headers=UA, timeout=TIMEOUT).content)
            for e in feed.entries[:40]:
                item = add_event(
                    src.get("name", "RSS"), e.get("title", ""), e.get("link", ""),
                    e.get("summary", ""), e.get("published"), src.get("official", False)
                )
                if item:
                    out.append(item)
        except Exception as exc:
            print("rss", src.get("url"), exc)
    return out

def telegram():
    out = []
    for cfg in CFG.get("telegram_channels", []):
        channel = cfg["channel"] if isinstance(cfg, dict) else cfg
        owner = bool(cfg.get("owner")) if isinstance(cfg, dict) else False
        try:
            url = f"https://t.me/s/{channel}"
            soup = BeautifulSoup(requests.get(url, headers=UA, timeout=TIMEOUT).text, "html.parser")
            for msg in soup.select(".tgme_widget_message_wrap")[-50:]:
                anchor = msg.select_one(".tgme_widget_message_date")
                txt = msg.select_one(".tgme_widget_message_text")
                if not anchor:
                    continue
                body = clean_text(txt.get_text(" ", strip=True) if txt else "Публикация в Telegram")
                dt = anchor.time.get("datetime") if anchor.time else None
                item = add_event(
                    f"Telegram @{channel}", body[:260], anchor.get("href", url),
                    body, dt, owner
                )
                if item:
                    out.append(item)
                if channel == "mellstroystream":
                    update_stream_from_telegram(body, dt, anchor.get("href", url))
        except Exception as exc:
            print("telegram", channel, exc)
    return out

def update_stream_from_telegram(body, published_at, url):
    low = body.lower()
    if "стрим" not in low and "mellstroy 💜" not in low:
        return
    if not any(x in low for x in ("стрим закончен", "стрим", "mellstroy 💜")):
        return
    ended = "стрим закончен" in low
    title = body.split("Тут:", 1)[0].strip()[:220] or "Mellstroy"
    dt = normalize_dt(published_at)
    fp = fingerprint("tg-stream", title, dt or "", ended)
    with db() as con:
        con.execute(
            "INSERT OR IGNORE INTO stream_history"
            "(title,started_at,ended_at,duration,viewers,url,source,fingerprint) VALUES(?,?,?,?,?,?,?,?)",
            (title, None if ended else dt, dt if ended else None, None, None, url,
             "Telegram @mellstroystream", fp)
        )
        con.execute(
            "UPDATE stream_status SET is_live=?,platform='Kick',title=?,url=?,"
            "last_online=?,source='Telegram @mellstroystream',updated_at=CURRENT_TIMESTAMP WHERE id=1",
            (0 if ended else 1, title, url, dt)
        )

def stream_index():
    src = CFG.get("stream_index")
    if not src:
        return
    try:
        r = requests.get(src["url"], headers=UA, timeout=TIMEOUT)
        soup = BeautifulSoup(r.text, "html.parser")
        text = clean_text(soup.get_text("\n", strip=True))
        is_live = bool(re.search(r"\bLIVE\b|\bLive now\b", text, re.I)) and "Mellstroy" in text
        last = ""
        m = re.search(r"(Last online[^\n]{0,120}|Последний раз онлайн[^\n]{0,120})", text, re.I)
        if m:
            last = m.group(1)[:180]
        with db() as con:
            row = con.execute("SELECT updated_at FROM stream_status WHERE id=1").fetchone()
            con.execute(
                "UPDATE stream_status SET is_live=?,platform='Kick',url=?,"
                "last_online=COALESCE(NULLIF(?,''),last_online),source=?,updated_at=CURRENT_TIMESTAMP WHERE id=1",
                (int(is_live), src["url"], last, src.get("name", "Индекс стримов"))
            )
    except Exception as exc:
        print("stream_index", exc)

def webpages():
    out = []
    keywords = [x.lower() for x in CFG.get("keywords", [])]
    for src in CFG.get("webpages", []):
        try:
            r = requests.get(src["url"], headers=UA, timeout=TIMEOUT)
            soup = BeautifulSoup(r.text, "html.parser")
            text = clean_text(soup.get_text(" ", strip=True))
            if any(k in text.lower() for k in keywords):
                item = add_event(
                    src["name"], clean_text(soup.title.get_text(" ", strip=True) if soup.title else src["name"]),
                    src["url"], text[:1800], None, src.get("official", False)
                )
                if item:
                    out.append(item)
        except Exception as exc:
            print("web", src.get("url"), exc)
    return out

def recent_events(hours=36):
    cutoff = now() - timedelta(hours=hours)
    with db() as con:
        rows = con.execute(
            "SELECT * FROM monitor_events ORDER BY COALESCE(published_at,created_at) DESC LIMIT 500"
        ).fetchall()
    out = []
    for row in rows:
        dt = dt_value(row["published_at"]) or dt_value(row["created_at"])
        if dt and dt >= cutoff:
            out.append((row, dt))
    return out

def source_outlet(source):
    if "·" in source:
        return source.rsplit("·", 1)[-1].strip().lower()
    return source.lower()

def evaluate_status():
    rows = recent_events(72)
    owner = []
    media_alerts = []
    cyprus_seen = False
    trusted = [x.lower() for x in CFG.get("trusted_media", [])]

    for row, dt in rows:
        text = f"{row['title']} {row['summary']}".lower()
        if any(x in text for x in LOCATION_CYPRUS):
            cyprus_seen = True
        if row["source"] == "Telegram @mellstroystream":
            owner.append((row, dt))
        if row["urgent"] and row["source"].startswith("Google News"):
            outlet = source_outlet(row["source"])
            if not trusted or any(t in outlet for t in trusted):
                media_alerts.append((outlet, row, dt))

    with db() as con:
        st = con.execute("SELECT code,manual_lock FROM status WHERE id=1").fetchone()
        if st and st["manual_lock"]:
            return

        stream = con.execute("SELECT * FROM stream_status WHERE id=1").fetchone()

    owner_alert = None
    for row, dt in sorted(owner, key=lambda x: x[1], reverse=True):
        if row["urgent"] and dt >= now() - timedelta(hours=30):
            owner_alert = (row, dt)
            break

    unique_media = {}
    for outlet, row, dt in media_alerts:
        if dt >= now() - timedelta(hours=30):
            unique_media.setdefault(outlet, (row, dt))

    location = "Кипр — по последним публичным сообщениям" if cyprus_seen else "Не установлено"

    code = "checking"
    label = "Проверяем свежие данные"
    detail = "Нет достаточных оснований для красного статуса. Монитор продолжает проверку первичных источников."
    source_url = None

    if owner_alert:
        row, dt = owner_alert
        code = "possibly_detained"
        label = "Возможно задержан — есть сигнал из основного Telegram"
        detail = (
            "В публичном канале @mellstroystream появилось тревожное сообщение. "
            "Это серьёзный первичный сигнал, но без официального подтверждения полиции/властей "
            "статус не считается подтверждённым задержанием."
        )
        source_url = row["url"]
    elif len(unique_media) >= 2:
        first = next(iter(unique_media.values()))[0]
        code = "alert"
        label = "Есть совпадающие сообщения СМИ — проверяем"
        detail = (
            "Несколько независимых публикаций сообщают о тревожном событии, "
            "но первичного подтверждения недостаточно. Красная сирена не включается."
        )
        source_url = first["url"]
    else:
        stream_dt = dt_value(stream["updated_at"]) if stream else None
        if stream and stream["is_live"] and stream_dt and stream_dt >= now() - timedelta(hours=2):
            code = "ok"
            label = "Сейчас идёт стрим"
            detail = "Стрим-монитор видит текущую активность. Это сильный публичный сигнал активности."
            source_url = stream["url"]

    with db() as con:
        con.execute(
            "UPDATE status SET code=?,label=?,detail=?,location=?,source_url=?,"
            "updated_at=CURRENT_TIMESTAMP WHERE id=1",
            (code, label, detail, location, source_url)
        )

def cleanup():
    with db() as con:
        con.execute(
            "DELETE FROM monitor_events WHERE id NOT IN "
            "(SELECT id FROM monitor_events ORDER BY created_at DESC LIMIT 8000)"
        )
        con.execute(
            "DELETE FROM stream_history WHERE id NOT IN "
            "(SELECT id FROM stream_history ORDER BY created_at DESC LIMIT 1500)"
        )

def main():
    init_db()
    for fn in (telegram, google_news, rss, webpages, stream_index):
        try:
            fn()
        except Exception as exc:
            print(fn.__name__, exc)
    evaluate_status()
    cleanup()
    print("monitor done", now().isoformat())

if __name__ == "__main__":
    main()
