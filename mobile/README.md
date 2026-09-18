# Bible Desktop Native Mobile

Полностью нативные клиенты Bible Desktop. Этот каталог не содержит WebView и
не встраивает PWA.

## Архитектура

- `androidApp` — интерфейс Android на Jetpack Compose;
- `iosApp` — интерфейс iOS на SwiftUI;
- `sharedLogic` — общие модели, API-клиент и прикладная логика на Kotlin
  Multiplatform;
- production API — `https://bible-desktop.com/api`.

Платформенные интерфейсы намеренно независимы. Это позволяет соблюдать привычки
Android и iOS, сохраняя единые контракты данных и поведение приложения.

## Текущий вертикальный сценарий

Первый нативный экран загружает список переводов непосредственно из production
API, показывает все переводы либо фильтрует русские и немецкие. Сетевой клиент и
декодирование ответа общие для обеих платформ. Строки экрана локализованы на
русский и немецкий по системному языку устройства.

Экран является проверкой архитектуры, а не утверждённым финальным дизайном.
Окончательные экраны будут собраны по согласованному Figma/UI-макету.

## Android

Требуются JDK 25, Android SDK 37 и Android Studio 2026.1.4 или новее.

Из каталога `mobile`:

```powershell
$env:JAVA_HOME='C:\Users\atapi\AppData\Local\Programs\Android\android-studio\jbr'
$env:ANDROID_HOME='C:\Users\atapi\AppData\Local\Android\Sdk'
.\gradlew.bat :sharedLogic:testAndroidHostTest :androidApp:assembleDebug
```

Debug APK создаётся в
`androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## iOS

Открыть `iosApp/iosApp.xcodeproj` на macOS в Xcode. В локальной копии указать
Apple Developer Team для target `iosApp`; секреты подписи и сертификаты в Git
не добавлять. Xcode сам вызывает
`:sharedLogic:embedAndSignAppleFrameworkForXcode` перед сборкой приложения.

Минимальная версия iOS — 16.0. Сборка iOS на Windows технически невозможна и
должна быть проверена на Mac.

## Идентификаторы и версия

- bundle/application ID: `com.bibledesktop.myapp`;
- marketing/version name: `0.1.0`;
- build/version code: `1`.
