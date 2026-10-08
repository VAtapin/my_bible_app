# Bible Desktop Native Mobile

Полностью нативные клиенты Bible Desktop. Здесь нет WebView и не встраивается
PWA.

## Архитектура

- `androidApp` — Android UI на Jetpack Compose;
- `iosApp` — iOS UI на SwiftUI;
- `sharedLogic` — модели, Ktor API-клиент и Swift-friendly loaders на Kotlin
  Multiplatform;
- production API — `https://bible-desktop.com/api`.

Идентификатор обеих платформ: `com.bibledesktop.myapp`. Текущая версия:
`0.1.0` (build 1).

Android debug имеет отдельный идентификатор `com.bibledesktop.myapp.debug`
и название «Bible Desktop · Тест». Он не заменяет release-приложение и не
использует его сохранённые данные. Автоматическая миграция данных старой
Capacitor-версии пока не реализована.

## Реализованные сценарии

- локализованный RU/DE onboarding: быстрый и ручной режимы;
- выбор разделов и нескольких переводов независимо от языка интерфейса;
- экран «Сегодня» с составом приложения;
- Библия: перевод → книга → глава → чтение, размер текста, закладки, share;
- Android: каталог полных закладок, открытие сохранённого места и автоматическое
  продолжение последней главы;
- молитвы: каталог по языку → чтение, размер текста, share;
- церковный календарь: день, старый стиль, пост, памяти и чтения.

Android-сценарии проверены на эмуляторе API 37 с production API. SwiftUI-код
требует первой сборки и проверки на macOS.

## Android

Требуются JDK 25, Android SDK 37 и Android Studio 2026.1.4 или новее.

Из каталога `mobile`:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\atapi\AppData\Local\Android\Sdk'
.\gradlew.bat :sharedLogic:testAndroidHostTest :androidApp:assembleDebug
```

Debug APK:
`androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

### Проверка базовых сценариев

На запущенном эмуляторе (не физическом телефоне), из каталога `mobile`:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\atapi\AppData\Local\Android\Sdk'
$env:ANDROID_SERIAL='emulator-5556'
.\gradlew.bat :sharedLogic:testAndroidHostTest :androidApp:connectedDebugAndroidTest :androidApp:assembleDebug
```

Укажите serial своего эмулятора. На 08.10.2026 прошли пять тестов общего
клиента и три instrumentation-теста на BibleDesktop_API_37: первый запуск,
ручная настройка/системный возврат, быстрый мастер/переходы основных экранов,
чтение реальных ответов BibleDesktop для переводов, книг, главы, молитвы и дня.
Проверка API требует сети; фиксированная дата — 08.10.2026. Тесты не создают
серверные профили, допускают только debug-пакет и восстанавливают изменённые
настройки после каждого теста. Физический телефон не проверен.

Это baseline нативного прототипа, не полная паритетная версия PWA:
пока RU/DE, быстрый режим с мастером, календарь дня. Следующий этап —
перенос актуального интерфейса и четырёх языков. Полный офлайн, заметки,
синхронизация и миграция прежних данных требуют отдельных реализаций.

## iOS

Открыть `iosApp/iosApp.xcodeproj` на macOS в Xcode. В локальной копии указать
Apple Developer Team для target `iosApp`; сертификаты и signing secrets в Git не
добавлять. Xcode вызывает `:sharedLogic:embedAndSignAppleFrameworkForXcode` перед
сборкой. Минимальная версия iOS — 16.0.

## Подтверждённое ограничение API

На 18 сентября 2026 года `/api/prayers?language=de` возвращает русские записи.
Клиенты фильтруют ответ по фактическому `language_code`, поэтому русские молитвы
не выдаются за немецкие. Немецкий корпус должен быть импортирован на стороне
основного Bible Desktop API.
