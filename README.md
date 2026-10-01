# Спасаем маму-птицу

Продакшен-сайт: **https://mellstroy.work.gd**

Неофициальный публичный монитор статуса Mellstroy + отдельный форум в стиле X + @mellai.

## Что есть

- отдельные страницы Главная / Статус / Форум / @mellai;
- разные фото для спокойного, неопределённого и тревожного режима;
- красная мигающая тревога и сирена при тревожных статусах;
- отдельные тревожные видео при жёстком режиме;
- регистрация и вход по логину/паролю;
- собственная SVG CAPTCHA;
- CSRF-защита и ограничение попыток входа;
- форум: юзернеймы, публикации, ответы, лайки, просмотры, фото;
- профили пользователей;
- админка статуса;
- /api/status и /health;
- robots.txt + sitemap.xml + webmanifest;
- публичный монитор Google News, Telegram, RSS и настраиваемых веб-страниц;
- @mellai через локальный Qwen 2.5 1.5B;
- systemd + nginx;
- полноценный JSON API `/api/v1/` для нативного клиента;
- отдельная ветка `android-native` с Kotlin + Jetpack Compose приложением без WebView.

## VPS / Ubuntu 24.04

```bash
cd /opt/Kbfhfjjfkdosk
sudo git fetch origin
sudo git reset --hard origin/main
sudo chmod +x install.sh
sudo ./install.sh
```

Nginx настроен на:

- mellstroy.work.gd
- www.mellstroy.work.gd

Основной адрес приложения: **https://mellstroy.work.gd**

## HTTPS

```bash
sudo apt update
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx --redirect -d mellstroy.work.gd -d www.mellstroy.work.gd
sudo sed -i 's/^MW_HTTPS=.*/MW_HTTPS=1/' /etc/mama-bird.env
sudo systemctl restart mama-bird nginx
```

## Android

Актуальная ветка: `android-native`.

Версия **3.0-native** написана на Kotlin + Jetpack Compose и не использует WebView.

В приложении доступны:

- нативная главная лента;
- статус и публичная хронология;
- форум в стиле Reddit/X;
- посты, фото, ответы, лайки и просмотры;
- нативный чат @mellai;
- вход/регистрация через API + CAPTCHA;
- профиль пользователя;
- тревожная красная индикация и alarm-tone;
- разные статусные фото;
- домен `https://mellstroy.work.gd` зашит в API-клиент.

Старая ветка `android-forum` оставлена только как архив WebView-прототипа.

## Граница мониторинга

Сервис использует только публично доступные данные. Точные адреса, GPS и закрытые данные не собираются.
