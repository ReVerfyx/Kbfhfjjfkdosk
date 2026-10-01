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
- отдельная ветка android-forum с полной Android-версией.

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

Ветка: `android-forum`

Android-приложение версии 2.0 уже привязано к **https://mellstroy.work.gd**. Адрес сервера вводить не нужно.

В приложении доступны:

- Главная
- Статус
- Форум
- @mellai
- вход и cookies
- публикация фото
- тревожный режим сайта

## Граница мониторинга

Сервис использует только публично доступные данные. Точные адреса, GPS и закрытые данные не собираются.
