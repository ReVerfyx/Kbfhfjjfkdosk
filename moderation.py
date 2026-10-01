import os, re, json, subprocess, tempfile
from pathlib import Path

import requests

TEXT_BLOCK = (
    "порн", "хентай", "cp ", "child porn", "rape porn", "изнасилован",
    "голая малолет", "нюдс", "nudes", "onlyfans leak"
)

def moderate_text(text):
    text = (text or "").strip()
    if not text:
        return True, None, 0.0

    low = text.lower()
    if any(x in low for x in TEXT_BLOCK):
        return False, "Запрещённый сексуальный контент.", 1.0

    if os.getenv("MW_AI_TEXT_MODERATION", "1") != "1":
        return True, None, 0.0

    try:
        base = os.getenv("MW_OLLAMA_URL", "http://127.0.0.1:11434").rstrip("/")
        model = os.getenv("MW_MODERATION_MODEL", os.getenv("MW_MELLAI_MODEL", "qwen2.5:1.5b"))
        prompt = (
            "Ты модератор русскоязычной соцсети. Ответь СТРОГО JSON без markdown: "
            '{"allow":true|false,"reason":"коротко","score":0.0}. '
            "Блокируй порнографию/сексуальный контент с несовершеннолетними, угрозы реального насилия, "
            "доксинг с частными адресами/телефонами и явные призывы к преступлению. "
            "Не блокируй обычный мат, шутки, новости, обсуждения и критику. Текст:\n" + text[:1800]
        )
        r = requests.post(
            base + "/api/generate",
            json={"model": model, "prompt": prompt, "stream": False,
                  "options": {"temperature": 0.0, "num_predict": 120, "num_ctx": 2048}},
            timeout=20,
        )
        if r.ok:
            raw = (r.json().get("response") or "").strip()
            m = re.search(r"\{.*\}", raw, re.S)
            if m:
                data = json.loads(m.group(0))
                allow = bool(data.get("allow", True))
                return allow, str(data.get("reason") or "")[:300] or None, float(data.get("score") or 0)
    except Exception as exc:
        print("text moderation fallback:", exc)

    return True, None, 0.0

_detector = None

def _detector_instance():
    global _detector
    if _detector is not None:
        return _detector
    try:
        from nudenet import NudeDetector
        _detector = NudeDetector()
        return _detector
    except Exception as exc:
        print("nudenet unavailable:", exc)
        return None

def moderate_image(path):
    if os.getenv("MW_AI_MEDIA_MODERATION", "1") != "1":
        return True, None, 0.0
    detector = _detector_instance()
    if detector is None:
        # Do not take the whole site down if the optional model cannot load.
        return True, "media-model-unavailable", 0.0
    try:
        detections = detector.detect(str(path))
        blocked = {
            "FEMALE_GENITALIA_EXPOSED", "MALE_GENITALIA_EXPOSED",
            "ANUS_EXPOSED", "FEMALE_BREAST_EXPOSED"
        }
        score = max((float(x.get("score", 0)) for x in detections if x.get("class") in blocked), default=0.0)
        if score >= float(os.getenv("MW_NSFW_THRESHOLD", "0.58")):
            return False, "Изображение отклонено автоматической модерацией.", score
    except Exception as exc:
        print("image moderation:", exc)
    return True, None, 0.0

def moderate_video(path):
    if os.getenv("MW_AI_MEDIA_MODERATION", "1") != "1":
        return True, None, 0.0
    with tempfile.TemporaryDirectory(prefix="mwmod-") as td:
        out = Path(td) / "frame-%02d.jpg"
        try:
            subprocess.run([
                "ffmpeg", "-v", "error", "-i", str(path),
                "-vf", "fps=1/4,scale=640:-2", "-frames:v", "8", str(out)
            ], check=True, timeout=35)
            worst = 0.0
            for frame in Path(td).glob("frame-*.jpg"):
                ok, reason, score = moderate_image(frame)
                worst = max(worst, score)
                if not ok:
                    return False, reason, worst
        except Exception as exc:
            print("video moderation:", exc)
    return True, None, 0.0
