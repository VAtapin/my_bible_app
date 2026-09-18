# Технический прототип этапа 0

## Что уже проверено

- Публичный API Bible Desktop доступен по `https://bible-desktop.com/api`.
- API разрешает запросы с отдельного web-origin (`Access-Control-Allow-Origin: *`).
- Подтверждена рабочая цепочка:
  `GET /translations` → `GET /translations/{code}/books` →
  `GET /translations/{code}/books/{book}/chapters/{chapter}`.
- На 18 сентября 2026 года тестовый запрос
  `BQ_RUSSIAN_RST_STRONG / genesis / 1` возвращает 31 стих.
- Формы ответов сверены с контроллерами и feature-тестами проекта
  `D:\Projekte\BibleDesktop`.

Клиент различает сетевую ошибку, тайм-аут, HTTP-ошибку и некорректную форму
критического ответа. Полученная глава сохраняется в IndexedDB и читается без
повторного обращения к API.

## Архитектурная граница

Одна кодовая база собирается как PWA, Android и iOS. Платформенные проекты
`android/` и `ios/` являются оболочками Capacitor, а не отдельными продуктами
или долгоживущими Git-ветками.

Текущий IndexedDB-репозиторий предназначен для PWA. Контракт
`ChapterRepository` отделяет прикладной сценарий от хранилища, чтобы следующим
шагом подключить SQLite-адаптер для Android и iOS после проверки кандидатов на
реальных устройствах.

## Пока отсутствует в API

В публичных маршрутах Bible Desktop пока не найдены контракты для:

- персонального профиля и восстановления;
- манифестов и пакетной загрузки офлайн-контента;
- синхронизации по ревизиям;
- регистрации push-токенов.

Эти endpoints не требуются для текущего прототипа одной главы. Их нужно
проектировать в основном API отдельными законченными задачами.

## Локальный запуск

Требуется Node.js 22:

```bash
npm ci
npm run dev
```

Полная локальная проверка:

```bash
npm run check
npm run cap:sync
```

Нативный стек проекта: Capacitor 9.0.0-alpha.6, Local Notifications
9.0.0-alpha.2, Android Gradle Plugin 9.2.1, Gradle 9.4.1 и JDK 25. Android
собирается с compile SDK 37, target SDK 37 и min SDK 26. На текущей Windows-машине
установлены Android Studio 2026.1.4, Android SDK 37 и эмулятор
`BibleDesktop_API_37`; `assembleDebug` и Android unit tests проходят. iOS
использует deployment target 16.0; его реальная сборка требует macOS с Xcode.

## Production PWA

Для PWA зафиксированы:

- адрес: `https://my.bible-desktop.com`;
- каталог проекта: `/var/www/vhosts/bible-desktop.com/my_app`;
- document root субдомена: `/var/www/vhosts/bible-desktop.com/my_app/dist`;
- API: `https://bible-desktop.com/api`;
- среда сборки: Plesk Node.js 22.

Первичную настройку document root и HTTPS нужно выполнить в Plesk. Так как
каталог субдомена уже создан, но Git в нём ещё не развёрнут, первое развёртывание
выполняется из SSH/Plesk terminal так:

```bash
cd /var/www/vhosts/bible-desktop.com/my_app && \
git init && \
git remote add origin https://github.com/VAtapin/my_bible_app.git && \
git fetch origin main && \
git checkout --track origin/main && \
export PATH="/opt/plesk/node/22/bin:$PATH" && \
bash scripts/deploy-production.sh
```

Шаги соединены через `&&`: при конфликте с уже существующим файлом checkout
останавливается, а установка зависимостей и сборка не запускаются. Команда ничего
не удаляет автоматически; конфликтующий стандартный файл Plesk нужно сначала
проверить и обработать вручную.

После первого успешного checkout дальнейшие обновления выполняются командой:

```bash
cd /var/www/vhosts/bible-desktop.com/my_app && \
export PATH="/opt/plesk/node/22/bin:$PATH" && \
bash scripts/deploy-production.sh
```

Скрипт использует только fast-forward pull, устанавливает точные зависимости из
lock-файла, создаёт production-сборку и проверяет наличие `dist/index.html` и
service worker. Файл `public/.htaccess` попадает в сборку и обеспечивает SPA
fallback для прямого открытия внутренних URL через Apache.

## Следующая проверка на устройствах

1. Развернуть текущую PWA-сборку на `my.bible-desktop.com` и проверить установку,
   HTTPS, service worker и IndexedDB после перезапуска без сети.
2. Выбрать и подключить SQLite-адаптер за `ChapterRepository`.
3. Перенести одну главу из API в SQLite и открыть её после полного перезапуска
   приложения без сети.
4. Проверить тестовое локальное уведомление на физическом Android-устройстве.
5. Повторить сценарии на iPhone/macOS.
6. Подтвердить на реальных устройствах зафиксированные минимальные версии:
   Android 8.0 / API 26 и iOS 16.
