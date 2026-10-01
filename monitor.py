import os, json, re, hashlib, html
from datetime import datetime, timezone
from urllib.parse import quote_plus

import feedparser, requests
from bs4 import BeautifulSoup
from db import db, init_db

BASE = os.path.dirname(__file__)
CFG = json.load(open(os.getenv("MW_SOURCES", os.path.join(BASE, "sources.json")), encoding="utf-8"))
UA = {"User-Agent": "Mozilla/5.0 (compatible; MamaBirdMonitor/2.0; public-source-monitor)"}
TIMEOUT = 18

URGENT = (
    "задерж", "арест", "полици", "экстрадиц", "обыск", "штурм",
    "розыск", "detain", "arrest", "police", "extrad"
)

def fingerprint(*parts):
    return hashlib.sha256("|".join(str(x) for x in parts).encode()).hexdigest()

def is_urgent(title, summary):
    s = f"{title} {summary}".lower()
    return any(k in s for k in URGENT)

def add_event(source, title, url, summary="", published_at=None, official=False):
    title = re.sub(r"\s+", " ", html.unescape(title or "")).strip()[:500]
    summary = re.sub(r"\s+", " ", html.unescape(summary or "")).strip()[:1800]
    if not title or not url:
        return False
    urgent = is_urgent(title, summary)
    fp = fingerprint(source, title, url, published_at or "", summary[:700])
    with db() as con:
        before = con.total_changes
        con.execute(
            "INSERT OR IGNORE INTO monitor_events"
            "(source,title,url,summary,published_at,official,urgent,fingerprint) "
            "VALUES(?,?,?,?,?,?,?,?)",
            (source, title, url, summary, published_at, int(official), int(urgent), fp)
        )
        created = con.total_changes > before
    return created and urgent

def google_news():
    urgent_new = False
    for query in CFG.get("news_queries", []):
        url = "https://news.google.com/rss/search?q=" + quote_plus(query) + "&hl=ru&gl=RU&ceid=RU:ru"
        r = requests.get(url, headers=UA, timeout=TIMEOUT)
        feed = feedparser.parse(r.content)
        for e in feed.entries[:40]:
            urgent_new |= add_event(
                "Google News", e.get("title",""), e.get("link",""),
                e.get("summary",""), e.get("published"), False
            )
    return urgent_new

def rss():
    urgent_new = False
    for src in CFG.get("rss", []):
        try:
            r = requests.get(src["url"], headers=UA, timeout=TIMEOUT)
            feed = feedparser.parse(r.content)
            for e in feed.entries[:40]:
                urgent_new |= add_event(
                    src.get("name","RSS"), e.get("title",""), e.get("link",""),
                    e.get("summary",""), e.get("published"), src.get("official",False)
                )
        except Exception as exc:
            print("rss", src.get("url"), exc)
    return urgent_new

def telegram():
    urgent_new = False
    for channel in CFG.get("telegram_channels", []):
        try:
            url = f"https://t.me/s/{channel}"
            soup = BeautifulSoup(requests.get(url, headers=UA, timeout=TIMEOUT).text, "html.parser")
            for msg in soup.select(".tgme_widget_message_wrap")[-40:]:
                anchor = msg.select_one(".tgme_widget_message_date")
                txt = msg.select_one(".tgme_widget_message_text")
                if not anchor:
                    continue
                body = (txt.get_text(" ", strip=True) if txt else "Публикация в Telegram")[:1800]
                dt = anchor.time.get("datetime") if anchor.time else None
                urgent_new |= add_event(
                    f"Telegram @{channel}", body[:220], anchor.get("href", url), body, dt, False
                )
        except Exception as exc:
            print("telegram", channel, exc)
    return urgent_new

def webpages():
    urgent_new = False
    keywords = [x.lower() for x in CFG.get("keywords", [])]
    for src in CFG.get("webpages", []):
        try:
            r = requests.get(src["url"], headers=UA, timeout=TIMEOUT)
            soup = BeautifulSoup(r.text, "html.parser")
            text = re.sub(r"\s+", " ", soup.get_text(" ", strip=True))
            low = text.lower()
            if any(k in low for k in keywords):
                title = soup.title.get_text(" ", strip=True) if soup.title else src["name"]
                urgent_new |= add_event(
                    src["name"], title, src["url"], text[:1800],
                    None, src.get("official", False)
                )
        except Exception as exc:
            print("web", src.get("url"), exc)
    return urgent_new

def auto_signal(urgent_new):
    if not urgent_new:
        return
    with db() as con:
        st = con.execute("SELECT code,manual_lock FROM status WHERE id=1").fetchone()
        if st and not st["manual_lock"] and st["code"] not in ("confirmed_detained",):
            con.execute(
                "UPDATE status SET code='alert',label='Появились тревожные сообщения — проверяем',"
                "detail='Монитор обнаружил новые публикации с тревожными ключевыми словами. "
                "Это ещё не подтверждение задержания.',updated_at=CURRENT_TIMESTAMP WHERE id=1"
            )

def cleanup():
    with db() as con:
        con.execute(
            "DELETE FROM monitor_events WHERE id NOT IN "
            "(SELECT id FROM monitor_events ORDER BY created_at DESC LIMIT 8000)"
        )

def main():
    init_db()
    urgent_new = False
    for fn in (google_news, rss, telegram, webpages):
        try:
            urgent_new |= bool(fn())
        except Exception as exc:
            print(fn.__name__, exc)
    auto_signal(urgent_new)
    cleanup()
    print("monitor done", datetime.now(timezone.utc).isoformat(), "urgent_new=", urgent_new)

if __name__ == "__main__":
    main()
