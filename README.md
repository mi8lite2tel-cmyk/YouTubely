# YouTubely

Неофициальный open-source клиент YouTube для Android на Material 3 Expressive и Jetpack Compose.
Без рекламы, без аккаунта Google, без трекеров.

> YouTubely не связан с Google и YouTube и не одобрен ими. Использование может нарушать
> Условия использования YouTube. Скачивайте только то, на что у вас есть права.

**Статус:** ранняя разработка. Сейчас в репозитории только каркас проекта.

## Сборка

Нужны JDK 21 и Android SDK (platform 37).

```sh
./gradlew assembleFossDebug          # F-Droid-совместимая сборка, без Google Play Services
./gradlew assembleFullDebug          # то же + (в будущем) Google Cast
./gradlew spotlessCheck detekt lintFossDebug test   # то, что проверяет CI
./gradlew spotlessApply              # автоформатирование и SPDX-заголовки
```

## Структура

```
app/                 Application, MainActivity, flavors foss/full
build-logic/         convention-плагины Gradle (android-application/library, compose, hilt, jvm-library)
core/common/         Outcome, DispatcherProvider
core/model/          доменные модели (VideoId, StreamItem, StreamInfo, …)
core/provider/       интерфейс StreamProvider — единственный путь к источнику видео
core/designsystem/   тема Material 3 Expressive
config/              Detekt, Compose stability
docs/adr/            архитектурные решения
tools/color/         генератор fallback-палитры (Material Color Utilities)
```

Подробные правила проекта — в [CLAUDE.md](CLAUDE.md), решения — в [docs/adr](docs/adr/README.md).

## Лицензия

[GPL-3.0-or-later](LICENSE). Почему не MIT — см. [ADR 0001](docs/adr/0001-license.md).
