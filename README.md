# МАМА-ПТИЦА / Mellstroy public status monitor

Полноценный VPS-сайт: статус-трекер + X-подобный форум.

## Есть
- адаптивный интерфейс с анимациями;
- разные фоновые фото для `checking`, `alert`, `possibly_detained`, `confirmed_detained`;
- отдельный спокойный фон для `ok`;
- регистрация и вход по логину/паролю;
- собственная SVG CAPTCHA;
- CSRF-защита и ограничение попыток входа;
- X-подобная лента: юзернеймы, публикации, ответы, лайки, просмотры;
- загрузка JPG/PNG/WebP в публикации с безопасной перекодировкой в WebP;
- профили пользователей;
- админка статуса;
- `/api/status`;
- публичный монитор Google News RSS, Telegram, RSS и настраиваемых веб-страниц;
- автоматическая пометка «Появились тревожные сообщения — проверяем», но не автоматическое утверждение об аресте;
- systemd + nginx.

## Ubuntu 24.04

```bash
git clone https://github.com/ReVerfyx/Kbfhfjjfkdosk.git
cd Kbfhfjjfkdosk
chmod +x install.sh
sudo ./install.sh
```

Инсталлер покажет IP, логин `admin` и случайный пароль.

## Источники

После установки:
`/opt/mama-bird/sources.json`

Можно добавлять:
- поисковые запросы Google News;
- публичные Telegram-каналы;
- RSS;
- любые публичные страницы для проверки по ключевым словам.

Перезапуск сбора вручную:

```bash
sudo systemctl start mama-bird-monitor.service
```

Логи:

```bash
journalctl -u mama-bird -f
journalctl -u mama-bird-monitor -f
```

## HTTPS

После привязки домена подключи HTTPS через Certbot, затем измени:
`MW_HTTPS=1` в `/etc/mama-bird.env`
и выполни:

```bash
sudo systemctl restart mama-bird
```

## Граница мониторинга

Сервис работает только с публично доступными данными. Он не пытается добывать закрытые аккаунты, домашние адреса, GPS/точные координаты или обходить авторизацию сторонних сервисов.
