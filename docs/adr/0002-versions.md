# ADR 0002: Версии инструментов и библиотек

- Статус: принято
- Дата проверки: 2026-10-12
- Источник истины для сборки: `gradle/libs.versions.toml`

## Как проверялось

Номера версий взяты из метаданных репозиториев, а не из памяти и не из статей:

- Google Maven: `https://dl.google.com/android/maven2/<group>/<artifact>/maven-metadata.xml`
  и `group-index.xml`. Это те же данные, что показывают страницы
  `developer.android.com/jetpack/androidx/releases/*`.
- Maven Central (`repo.maven.apache.org`), Gradle Plugin Portal, JitPack: `maven-metadata.xml`.
- Gradle: `https://services.gradle.org/versions/current`.
- Android SDK: `https://dl.google.com/android/repository/repository2-3.xml`.
- Маппинг Compose BOM: POM `compose-bom-2026.09.00`.

«Стабильная» версия — последняя без суффиксов `alpha`, `beta`, `rc`, `dev`, `eap`.

## Используется сейчас (в каталоге)

| Компонент | Версия | Примечание |
|---|---|---|
| Gradle | 9.8.1 | wrapper проверяет sha256 дистрибутива |
| Android Gradle Plugin | 9.4.1 | stable; 9.5.0 пока alpha. Kotlin компилируется встроенной поддержкой AGP 9, плагин `org.jetbrains.kotlin.android` не нужен |
| Kotlin | 2.4.21 | stable; 2.5.0 — Beta1 |
| KSP | 2.3.12 | у KSP2 своя нумерация, она не привязана к версии Kotlin |
| JDK (сборка) | 21 | байткод модулей — Java 17 |
| compileSdk / targetSdk | 37 | `platforms;android-37.0`; минорные 37.1/37.2 пока не используем |
| minSdk | 26 | |
| desugar_jdk_libs | 2.1.5 | нужен NewPipeExtractor (`java.time`, `java.nio`) |
| Compose BOM | 2026.09.00 | маппит ui на 1.12.1 и material3 на **1.4.0** |
| Compose UI / Foundation / Runtime | 1.12.1 | через BOM; 1.13.0-beta01 — только в отдельной ветке-эксперименте |
| **material3** | **1.5.0-beta01** | переопределён явно поверх BOM: в 1.5 есть Expressive API (`MaterialExpressiveTheme`, `MotionScheme.expressive()` уже стабильны). Переходим на 1.5.0, как только выйдет stable |
| activity-compose | 1.13.0 | |
| core-ktx | 1.19.1 | |
| lifecycle | 2.11.0 | |
| Hilt (Dagger) | 2.60.1 | |
| kotlinx-coroutines | 1.11.0 | |
| kotlinx-serialization | 1.11.0 | |
| kotlinx-collections-immutable | 0.5.2 | |
| Detekt | 1.23.8 | работает с Kotlin 2.4 (проверено на этом проекте). Detekt 2.0 пока alpha |
| Spotless / ktlint | 8.10.4 / 1.8.0 | |
| JUnit 4 | 4.13.2 | для Android-модулей; JVM-модули используют `kotlin-test` на JUnit Platform |
| Navigation 3 | 1.2.0 | `lifecycle-viewmodel-navigation3` 2.11.0 |
| material3-adaptive-navigation-suite | 1.5.0-beta01 | та же версия, что и material3 |
| Media3 | 1.11.1 | exoplayer, dash, hls, datasource-okhttp, session, ui-compose, ui-compose-material3 |
| NewPipeExtractor | v0.26.5 | JitPack, только группа `com.github.TeamNewPipe` |
| OkHttp | 5.5.0 | |
| jsoup | 1.23.2 | |
| Coil 3 | 3.6.3 | |
| DataStore | 1.2.1 | Preferences |
| hilt-lifecycle-viewmodel-compose | 1.4.0 | |
| core-splashscreen | 1.2.0 | |
| Robolectric | 4.17 | тест разбора DASH-манифеста парсером Media3, тесты Room |
| WorkManager | 2.12.0 | загрузки, фоновое обновление подписок, автозагрузка |
| CameraX | 1.6.2 | чтение QR-кодов при переносе библиотеки (проверено 14.10.2026) |
| ZXing core | 3.5.4 | создание и разбор QR-кодов, Apache-2.0, без Google Play Services (проверено 14.10.2026) |
| Room 3 (`androidx.room3`) | 3.0.3 | история и подписки; драйвер `AndroidSQLiteDriver` из `sqlite-framework` 2.7.1 (системный SQLite, APK не растёт). Плагин `androidx.room3` экспортирует схемы в `core/database/schemas` |

## Проверено, добавляется вместе с модулем-потребителем

В каталог намеренно не добавлено то, что ещё ничем не используется. Версии на дату проверки:

| Компонент | Версия |
|---|---|
| `material3-adaptive` | 1.3.0 (1.4.0 — alpha03) |
| Paging | 3.5.2 |
| Glance | 1.2.0 |
| `androidx.window` | 1.5.1 |
| `palette-ktx` | 1.0.0 |
| Benchmark / Baseline Profile | 1.5.0 |
| profileinstaller | 1.4.1 |
| protobuf (kotlin-lite) / plugin | 4.36.2 / 0.10.0 |
| MockK | 1.14.11 |
| Turbine | 1.2.1 |
| Roborazzi | 1.76.0 |
| LeakCanary | 2.14 (3.0 — alpha) |
| AndroidX Test: ext-junit / espresso / uiautomator | 1.3.0 / 3.7.0 / 2.4.0 |

## Правила

1. Перед изменением версии сверяйтесь с первоисточником и обновляйте этот файл (дату и таблицу).
2. Обновления приходят через Renovate (`renovate.json`). Kotlin+KSP, Compose и Media3 обновляются группами.
3. Pre-release допускается только там, где это прямо записано здесь. Сейчас это material3 и navigation-suite той же версии.
4. Если BOM отстаёт от нужной версии артефакта, артефакт переопределяется явно с комментарием в каталоге.
