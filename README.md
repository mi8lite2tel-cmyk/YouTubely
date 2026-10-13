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
> **Проект в ранней разработке.** Уже работают главная с лентой подписок, поиск, каналы и плейлисты,
> плеер с жестами, аудио-режимом, фоном и «картинкой в картинке», локальные подписки и история,
> загрузки для офлайн-просмотра. Комментариев и SponsorBlock пока нет.

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
- Аудио-режим: только звук, без загрузки картинки
- Жесты: двойное касание ±5–30 с, удержание — 2×, яркость, громкость, масштаб
- Продолжение с места остановки, плейлисты по порядку
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
- Локальные подписки и лента новых видео (RSS, без аккаунта)
- История просмотра с паузой и очисткой
- Каналы и плейлисты
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
- [x] Получение потоков через NewPipeExtractor
- [x] Главная с разделами (музыка, игры, трансляции, подкасты)
- [x] Поиск с подсказками и фильтрами
- [x] Адаптивная навигация: нижняя панель на телефоне, боковая на планшете
- [x] Плеер: адаптивное качество до 4K, скорость 0.25–4×, фон, PiP, экран блокировки
- [x] Настройки темы и лимиты качества для Wi-Fi и мобильной сети
- [x] Аудио-режим
- [x] Загрузки: выбор качества или только аудио, докачка, вкладка «Скачанные», просмотр без интернета
- [x] Жесты в плеере: перемотка двойным касанием, 2× удержанием, яркость, громкость, масштаб
- [x] Каналы и плейлисты, воспроизведение плейлиста по порядку
- [x] Локальные подписки, лента подписок на главной, история с продолжением просмотра
- [ ] Комментарии, главы на шкале, субтитры
- [ ] Таймер сна, «Смотреть позже», свои плейлисты
- [ ] Импорт подписок из NewPipe и Google Takeout
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
├─ database/         Room 3: история и подписки
├─ data/             репозитории истории и подписок, лента подписок
├─ model/            доменные модели: VideoId, StreamItem, StreamInfo, …
├─ network/          OkHttp и загрузчик для NewPipeExtractor
├─ provider/         StreamProvider + реализация на NewPipeExtractor
├─ player/           Media3: DASH-манифест на устройстве, фоновый сервис, медиа-сессия
├─ datastore/        настройки (DataStore)
├─ download/         загрузки: WorkManager, докачка, склейка видео и аудио в MP4 через Media3
├─ designsystem/     тема Material 3 Expressive, Roboto Flex, Material Symbols
└─ ui/               карточки видео, состояния загрузки и ошибок
feature/
├─ home/ search/ watch/ channel/ playlist/ library/ downloads/ settings/
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
| Навигация | Navigation 3, адаптивный NavigationSuiteScaffold |
| Плеер | Media3 (ExoPlayer, DASH/HLS, MediaSession) |
| Данные | NewPipeExtractor, OkHttp, Coil 3, DataStore, Room 3 |
| Загрузки | WorkManager, Media3 Transformer (без FFmpeg) |

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
