# YouTubely — мастер-промпт для AI-агента

> Как пользоваться:
> 1. Часть A целиком положи в корень репозитория как `AGENTS.md` (или `CLAUDE.md`) — это постоянный контекст проекта.
> 2. Часть B — это набор задач по фазам. Вставляй по одной фазе за раз в Claude Code / Cursor / Codex CLI, запущенный в клоне `https://github.com/mi8lite2tel-cmyk/YouTubely`.
> 3. Версии библиотек проверены по официальным релиз-нотам на 12.10.2026. Агент обязан перепроверять их перед записью в `libs.versions.toml`.

---

# ЧАСТЬ A — ПОСТОЯННЫЙ КОНТЕКСТ ПРОЕКТА

## A0. Роль

Ты — Principal Android Engineer и Material Design-инженер уровня Google. Твоя задача: спроектировать и построить с нуля **YouTubely** — open-source Android-клиент YouTube с полным Material 3 Expressive, новейшим Jetpack Compose и набором «премиум»-функций, работающих без Google-аккаунта и без рекламы.

Ты работаешь автономно, но дисциплинированно: маленькие коммиты, зелёная сборка после каждого шага, никаких заглушек и выдуманных API.

## A1. Состояние репозитория

- Репозиторий: `https://github.com/mi8lite2tel-cmyk/YouTubely` (public, ветка `main`).
- Сейчас там один коммит: `LICENSE` (MIT) и пустой `README.md`. Кода нет. Описания, topics и релизов нет.
- Все изменения идут через ветки `feat/...`, `fix/...`, `chore/...` и Pull Request в `main` (используй `gh` CLI, если доступен; иначе обычный git). `main` всегда собирается и проходит CI.

## A2. Критическое решение по лицензии (сделай первым, до кода)

Репозиторий под MIT, но лучший открытый экстрактор стримов — **NewPipeExtractor — под GPL-3.0**. Если приложение линкуется с ним, итоговая сборка распространяется под GPL-3.0.

Действуй так:
1. Реши между вариантами и **спроси владельца одним коротким вопросом** (`AskUserQuestion`), если он на связи; если нет — выбери вариант 1 и запиши решение в `docs/adr/0001-license.md`:
   - **Вариант 1 (рекомендуемый):** переключить лицензию проекта на `GPL-3.0-or-later`, как у NewPipe и LibreTube. Заменить `LICENSE`, добавить SPDX-заголовки в файлы.
   - **Вариант 2:** оставить MIT и не линковать NewPipeExtractor, а писать собственный клиент Innertube и/или использовать API инстансов Piped/Invidious. Это дороже, хрупче и юридически не чище.
2. Весь доступ к источникам видео спрятать за интерфейсом `StreamProvider` (см. A6), чтобы бэкенд можно было заменить без переписывания приложения.

## A3. Юридические и этические рамки (обязательны)

Делай:
- Использовать только публичные данные и публичные потоки, как NewPipe/LibreTube.
- Показать при первом запуске понятное уведомление: приложение неофициальное, не связано с Google/YouTube; использование может нарушать условия YouTube; ответственность на пользователе; скачивать можно только то, на что есть права.
- Свой логотип, иконка, название шрифтов и цветов. Не использовать товарные знаки, логотип, красно-белую кнопку play и фирменные иконки YouTube.
- Без аналитики, трекеров, краш-репортеров третьих сторон и рекламных SDK.
- Опциональные внешние сервисы (SponsorBlock, DeArrow, Return YouTube Dislike) — только opt-in, с понятным описанием, какие данные уходят, и с возможностью указать свой сервер.

Не делай:
- Не реализуй обход DRM, платных/Premium-only/приватных/членских (membership) материалов и возрастных ограничений через чужие учётные данные.
- Не используй украденные/вшитые ключи, куки и токены. Не имитируй вход в аккаунт Google.
- Не добавляй функции, нацеленные на нарушение авторских прав (массовое скачивание каналов для перераспространения и т.п.).

Что значит «премиум» в этом проекте: функции, которые YouTube продаёт по подписке, но которые честно реализуются на стороне клиента из публичных потоков — фоновое воспроизведение, PiP, загрузки для личного офлайн-просмотра, отсутствие рекламы, аудио-режим, SponsorBlock и т.д.

## A4. Дистрибуция

- Google Play не подходит (политика на клиенты YouTube). Каналы: GitHub Releases, F-Droid (flavor `foss`), IzzyOnDroid, Obtainium.
- Android Developer Verification: с 30.09.2026 установка на сертифицированные устройства в Бразилии, Индонезии, Сингапуре и Таиланде требует приложения от верифицированного разработчика, глобально — с 2027. Подготовь инструкцию `docs/distribution.md`: как владелец регистрирует package name и ключ подписи в Android Developer Console, как это влияет на F-Droid (подпись своим ключом F-Droid) и на GitHub-релизы, как устанавливать через ADB для тестов.
- Сборки воспроизводимые (reproducible builds), подпись — отдельным release-ключом через GitHub Secrets, никогда не в репозитории.
- Два flavor-а: `foss` (без Google Play Services, F-Droid-совместим) и `full` (+ Google Cast). Всё остальное идентично.

## A5. Технологический стек (актуально на 12.10.2026, перепроверь перед использованием)

| Область | Решение | Версия на 12.10.2026 |
|---|---|---|
| Язык | Kotlin, K2, `kotlinx-coroutines`, `kotlinx-serialization`, `kotlinx-collections-immutable` | Kotlin ≥ 2.2 (Media3 1.11 сам собирается на 2.2.0) — взять последнюю стабильную |
| Сборка | Gradle (wrapper, последняя), AGP (последняя стабильная), JDK 21, version catalog `libs.versions.toml`, `build-logic` с convention-плагинами | проверить на developer.android.com |
| UI | Jetpack Compose (ui, foundation, animation, runtime) | **1.12.1** stable; 1.13.0-beta01 — только в отдельной ветке-эксперименте |
| Material | `androidx.compose.material3` | **1.5.0-beta01** (07.10.2026); stable — 1.4.0. Если вышел 1.5.0 stable — брать его |
| Адаптивность | `material3-adaptive`, `material3-adaptive-navigation-suite`, `androidx.window` | последние стабильные |
| Навигация | **Navigation 3** (`androidx.navigation3:navigation3-runtime` и `-ui`) | **1.2.0** stable (23.09.2026) |
| Плеер | **Media3**: `exoplayer`, `exoplayer-dash`, `exoplayer-hls`, `session`, `ui-compose`, `ui-compose-material3`, `datasource-okhttp`, `transformer`, `muxer`, `exoplayer-workmanager`, `cast` (только `full`) | **1.11.1** (10.09.2026) |
| Экстрактор | NewPipeExtractor (JitPack), по умолчанию за `StreamProvider` | **v0.26.5** (GPL-3.0) |
| БД | Room 3 (`androidx.room3`, KSP) | **3.0.3** (09.09.2026). При проблемах миграции — Room 2.8.x, зафиксировать в ADR |
| Настройки | DataStore (Preferences/Proto) | последняя |
| DI | Hilt + KSP | последняя |
| Сеть | OkHttp (+ `okhttp-brotli`), DoH-резолвер | последняя |
| Картинки | Coil 3 (`coil-compose`, `coil-network-okhttp`) | последняя |
| Пагинация | Paging 3 | 3.5.x |
| Фон | WorkManager | последняя |
| Виджеты | Glance | последняя |
| Цвета | `androidx.palette` / Material Color Utilities | последняя |
| Тесты | JUnit5/4, MockK, Turbine, Robolectric, Compose UI Test, Roborazzi (скриншоты), MockWebServer, `media3-test-utils` | последние |
| Perf | Macrobenchmark + Baseline Profile (`androidx.benchmark` 1.5.0), Compose compiler reports, LeakCanary (debug) | — |
| Качество | Detekt, Spotless (ktlint), Android Lint, dependency-verification, Renovate | — |

Параметры SDK: `minSdk = 26`, `compileSdk`/`targetSdk` — последний стабильный (на 12.10.2026 это 37; проверь). Core library desugaring включён (нужен NewPipeExtractor).

Правило версий: **перед первой записью в `libs.versions.toml` открой официальные релиз-ноты (developer.android.com/jetpack/androidx/releases/...) и GitHub Releases, сверь номера, зафиксируй в `docs/adr/0002-versions.md` с датой проверки.** Compose BOM смотри на странице «Compose BOM mapping»; если BOM маппит material3 на 1.4.0, переопредели material3 явно.

## A6. Архитектура

Принципы: UDF/MVI, state hoisting, single source of truth, оффлайн-first, структурная конкурентность, модульность по фичам, строгие границы слоёв.

```
YouTubely/
├─ build-logic/                    # convention plugins: android-app, android-lib, compose, hilt, room3, detekt, spotless
├─ app/                            # Application, MainActivity, root nav (Navigation 3), flavors foss/full
├─ core/
│  ├─ common/                      # DispatcherProvider, Result/Outcome, Logger, extensions
│  ├─ designsystem/                # тема Expressive, токены, компоненты-обёртки, иконки, шрифты
│  ├─ ui/                          # общие composable: VideoCard, ChannelRow, Shimmer, Error/Empty states
│  ├─ model/                       # доменные модели (Video, Channel, Playlist, StreamInfo, ...)
│  ├─ network/                     # OkHttp, Downloader для экстрактора, DoH, прокси
│  ├─ provider/                    # интерфейс StreamProvider + реализации (newpipe/, piped/ позже)
│  ├─ database/                    # Room 3: сущности, DAO, миграции
│  ├─ datastore/                   # настройки (Proto)
│  ├─ player/                      # PlaybackService, PlayerPool, MediaSource-фабрики, PiP, сессия
│  ├─ download/                    # менеджер загрузок, слияние потоков (Transformer/Muxer)
│  ├─ sponsorblock/ dearrow/ ryd/  # опциональные интеграции
│  └─ testing/                     # фейки, фикстуры, тестовые правила
├─ feature/
│  ├─ home/ search/ watch/ channel/ shorts/ comments/
│  ├─ library/ subscriptions/ history/ playlists/ downloads/
│  ├─ settings/ onboarding/ about/
├─ benchmark/                      # Macrobenchmark + Baseline Profile generator
├─ docs/                           # ADR, архитектура, дистрибуция, вклад
└─ fastlane/metadata/android/      # метаданные для F-Droid (en-US, ru-RU, uk, lt)
```

Слои внутри фичи: `ui` (Composable + ViewModel + UiState/UiEvent/UiEffect) → `domain` (UseCase только если есть логика) → репозитории из `core`. ViewModel не знает про Compose; экраны получают состояние как `StateFlow` и читают через `collectAsStateWithLifecycle()`.

**Интерфейс бэкенда (обязателен):**
```kotlin
interface StreamProvider {
    suspend fun search(query: String, filter: SearchFilter, page: Page?): Paged<SearchItem>
    suspend fun suggestions(query: String): List<String>
    suspend fun streamInfo(videoId: VideoId): StreamInfo       // форматы, сабы, главы, related, loudness
    suspend fun channel(id: ChannelId, tab: ChannelTab, page: Page?): ChannelPage
    suspend fun playlist(id: PlaylistId, page: Page?): PlaylistPage
    suspend fun comments(videoId: VideoId, sort: CommentSort, page: Page?): Paged<Comment>
    suspend fun trending(region: Region): List<StreamItem>
    suspend fun channelFeed(id: ChannelId): List<StreamItem>   // публичный RSS как лёгкий путь
}
```
Реализация по умолчанию — `NewPipeStreamProvider`. Ошибки — sealed `ProviderError` (Network, Geo-block, AgeRestricted, Paid, Removed, RateLimited, ExtractorOutdated, Unknown) с локализованными сообщениями и кнопкой «Повторить».

## A7. Дизайн-система — Material 3 Expressive на максимум

Правила:
- Только `androidx.compose.material3.*`. Импорты `androidx.compose.material.*` (M2) запрещены — добавь Detekt/Lint-правило.
- Оборачивай приложение в `MaterialExpressiveTheme` с `MotionScheme.expressive()`. Эксперименталки отмечай `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` на уровне файла, не модуля. Если API в 1.5.0-beta01 отличается от того, что написано здесь, прав официальный KDoc — не угадывай.
- **Цвет:** Dynamic Color (Android 12+) включён по умолчанию; фоллбэк-палитра от seed `#7C4DFF` (не красная — чтобы не путаться с YouTube). Темы: Expressive/Standard, светлая/тёмная/системная, AMOLED-чёрная, высокий контраст. Режим «ambient» на экране просмотра: динамическая палитра из обложки видео (Palette/Color Utilities) с плавным кроссфейдом и мягким свечением вокруг плеера (`Modifier.blur`/`RenderEffect` на API 31+, градиент на старых).
- **Типографика:** Roboto Flex как переменный шрифт (OFL) с осями weight/width/grade через `FontVariation`; использовать emphasized-стили (`bodyLargeEmphasized`, `titleMediumEmphasized` и т.д.) для заголовков, названий и активных элементов.
- **Формы:** `MaterialShapes` + `toShape()` и morphing: аватары каналов — cookie/clover-формы, кнопки при нажатии меняют форму (`ButtonDefaults.shapes()` с pressed/checked shape), FilterChip с морфингом при выборе. Скругления из `MaterialTheme.shapes`, без своих «магических» значений.
- **Движение:** все анимации через `MaterialTheme.motionScheme` (spatial vs effects specs), никаких захардкоженных `tween`, кроме обоснованных. Shared element transitions (`SharedTransitionLayout`) для превью→плеер, аватар→канал, mini→full player. Predictive back на всех уровнях (system back + кастомные bottom sheet/плеер). Учитывать системную «Убрать анимации».
- **Иконки:** Material Symbols Rounded как переменный шрифт или векторы с осью FILL (анимация заливки у выбранной вкладки). Адаптивная и **monochrome**-иконка приложения (themed icons).
- **Edge-to-edge** везде, правильные insets (`WindowInsets.safeDrawing`, IME, cutout), 3-кнопочная и жестовая навигация.

Карта компонентов (используй именно эти, не изобретай свои, если есть готовый M3):

| Где | Компонент |
|---|---|
| Нижняя навигация (compact) | `ShortNavigationBar` + `ShortNavigationBarItem` |
| Rail (medium/expanded) | `WideNavigationRail` (collapsed/expanded), `ModalWideNavigationRail` на medium |
| Выбор между ними | `NavigationSuiteScaffold` с кастомным `layoutType` по window size class |
| Верхняя панель | `AppBarWithSearch` + `SearchBar` (state-based), `LargeFlexibleTopAppBar`/`MediumFlexibleTopAppBar` на листах, `TopAppBarDefaults.enterAlwaysScrollBehavior` |
| Поиск | `ExpandedFullScreenContainedSearchBar` (compact), `ExpandedDockedSearchBar` (large) |
| Фильтры ленты/поиска | `FilterChip` с shape morphing, `InputChip` для тегов/групп |
| Быстрые действия в плеере | `HorizontalFloatingToolbar` (like, save, share, download, queue) c `FloatingActionButton` внутри |
| Меню создания в библиотеке | `FloatingActionButtonMenu` |
| Скачать с выбором качества | `SplitButton` (основное действие + выпадающее меню качества) |
| Скорость, лупа, автоплей | `ButtonGroup` с `ToggleButton`/`FilledTonalToggleButton`, connected button group для пресетов скорости |
| Списки (настройки, очередь, история) | expressive `ListItem` / `SegmentedListItem`, `SwipeToDismissBox` для удаления |
| Витрины | `HorizontalCenteredHeroCarousel`, `HorizontalMultiBrowseCarousel`, `HorizontalUncontainedCarousel` |
| Загрузка | `LoadingIndicator` / `ContainedLoadingIndicator`; прогресс — `LinearWavyProgressIndicator`/`CircularWavyProgressIndicator` (загрузки, буферизация); просмотренная часть превью — обычный `LinearProgressIndicator` |
| Pull-to-refresh | `PullToRefreshBox` с expressive loading indicator |
| Нижние листы | `ModalBottomSheet` / `BottomSheetScaffold` с `rememberBottomSheetState` (1.5), `Scrim` |
| Диалоги | `AlertDialog` / `BasicAlertDialog` |
| Таймер сна | expressive `TimePicker`-диалог (Vibrant), либо `ScrollField` |
| Слайдеры | `Slider` / `RangeSlider` на `SliderState`/`RangeSliderState` (новая stateful-форма, `onValueChange` обязателен), A–B-повтор через `RangeSlider` |
| Подсказки | `TooltipBox` + `RichTooltip` (онбординг) |
| Профили и ручки | `VerticalDragHandle`, `DragHandle` |
| Адаптивные панели | `ListDetailPaneScaffold` (подписки, библиотека), `SupportingPaneScaffold` (просмотр: видео + комментарии/related) |

Ripple: использовать встроенную M3-конфигурацию (inset focus ring для клавиатуры/ТВ).

Экран «Component Gallery» (debug-only): показывает все используемые компоненты в светлой/тёмной/AMOLED/динамической/высококонтрастной темах и на 3 размерах окна. Он же — база для скриншот-тестов.

## A8. Функциональность

Приоритеты: **P0** — без этого релиза нет; **P1** — для v1.0; **P2** — после.

### Воспроизведение
- P0: разрешение потоков через `StreamProvider`; адаптивное качество до 4K/8K, 60 fps, HDR; кодеки AV1/VP9/H.264 с выбором и учётом поддержки устройства; аудио Opus/AAC; выбор аудиодорожки по языку; DASH/HLS; живые трансляции с DVR.
- P0: **фоновое воспроизведение** (`MediaSessionService`/`MediaLibraryService`, foreground service `mediaPlayback`), уведомление, экран блокировки, Bluetooth/гарнитура, Android Auto (`MediaLibraryService`).
- P0: **PiP** с автоматическим входом при уходе из приложения (`setAutoEnterEnabled`), кнопки в PiP, плавная смена размера.
- P0: **Аудио-режим** (экономия трафика): переключение видео↔аудио одним нажатием с сохранением позиции.
- P1: **SponsorBlock** (категории, режимы: авто-пропуск / спросить / без звука / только метки на таймлайне; запросы по hash-префиксу; кеш в Room).
- P1: **DeArrow** (оригинальные названия и обложки; opt-in).
- P1: **Return YouTube Dislike** (opt-in).
- P1: главы (из экстрактора и описания) с метками на `ProgressSlider`, список глав в листе.
- P1: субтитры + автоперевод, стили субтитров (размер, фон, контур).
- P1: скорость 0.25–4× с сохранением тона, долгое нажатие = 2×, запоминание скорости/качества по каналу.
- P1: жесты: двойной тап — перемотка, свайп — яркость/громкость, pinch — fit/fill, блокировка поворота, авто-фуллскрин при повороте.
- P1: таймер сна (по времени и «до конца видео»).
- P1: пропуск тишины, нормализация громкости (по `loudnessDb` из потока + `LoudnessEnhancer`), эквалайзер (системный через audio session id).
- P2: A–B-повтор, покадровая перемотка, скриншот кадра, Chromecast (flavor `full`).

### Просмотр и поиск
- P0: поиск с подсказками, фильтры (тип, длительность, дата, сортировка), история поиска (можно отключить).
- P0: главная = локальная лента подписок + тренды региона + «связанные» после просмотра (без аккаунта и без рекомендательной слежки).
- P0: страница канала (видео, shorts, live, плейлисты, о канале), страница плейлиста.
- P1: Shorts — `VerticalPager` с предзагрузкой через `PlayerPool`/`rememberPooledPlayer`.
- P1: комментарии (только чтение: сортировка, ответы, закреплённые), описание с кликабельными таймкодами и ссылками.
- P1: обработка ссылок `youtube.com`, `youtu.be`, `music.youtube.com`, shorts-ссылок (App Links/intent filters, «открывать по умолчанию»), определение ссылки в буфере.

### Библиотека
- P0: локальные подписки, группы, фоновое обновление ленты через WorkManager (лёгкий путь — публичные RSS-фиды каналов), уведомления о новых видео (опционально).
- P0: история просмотра с возобновлением (пауза истории, инкогнито), «Смотреть позже», локальные плейлисты.
- P1: импорт/экспорт: NewPipe (JSON/ZIP), Google Takeout (`subscriptions.csv`), OPML, FreeTube, LibreTube.
- P1: резервная копия/восстановление (ZIP: БД + настройки) в выбранную SAF-папку, расписание.
- P2: опциональная синхронизация между устройствами через пользовательский сервер или файл в облаке пользователя.

### Загрузки
- P1: видео+аудио с выбором качества/кодека, только аудио (m4a/opus), субтитры, обложка, метаданные; очередь, пауза/возобновление, параллельные чанки (учесть троттлинг: диапазонные запросы), «только Wi-Fi», лимит хранилища, выбор папки через SAF.
- P1: слияние дорожек **без FFmpeg** — `media3-transformer`/`media3-muxer`; контейнеры MP4/MKV/WebM.
- P1: офлайн-плеер: скачанное играет через тот же плеер, есть в поиске по библиотеке.
- Уведомления с `LinearWavyProgressIndicator`-подобным прогрессом (в приложении), системный прогресс в уведомлении.

### Приватность и безопасность
- P0: ноль аналитики/трекеров; `foss` без GMS; минимум разрешений.
- P1: блокировка приложения биометрией, запрет скриншотов (`FLAG_SECURE`) по настройке, чистка кэша/истории по таймеру.
- P1: прокси (HTTP/SOCKS, Orbot), DoH, свой User-Agent/регион контента.
- P1: блок-листы: каналы и ключевые слова; скрыть Shorts; скрыть просмотренное.

### Кастомизация и платформа
- P1: темы (см. A7), плотность списков, стиль превью, порядок и видимость вкладок навигации, язык приложения на уровне системы (`LocaleManager`, `locales_config.xml`), старт-вкладка.
- P1: адаптивность: телефон, складной (tabletop-posture: видео сверху, управление снизу), планшет, ChromeOS/desktop-окна; горячие клавиши (Space, ←/→, J/L, M, F, ↑/↓), наведение мышью, drag&drop ссылок.
- P1: виджеты Glance («Сейчас играет», «Новое от подписок»), App Shortcuts, Quick Settings tile «Открыть из буфера».
- P1: доступность: TalkBack (осмысленные `contentDescription`/semantics, порядок фокуса), крупный шрифт до 200%, контраст, поддержка клавиатуры/D-pad, RTL.
- P2: Android TV (Compose for TV) — отдельный модуль позже.

## A9. Движок плеер
