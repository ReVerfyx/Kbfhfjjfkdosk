#!/usr/bin/env bash
set -euo pipefail
if [ "$(id -u)" -ne 0 ]; then echo "Запусти: sudo ./install.sh"; exit 1; fi

APP=/opt/mama-bird
SRC="$(cd "$(dirname "$0")" && pwd)"

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y python3 python3-venv python3-pip nginx openssl curl wget ffmpeg

mkdir -p "$APP/data" "$APP/static/uploads"
cp "$SRC/app.py" "$SRC/db.py" "$SRC/monitor.py" "$SRC/moderation.py" "$SRC/sources.json" "$SRC/requirements.txt" "$APP/"
rm -rf "$APP/templates"
cp -r "$SRC/templates" "$APP/templates"
mkdir -p "$APP/static"
cp -r "$SRC/static/." "$APP/static/"
mkdir -p "$APP/static/uploads"

python3 -m venv "$APP/venv"
"$APP/venv/bin/pip" install --upgrade pip
"$APP/venv/bin/pip" install -r "$APP/requirements.txt"
# AI media moderation is optional at runtime, but install it when possible.
"$APP/venv/bin/pip" install "nudenet>=3.4" "onnxruntime>=1.19" || true

# Локальный ИИ для @mellai. Не подменяем ИИ мгновенными шаблонными ответами.
MELLAI_MODEL="qwen2.5:3b"

install_ollama() {
  if command -v ollama >/dev/null 2>&1; then
    return 0
  fi

  echo "[AI] Устанавливаю Ollama..."
  rm -f /tmp/ollama-install.sh /tmp/ollama.tgz

  if curl -fL --retry 5 --retry-delay 3 --retry-all-errors       https://ollama.com/install.sh -o /tmp/ollama-install.sh; then
    sh /tmp/ollama-install.sh || true
  fi

  if ! command -v ollama >/dev/null 2>&1; then
    echo "[AI] Основной установщик недоступен, пробую GitHub release..."
    if curl -fL --retry 5 --retry-delay 3 --retry-all-errors         https://github.com/ollama/ollama/releases/latest/download/ollama-linux-amd64.tgz         -o /tmp/ollama.tgz; then
      tar -C /usr -xzf /tmp/ollama.tgz
    fi
  fi

  command -v ollama >/dev/null 2>&1
}

if install_ollama; then
  if ! id ollama >/dev/null 2>&1; then
    useradd -r -s /usr/sbin/nologin -d /usr/share/ollama -m ollama
  fi
  mkdir -p /usr/share/ollama
  chown -R ollama:ollama /usr/share/ollama

  if [ ! -f /etc/systemd/system/ollama.service ] && [ ! -f /lib/systemd/system/ollama.service ]; then
    OLLAMA_BIN="$(command -v ollama)"
    cat >/etc/systemd/system/ollama.service <<EOF
[Unit]
Description=Ollama local inference server
After=network-online.target

[Service]
ExecStart=$OLLAMA_BIN serve
User=ollama
Group=ollama
Environment=HOME=/usr/share/ollama
Environment=OLLAMA_HOST=127.0.0.1:11434
Environment=OLLAMA_KEEP_ALIVE=30m
Environment=OLLAMA_NUM_PARALLEL=1
Restart=always
RestartSec=2

[Install]
WantedBy=multi-user.target
EOF
  fi

  mkdir -p /etc/systemd/system/ollama.service.d
  cat >/etc/systemd/system/ollama.service.d/mama-bird.conf <<EOF
[Service]
Environment=OLLAMA_KEEP_ALIVE=30m
Environment=OLLAMA_NUM_PARALLEL=1
EOF

  systemctl daemon-reload
  systemctl enable --now ollama
  for _ in {1..30}; do
    curl -fsS http://127.0.0.1:11434/api/tags >/dev/null 2>&1 && break
    sleep 1
  done

  echo "[AI] Загружаю $MELLAI_MODEL..."
  if id ollama >/dev/null 2>&1; then
    runuser -u ollama -- env HOME=/usr/share/ollama "$(command -v ollama)" pull "$MELLAI_MODEL"
  else
    ollama pull "$MELLAI_MODEL"
  fi

  echo "[AI] Проверяю реальную генерацию..."
  TEST_JSON="$(curl -fsS --max-time 90 http://127.0.0.1:11434/api/generate     -H 'Content-Type: application/json'     -d "{\"model\":\"$MELLAI_MODEL\",\"prompt\":\"Ответь одним словом: работает\",\"stream\":false}" || true)"
  if echo "$TEST_JSON" | grep -q '"response"'; then
    echo "[AI] @mellai: модель отвечает."
  else
    echo "[AI] ВНИМАНИЕ: Ollama установлен, но тест генерации не прошёл."
  fi
else
  echo "[AI] ВНИМАНИЕ: Ollama установить не удалось. @mellai будет показывать недоступность, а не фальшивый шаблон."
fi

ADMIN_PASS=""
if [ ! -f /etc/mama-bird.env ]; then
  SECRET="$(openssl rand -hex 32)"
  ADMIN_PASS="$(openssl rand -hex 12)"
  cat >/etc/mama-bird.env <<EOF
MW_SECRET_KEY=$SECRET
MW_ADMIN_USER=admin
MW_ADMIN_PASSWORD=$ADMIN_PASS
MW_DB=$APP/data/app.db
MW_SOURCES=$APP/sources.json
MW_HTTPS=0
MW_OLLAMA_URL=http://127.0.0.1:11434
MW_MELLAI_MODEL=qwen2.5:3b
MW_PUBLIC_URL=https://mellstroy.work.gd
MW_AI_TEXT_MODERATION=1
MW_AI_MEDIA_MODERATION=1
MW_MODERATION_MODEL=qwen2.5:3b
MW_NSFW_THRESHOLD=0.58
MW_TON_WALLET=
MW_LOLZ_API_BASE=
MW_LOLZ_API_TOKEN=
MW_LOLZ_MERCHANT_ID=
MW_LOLZ_WEBHOOK_SECRET=
EOF
else
  if grep -q '^MW_MELLAI_MODEL=' /etc/mama-bird.env; then
    sed -i 's/^MW_MELLAI_MODEL=.*/MW_MELLAI_MODEL=qwen2.5:3b/' /etc/mama-bird.env
  else
    echo 'MW_MELLAI_MODEL=qwen2.5:3b' >> /etc/mama-bird.env
  fi
  for row in     'MW_AI_TEXT_MODERATION=1'     'MW_AI_MEDIA_MODERATION=1'     'MW_MODERATION_MODEL=qwen2.5:3b'     'MW_NSFW_THRESHOLD=0.58'     'MW_TON_WALLET='     'MW_LOLZ_API_BASE='     'MW_LOLZ_API_TOKEN='     'MW_LOLZ_MERCHANT_ID='     'MW_LOLZ_WEBHOOK_SECRET='; do
      key="${row%%=*}"
      grep -q "^${key}=" /etc/mama-bird.env || echo "$row" >> /etc/mama-bird.env
  done
  if grep -q '^MW_PUBLIC_URL=' /etc/mama-bird.env; then
    sed -i 's#^MW_PUBLIC_URL=.*#MW_PUBLIC_URL=https://mellstroy.work.gd#' /etc/mama-bird.env
  else
    echo 'MW_PUBLIC_URL=https://mellstroy.work.gd' >> /etc/mama-bird.env
  fi
fi
chmod 600 /etc/mama-bird.env
chown -R www-data:www-data "$APP/data" "$APP/static/uploads"

cat >/etc/systemd/system/mama-bird.service <<EOF
[Unit]
Description=Спасаем маму-птицу — сайт
After=network.target ollama.service

[Service]
WorkingDirectory=$APP
EnvironmentFile=/etc/mama-bird.env
ExecStart=$APP/venv/bin/gunicorn -w 2 -b 127.0.0.1:8055 app:app
Restart=always
User=www-data
Group=www-data

[Install]
WantedBy=multi-user.target
EOF

cat >/etc/systemd/system/mama-bird-monitor.service <<EOF
[Unit]
Description=Спасаем маму-птицу — монитор публичных источников
After=network-online.target

[Service]
Type=oneshot
WorkingDirectory=$APP
EnvironmentFile=/etc/mama-bird.env
ExecStart=$APP/venv/bin/python $APP/monitor.py
User=www-data
Group=www-data
EOF

cat >/etc/systemd/system/mama-bird-monitor.timer <<EOF
[Unit]
Description=Монитор Спасаем маму-птицу каждые 5 минут

[Timer]
OnBootSec=30s
OnUnitActiveSec=5min
RandomizedDelaySec=20s
Persistent=true

[Install]
WantedBy=timers.target
EOF

CERT="/etc/letsencrypt/live/mellstroy.work.gd/fullchain.pem"
KEY="/etc/letsencrypt/live/mellstroy.work.gd/privkey.pem"

if [ -f "$CERT" ] && [ -f "$KEY" ]; then
  sed -i 's/^MW_HTTPS=.*/MW_HTTPS=1/' /etc/mama-bird.env || true
  cat >/etc/nginx/sites-available/mama-bird <<'EOF'
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name mellstroy.work.gd www.mellstroy.work.gd;
    return 301 https://mellstroy.work.gd$request_uri;
}
server {
    listen 443 ssl http2 default_server;
    listen [::]:443 ssl http2 default_server;
    server_name mellstroy.work.gd www.mellstroy.work.gd;
    ssl_certificate /etc/letsencrypt/live/mellstroy.work.gd/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/mellstroy.work.gd/privkey.pem;
    client_max_body_size 80m;

    location /static/uploads/ {
        alias /opt/mama-bird/static/uploads/;
        expires 7d;
        add_header Cache-Control "public";
    }

    location / {
        proxy_pass http://127.0.0.1:8055;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF
else
  cat >/etc/nginx/sites-available/mama-bird <<'EOF'
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name mellstroy.work.gd www.mellstroy.work.gd;
    client_max_body_size 80m;

    location /static/uploads/ {
        alias /opt/mama-bird/static/uploads/;
        expires 7d;
        add_header Cache-Control "public";
    }

    location / {
        proxy_pass http://127.0.0.1:8055;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF
fi
rm -f /etc/nginx/sites-enabled/default
ln -sf /etc/nginx/sites-available/mama-bird /etc/nginx/sites-enabled/mama-bird
nginx -t

systemctl daemon-reload
systemctl enable --now mama-bird mama-bird-monitor.timer nginx
systemctl restart mama-bird nginx
systemctl start mama-bird-monitor.service || true

IP="$(hostname -I | awk '{print $1}')"
echo
echo "============================================"
echo "Сайт: https://mellstroy.work.gd/"
echo "Админ: admin"
if [ -n "$ADMIN_PASS" ]; then
  echo "Пароль: $ADMIN_PASS"
  echo "СОХРАНИ ПАРОЛЬ СЕЙЧАС"
else
  echo "Пароль администратора сохранён прежний"
fi
echo "Настройки поддержки: /etc/mama-bird.env"
echo "  MW_TON_WALLET=..."
echo "  MW_LOLZ_API_BASE=..."
echo "  MW_LOLZ_API_TOKEN=..."
echo "  MW_LOLZ_MERCHANT_ID=..."
echo "  MW_LOLZ_WEBHOOK_SECRET=..."
echo
echo "Источники: $APP/sources.json"
echo "Сайт: journalctl -u mama-bird -f"
echo "Монитор: journalctl -u mama-bird-monitor -f"
echo "============================================"
