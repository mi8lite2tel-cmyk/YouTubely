<div align="center">

<img src="docs/assets/logo.svg" width="128" height="128" alt="Логотип YouTubely">

# YouTubely

**Свободный клиент YouTube для Android на Material 3 Expressive.**<br>
Без рекламы. Без аккаунта Google. Без трекеров.

[![CI](https://github.com/mi8lite2tel-cmyk/YouTubely/actions/workflows/ci.yml/badge.svg)](https://github.com/mi8lite2tel-cmyk/YouTubely/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/mi8lite2tel-cmyk/YouTubely?include_prereleases&label=release&color=7C4DFF)](https://github.com/mi8lite2tel-cmyk/YouTubely/releases)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSE)
<br>
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-4285F4?logo=jetpackcompose&logoColor=white)

[**Скачать**](#-скачать) · [Возможности](#-возможности) · [Roadmap](#-roadmap) · [Сборка](#-сборка-из-исходников) · [Архитектура](#-архитектура)

</div>

> [!WARNING]
> **Проект в ранней разработке.** Сейчас готов каркас приложения: сборка, тема и CI.
> Воспроизведения видео пока нет. Ниже описано, к чему мы идём.

> [!NOTE]
> YouTubely — независимое приложение с открытым кодом. Оно не связано с Google и YouTube
> и не одобрено ими. Использование может нарушать Условия использования YouTube —
> ответственность лежит на пользователе. Скачивайте только то, на что у вас есть права.

## ✨ Возможности

То, что YouTube продаёт по подписке, YouTubely честно делает на стороне клиента из публичных потоков:

<table>
<tr>
<td width="50%" valign="top">

### 🎬 Воспроизведение
- Фоновое воспроизведение и экран блокировки
- Картинка в картинке (PiP)
- Аудио-режим для экономии трафика
- До 4K/8K, 60 fps, HDR, AV1/VP9
- Главы, субтитры, скорость 0.25–4×
- Таймер сна, нормализация громкости

</td>
<td width="50%" valign="top">

### 🔒 Приватность
- Без аккаунта Google и без входа
- Ноль аналитики, трекеров и рекламы
- Подписки и история хранятся только на устройстве
- Прокси, Orbot, DNS-over-HTTPS
- Сборка `foss` без Google Play Services

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 📚 Библиотека
- Локальные подписки с группами
- История, «Смотреть позже», плейлисты
- Импорт из NewPipe, LibreTube, FreeTube, Google Takeout
- Резервные копии в выбранную папку
- Загрузки для офлайн-просмотра

</td>
<td width="50%" valign="top">

### 🎨 Дизайн
- Material 3 Expressive: формы, морфинг, выразительные анимации
- Dynamic Color и своя палитра
- Тёмная, AMOLED и высококонтрастная темы
- Телефоны, планшеты, складные и ChromeOS
- Опционально: SponsorBlock, DeArrow, Return YouTube Dislike

</td>
</tr>
</table>

## 📥 Скачать

Готовые APK публикуются в **[Releases](https://github.com/mi8lite2tel-cmyk/YouTubely/releases)**.

| Файл | Для кого |
|---|---|
| `YouTubely-<версия>-foss.apk` | **Рекомендуется.** Без Google Play Services, как будет в F-Droid |
| `YouTubely-<версия>-full.apk` | То же + (в будущем) трансляция на Chromecast |

Чтобы получать обновления автоматически, добавьте репозиторий в
[Obtainium](https://github.com/ImranR98/Obtainium): `https://github.com/mi8lite2tel-cmyk/YouTubely`.

Позже приложение появится в F-Droid и IzzyOnDroid. Google Play не планируется: его правила
запрещают сторонние клиенты YouTube.

## 🧭 Roadmap

- [x] Каркас проекта: модули, convention-плагины, version catalog
- [x] Тема Material 3 Expressive, Dynamic Color, AMOLED и высокий контраст
- [x] CI и автоматические релизы с подписанными APK
- [ ] Получение потоков через NewPipeExtractor
- [ ] Навигация, адаптивная оболочка, поиск
- [ ] Плеер: фон, PiP, аудио-режим
- [ ] Каналы, плейлисты, комментарии
- [ ] Локальные подписки и лента
- [ ] Загрузки
- [ ] SponsorBlock, DeArrow, Return YouTube Dislike
- [ ] F-Droid и IzzyOnDroid

## 🔧 Сборка из исходников

Нужны **JDK 21** и **Android SDK** (platform 37).

```sh
git clone https://github.com/mi8lite2tel-cmyk/YouTubely.git
cd YouTubely
./gradlew assembleFossDebug
```

APK появится в `app/build/outputs/apk/foss/debug/`.

<details>
<summary>Другие команды</summary>

| Команда | Что делает |
|---|---|
| `./gradlew assembleFullDebug` | debug-сборка flavor-а `full` |
| `./gradlew assembleFossRelease` | release-сборка с R8 (без ключа — неподписанная) |
| `./gradlew test` | юнит-тесты |
| `./gradlew spotlessCheck detekt lintFossDebug` | форматирование, статический анализ, Android Lint |
| `./gradlew spotlessApply` | автоформатирование и SPDX-заголовки |

</details>

Как выпустить релиз — в [docs/releasing.md](docs/releasing.md).

## 🧱 Архитектура

Модульное приложение на Kotlin и Jetpack Compose: однонаправленный поток данных (UDF),
offline-first, Hilt для DI. Весь доступ к источнику видео идёт через интерфейс `StreamProvider`,
поэтому бэкенд можно заменить, не трогая экраны.

```
app/                 Application, MainActivity, flavors foss / full
build-logic/         convention-плагины Gradle
core/
├─ common/           Outcome, DispatcherProvider
├─ model/            доменные модели: VideoId, StreamItem, StreamInfo, …
├─ provider/         StreamProvider — единственный путь к источнику видео
└─ designsystem/     тема Material 3 Expressive
config/              Detekt, Compose stability
docs/adr/            архитектурные решения
tools/color/         генератор палитры (Material Color Utilities)
```

<details>
<summary>Технологии</summary>

| Область | Стек |
|---|---|
| Язык | Kotlin 2.4, Coroutines, Serialization |
| UI | Jetpack Compose, Material 3 1.5 (Expressive) |
| DI | Hilt + KSP |
| Сборка | Gradle 9.8, AGP 9.4, version catalog, convention-плагины |
| Качество | Detekt, Spotless (ktlint), Android Lint, Renovate |
| Планируется | Navigation 3, Media3, NewPipeExtractor, Room 3, DataStore, Coil 3, WorkManager |

Версии и откуда они взяты — в [ADR 0002](docs/adr/0002-versions.md).

</details>

## 🤝 Участие

Issues и Pull Request приветствуются. Перед PR запустите
`./gradlew spotlessApply detekt test` — то же самое проверяет CI.
Правила проекта — в [CLAUDE.md](CLAUDE.md), принятые решения — в [docs/adr](docs/adr/README.md).

## 📄 Лицензия

YouTubely распространяется под лицензией [GPL-3.0-or-later](LICENSE).
Почему не MIT, объясняет [ADR 0001](docs/adr/0001-license.md).

<div align="center">
<sub>Сделано с ❤️ и Material 3 Expressive</sub>
</div>
