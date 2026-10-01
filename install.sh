#!/usr/bin/env bash
set -euo pipefail
if [ "$(id -u)" -ne 0 ]; then echo "Запусти: sudo ./install.sh"; exit 1; fi

APP=/opt/mama-bird
SRC="$(cd "$(dirname "$0")" && pwd)"

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y python3 python3-venv python3-pip nginx openssl curl wget

mkdir -p "$APP/data" "$APP/static/uploads"
cp "$SRC/app.py" "$SRC/db.py" "$SRC/monitor.py" "$SRC/sources.json" "$SRC/requirements.txt" "$APP/"
rm -rf "$APP/templates"
cp -r "$SRC/templates" "$APP/templates"
mkdir -p "$APP/static"
cp -r "$SRC/static/." "$APP/static/"
mkdir -p "$APP/static/uploads"

python3 -m venv "$APP/venv"
"$APP/venv/bin/pip" install --upgrade pip
"$APP/venv/bin/pip" install -r "$APP/requirements.txt"

# Локальный ИИ для @mellai. Qwen 2.5 1.5B — компромисс между качеством и нагрузкой.
if ! command -v ollama >/dev/null 2>&1; then
  curl -fsSL https://ollama.com/install.sh | sh || true
fi
systemctl enable --now ollama 2>/dev/null || true
ollama pull qwen2.5:1.5b || true

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
MW_MELLAI_MODEL=qwen2.5:1.5b
EOF
chmod 600 /etc/mama-bird.env
chown -R www-data:www-data "$APP/data" "$APP/static/uploads"

cat >/etc/systemd/system/mama-bird.service <<EOF
[Unit]
Description=Mama Bird status + social web app
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
Description=Mama Bird public source monitor
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
Description=Run Mama Bird monitor every 5 minutes

[Timer]
OnBootSec=30s
OnUnitActiveSec=5min
RandomizedDelaySec=20s
Persistent=true

[Install]
WantedBy=timers.target
EOF

cat >/etc/nginx/sites-available/mama-bird <<'EOF'
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;
    client_max_body_size 6m;

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

rm -f /etc/nginx/sites-enabled/default
ln -sf /etc/nginx/sites-available/mama-bird /etc/nginx/sites-enabled/mama-bird

systemctl daemon-reload
systemctl enable --now mama-bird mama-bird-monitor.timer nginx
systemctl restart mama-bird nginx
systemctl start mama-bird-monitor.service || true

IP="$(hostname -I | awk '{print $1}')"
echo
echo "============================================"
echo "Сайт: http://$IP/"
echo "Админ: admin"
echo "Пароль: $ADMIN_PASS"
echo "@mellai: Qwen2.5 1.5B через Ollama"
echo "СОХРАНИ ПАРОЛЬ СЕЙЧАС"
echo
echo "Источники: $APP/sources.json"
echo "Сайт: journalctl -u mama-bird -f"
echo "Монитор: journalctl -u mama-bird-monitor -f"
echo "============================================"
