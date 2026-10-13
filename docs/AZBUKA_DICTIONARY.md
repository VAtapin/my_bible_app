# Словарь в «Азбуке с цифирью»

Один Vue-экран для самостоятельного приложения и плагина Bible App.
Поиск по слову использует исходную церковнославянскую и гражданскую формы;
поиск по значению — русское толкование. Нормализацию старых букв/диакритики
выполняет API, оригинал статьи сохраняется. Значения не переводятся на язык UI.
Алфавит — реальные 36 разделов издания, подписи/число статей приходят с API.
Полная статья открывается поверх результатов на ширину телефона; закрытие
возвращает к поиску. Избранное и 30 недавно открытых слов локальные.

## API

База `https://bible-desktop.com/api`, модуль `AZBYKA_CU_FULL`:

Обе сборки используют прежний `config/api-base-url.txt`; для изолированной
разработки доступен существующий публичный `VITE_API_BASE_URL`. Это build-time
параметр, без ключей/cookies; production default не подменяется тестовым сервером.

- `GET /dictionaries/AZBYKA_CU_FULL/search?q=абие&mode=word&limit=30&offset=0`;
- `GET /dictionaries/AZBYKA_CU_FULL/search?q=начало&mode=meaning`;
- `GET /dictionaries/AZBYKA_CU_FULL/alphabet`;
- `GET /dictionaries/AZBYKA_CU_FULL/search?letter=1&limit=30&offset=0`;
- `GET /dictionaries/AZBYKA_CU_FULL/entries/{key}`.

`word` — префикс формы, точные совпадения первыми; `meaning` — подстрока русского
значения. Все совпадения сохраняются. Пустой q разрешает общий просмотр.
Тело `definition`/`lexical.definition` — plain-text; Vue interpolation без v-html.
API проверяет соответствие статьи source-bound content_hash. Клиент проверяет
контракт: stable40hexkey, модуль, plain-text, формат хеша и точный source_url
главы Азбуки веры. Неизвестные или изменённые данные не подменяются.
Поиск/алфавит GET60/мин; клиент соблюдает Retry-After429, abort старых запросов
и не запускает поиск на каждое нажатие клавиши. Сеть/404/409/429 не означают
«слово не найдено»; пустое совпадение только при успешном total0.

Полный backend контракт: BibleDesktop `docs/CHURCH_SLAVONIC_DICTIONARY_API.md`.
POST `/dictionaries/analyze` предназначен также для словарного разбора в
переводчике; этот экран не генерирует перевод, морфологию или озвучивание.

## Прямые ссылки и WordPress

Самостоятельный сервис: `https://azbuka.bible-desktop.com/dictionary`.
Встроенный: `https://biblia-app.ru/education/azbuka/dictionary`.
Query-параметры одинаковы: `q`, `mode=word|meaning`, `letter=1..36`,
`entry={40hexkey}`. Ссылки кодируются URLSearchParams/эквивалентом.
Например `/dictionary?q=абие&mode=word` или `/dictionary?q=начало&mode=meaning`.
Прямая самостоятельная ссылка доступна без прохождения учебного onboarding.

WordPress shortcodes реализует проект `wp_cu_translator`, а не Vue-приложение
или API. Существующий `[wp_cu_translator]` относится к переводчику; новый
словарный shortcode и его атрибуты должны быть проверены/документированы
чатом этого проекта. Не выдавать предполагаемое имя shortcode за готовое.
Контракт и адреса переданы соответствующему чату.

## Хранение и публикация

Существующая IndexedDB `azbuka-web` обновляется до2 с дополнительным store
dictionary; профиль/учебный прогресс не перезаписываются. Избранное/история
содержат ключ и отображаемые формы, полный словарь не скачивается.
Поиск и повторное открытие статьи требуют сети. Cookies/API-ключ не нужны.
Состояние каждого origin независимо; удаление браузерных данных удаляет записи.

`npm run check` в корне проверяет embedded и обе сборки; отдельный набор —
`cd azbuka-web && npm run check`. Standalone публикуется отдельно её Plesk
пользователем из архива штатного `deploy-production.sh --with-azbuka`.
Нативный Android Bible App этот Vue-экран не встраивает; APK не изменяется.
Публикация/реальные API/browser проверки фиксируются в PROJECT_STATUS.md.
