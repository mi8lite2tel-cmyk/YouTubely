// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.designsystem.theme.isDynamicColorSupported
import app.youtubely.core.model.AnimationSpeed
import app.youtubely.core.model.DarkMode
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.ui.groupedListItemColors
import app.youtubely.core.ui.groupedListItemShapes
import app.youtubely.core.ui.rememberNotificationPermissionRequest

private const val SOURCE_URL = "https://github.com/mi8lite2tel-cmyk/YouTubely"

/** The sub-pages of the settings; the main page lists them in groups. */
internal enum class SettingsPage(
    val title: Int,
    val summary: Int,
    val icon: Int,
) {
    Appearance(R.string.settings_appearance, R.string.settings_cat_appearance, YouTubelyIcons.Palette),
    Playback(R.string.settings_playback, R.string.settings_cat_playback, YouTubelyIcons.Play),
    Quality(R.string.settings_quality, R.string.settings_cat_quality, YouTubelyIcons.DataUsage),
    Subtitles(R.string.settings_subtitles, R.string.settings_cat_subtitles, YouTubelyIcons.Subtitles),
    SponsorBlock(R.string.settings_sponsorblock, R.string.settings_cat_sponsorblock, YouTubelyIcons.FastForward),
    Downloads(R.string.settings_downloads, R.string.settings_cat_downloads, YouTubelyIcons.Download),
    Subscriptions(
        R.string.settings_subscriptions,
        R.string.settings_cat_subscriptions,
        YouTubelyIcons.Subscriptions.outlined,
    ),
    Privacy(R.string.settings_privacy, R.string.settings_cat_privacy, YouTubelyIcons.Shield),
    About(R.string.settings_about, R.string.settings_cat_about, YouTubelyIcons.Info),
}

private val PAGE_GROUPS =
    listOf(
        listOf(SettingsPage.Appearance),
        listOf(SettingsPage.Playback, SettingsPage.Quality, SettingsPage.Subtitles, SettingsPage.SponsorBlock),
        listOf(SettingsPage.Downloads, SettingsPage.Subscriptions),
        listOf(SettingsPage.Privacy, SettingsPage.About),
    )

@Composable
fun SettingsScreen(
    versionName: String,
    onOpenTransfer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motion = MaterialTheme.motionScheme
    BackHandler(enabled = page != null) { page = null }
    // Every page starts with the large title expanded.
    LaunchedEffect(page) {
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(page?.title ?: R.string.settings_title)) },
                navigationIcon = {
                    if (page != null) {
                        IconButton(onClick = { page = null }) {
                            Icon(painterResource(YouTubelyIcons.ArrowBack), stringResource(R.string.settings_back))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != null
                val direction = if (forward) 1 else -1
                val enter = slideInHorizontally(motion.defaultSpatialSpec()) { direction * it / SLIDE_FRACTION }
                (enter + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(
                        slideOutHorizontally(motion.defaultSpatialSpec()) { -direction * it / SLIDE_FRACTION } +
                            fadeOut(motion.fastEffectsSpec()),
                    )
            },
            label = "settings-page",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            LazyColumn(
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (current == null) {
                    mainPage(onOpen = { page = it })
                } else {
                    item(key = current.name) {
                        SettingsPageContent(current, viewModel, versionName, onOpenTransfer)
                    }
                }
            }
        }
    }
}

private const val SLIDE_FRACTION = 4

internal fun LazyListScope.mainPage(onOpen: (SettingsPage) -> Unit) {
    PAGE_GROUPS.forEachIndexed { groupIndex, group ->
        if (groupIndex > 0) item(key = "gap-$groupIndex") { Box(Modifier.height(12.dp)) }
        group.forEachIndexed { index, page ->
            item(key = page.name) {
                CategoryRow(
                    title = page.title,
                    summary = page.summary,
                    icon = page.icon,
                    position = RowPosition(index, group.size),
                ) { onOpen(page) }
            }
        }
    }
}

@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    viewModel: SettingsViewModel,
    versionName: String,
    onOpenTransfer: () -> Unit,
) {
    val playback by viewModel.playbackSettings.collectAsStateWithLifecycle()
    Column {
        when (page) {
            SettingsPage.Appearance -> {
                AppearancePage(viewModel)
            }

            SettingsPage.Playback -> {
                PlaybackPage(playback, viewModel)
            }

            SettingsPage.Quality -> {
                val traffic by viewModel.traffic.collectAsStateWithLifecycle()
                QualitySettings(playback, viewModel::playback)
                SectionHeader(R.string.settings_traffic)
                TrafficSettings(traffic, playback, viewModel::playback, viewModel::resetTraffic)
            }

            SettingsPage.Subtitles -> {
                SubtitleSettings(playback, viewModel::playback)
            }

            SettingsPage.SponsorBlock -> {
                val sponsor by viewModel.sponsorBlockSettings.collectAsStateWithLifecycle()
                SponsorBlockSettingsSection(sponsor, viewModel::sponsorBlock)
            }

            SettingsPage.Downloads -> {
                DownloadsPage(playback, viewModel)
            }

            SettingsPage.Subscriptions -> {
                SubscriptionsPage(viewModel, onOpenTransfer)
            }

            SettingsPage.Privacy -> {
                SettingsGroup {
                    SwitchItem(
                        title = R.string.settings_history,
                        summary = R.string.settings_history_summary,
                        icon = YouTubelyIcons.History,
                        checked = playback.historyEnabled,
                        position = RowPosition(0, 1),
                    ) { viewModel.playback { setHistoryEnabled(it) } }
                }
            }

            SettingsPage.About -> {
                AboutPage(versionName)
            }
        }
    }
}

@Composable
private fun AppearancePage(viewModel: SettingsViewModel) {
    val theme by viewModel.themeSettings.collectAsStateWithLifecycle()
    val dynamicAvailable = isDynamicColorSupported
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PalettePreview()
        DarkModeSelector(theme.darkMode, viewModel::setDarkMode)
    }
    Spacer(Modifier.height(16.dp))
    SettingsGroup {
        SwitchItem(
            title = R.string.settings_dynamic_color,
            summary =
                if (dynamicAvailable) {
                    R.string.settings_dynamic_color_summary
                } else {
                    R.string.settings_dynamic_color_unsupported
                },
            icon = YouTubelyIcons.Palette,
            checked = theme.dynamicColor && dynamicAvailable,
            enabled = dynamicAvailable,
            position = RowPosition(0, APPEARANCE_ROWS),
            onChange = viewModel::setDynamicColor,
        )
        SwitchItem(
            title = R.string.settings_pure_black,
            summary = R.string.settings_pure_black_summary,
            icon = YouTubelyIcons.DarkMode,
            checked = theme.pureBlack,
            position = RowPosition(1, APPEARANCE_ROWS),
            onChange = viewModel::setPureBlack,
        )
        SwitchItem(
            title = R.string.settings_high_contrast,
            summary = R.string.settings_high_contrast_summary,
            icon = YouTubelyIcons.Contrast,
            checked = theme.highContrast,
            enabled = !(theme.dynamicColor && dynamicAvailable),
            position = RowPosition(2, APPEARANCE_ROWS),
            onChange = viewModel::setHighContrast,
        )
    }
    SectionHeader(R.string.settings_animation_speed)
    AnimationSpeedRow(theme.animationSpeed, viewModel::setAnimationSpeed)
}

private const val APPEARANCE_ROWS = 3
private const val PLAYBACK_ROWS = 7

@Composable
private fun PlaybackPage(
    playback: PlaybackSettings,
    viewModel: SettingsViewModel,
) {
    val rows =
        listOf(
            Triple(R.string.settings_background, R.string.settings_background_summary, YouTubelyIcons.Music) to
                (playback.backgroundPlayback to { on: Boolean -> viewModel.playback { setBackgroundPlayback(on) } }),
            Triple(R.string.settings_pip, R.string.settings_pip_summary, YouTubelyIcons.Video) to
                (playback.pictureInPicture to { on: Boolean -> viewModel.playback { setPictureInPicture(on) } }),
            Triple(R.string.settings_audio_only, R.string.settings_audio_only_summary, YouTubelyIcons.Headphones) to
                (playback.audioOnly to { on: Boolean -> viewModel.playback { setAudioOnly(on) } }),
            Triple(R.string.settings_resume, R.string.settings_resume_summary, YouTubelyIcons.History) to
                (playback.resumePlayback to { on: Boolean -> viewModel.playback { setResumePlayback(on) } }),
            Triple(
                R.string.settings_skip_silence,
                R.string.settings_skip_silence_summary,
                YouTubelyIcons.FastForward,
            ) to
                (playback.skipSilence to { on: Boolean -> viewModel.playback { setSkipSilence(on) } }),
            Triple(R.string.settings_normalize, R.string.settings_normalize_summary, YouTubelyIcons.Volume) to
                (playback.normalizeVolume to { on: Boolean -> viewModel.playback { setNormalizeVolume(on) } }),
            Triple(R.string.settings_autoplay, R.string.settings_autoplay_summary, YouTubelyIcons.SkipNext) to
                (playback.autoplayNext to { on: Boolean -> viewModel.playback { setAutoplayNext(on) } }),
        )
    SettingsGroup {
        rows.forEachIndexed { index, (text, state) ->
            SwitchItem(
                title = text.first,
                summary = text.second,
                icon = text.third,
                checked = state.first,
                position = RowPosition(index, PLAYBACK_ROWS),
                onChange = state.second,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    SeekStepSelector(playback.seekStepSeconds) { viewModel.playback { setSeekStepSeconds(it) } }
}

@Composable
private fun DownloadsPage(
    playback: PlaybackSettings,
    viewModel: SettingsViewModel,
) {
    SettingsGroup {
        SwitchItem(
            title = R.string.settings_download_wifi_only,
            summary = R.string.settings_download_wifi_only_summary,
            icon = YouTubelyIcons.Wifi,
            checked = playback.downloadOnWifiOnly,
            position = RowPosition(0, 2),
        ) { viewModel.playback { setDownloadOnWifiOnly(it) } }
        ChoiceRow(
            title = R.string.settings_download_connections,
            icon = YouTubelyIcons.Speed,
            options = PlaybackSettings.CONNECTION_CHOICES.map { it to it.toString() },
            selected = playback.downloadConnections,
            position = RowPosition(1, 2),
        ) { viewModel.playback { setDownloadConnections(it) } }
    }
    Text(
        stringResource(R.string.settings_download_connections_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SubscriptionsPage(
    viewModel: SettingsViewModel,
    onOpenTransfer: () -> Unit,
) {
    val sync by viewModel.syncSettings.collectAsStateWithLifecycle()
    val askNotificationPermission = rememberNotificationPermissionRequest()
    RefreshSettings(sync, viewModel::sync, askNotificationPermission)
    SectionHeader(R.string.settings_auto_download)
    AutoDownloadSettings(sync, viewModel::sync)
    SectionHeader(R.string.settings_transfer)
    SegmentedListItem(
        onClick = onOpenTransfer,
        shapes = groupedListItemShapes(0, 1),
        colors = groupedListItemColors(),
        leadingContent = { Icon(painterResource(YouTubelyIcons.QrCode), contentDescription = null) },
        trailingContent = { Icon(painterResource(YouTubelyIcons.ChevronRight), contentDescription = null) },
        supportingContent = { Text(stringResource(R.string.settings_transfer_summary)) },
    ) { Text(stringResource(R.string.settings_transfer)) }
}

@Composable
private fun AboutPage(versionName: String) {
    val context = LocalContext.current
    SettingsGroup {
        SegmentedListItem(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri())) },
            shapes = groupedListItemShapes(0, 2),
            colors = groupedListItemColors(),
            leadingContent = { Icon(painterResource(YouTubelyIcons.Info), contentDescription = null) },
            supportingContent = { Text(stringResource(R.string.settings_version, versionName)) },
        ) { Text(stringResource(R.string.settings_source)) }
        SegmentedListItem(
            onClick = {},
            enabled = false,
            shapes = groupedListItemShapes(1, 2),
            colors = groupedListItemColors(),
            leadingContent = { Icon(painterResource(YouTubelyIcons.Language), contentDescription = null) },
            supportingContent = { Text(stringResource(R.string.settings_license_summary)) },
        ) { Text(stringResource(R.string.settings_license)) }
    }
}

/** System / light / dark as a connected button group with shape-morphing toggle buttons. */
@Composable
private fun DarkModeSelector(
    selected: DarkMode,
    onSelect: (DarkMode) -> Unit,
) {
    val modes =
        listOf(
            Triple(DarkMode.System, R.string.settings_theme_system, YouTubelyIcons.BrightnessAuto),
            Triple(DarkMode.Light, R.string.settings_theme_light, YouTubelyIcons.LightMode),
            Triple(DarkMode.Dark, R.string.settings_theme_dark, YouTubelyIcons.DarkMode),
        )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        modes.forEachIndexed { index, (mode, label, icon) ->
            ToggleButton(
                checked = selected == mode,
                onCheckedChange = { onSelect(mode) },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                modifier = Modifier.weight(1f),
            ) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(label), Modifier.padding(start = 6.dp), maxLines = 1)
            }
        }
    }
}

/** Double-tap seek step as a connected button group. */
@Composable
private fun SeekStepSelector(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val choices = PlaybackSettings.SEEK_STEP_CHOICES
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(YouTubelyIcons.FastForward), contentDescription = null)
            Column {
                Text(stringResource(R.string.settings_seek_step), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_seek_step_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            choices.forEachIndexed { index, seconds ->
                ToggleButton(
                    checked = selected == seconds,
                    onCheckedChange = { onSelect(seconds) },
                    shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_seconds, seconds), maxLines = 1)
                }
            }
        }
    }
}

/** Live preview of the current scheme; it cross-fades together with the rest of the app. */
@Composable
private fun PalettePreview() {
    val colors = MaterialTheme.colorScheme
    val swatches =
        listOf(
            colors.primary to MaterialShapes.Cookie9Sided,
            colors.secondary to MaterialShapes.Clover4Leaf,
            colors.tertiary to MaterialShapes.SoftBurst,
            colors.primaryContainer to MaterialShapes.Sunny,
            colors.tertiaryContainer to MaterialShapes.Pill,
        )
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(20.dp).height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            swatches.forEach { (color, shape) ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(48.dp).clip(shape.toShape()).background(color))
                }
            }
        }
    }
}

/** How long animations take: stretches the whole app's motion. */
@Composable
private fun AnimationSpeedRow(
    selected: AnimationSpeed,
    onSelect: (AnimationSpeed) -> Unit,
) {
    val options =
        AnimationSpeed.entries.map { speed ->
            speed to
                stringResource(
                    when (speed) {
                        AnimationSpeed.Standard -> R.string.settings_animation_standard
                        AnimationSpeed.Relaxed -> R.string.settings_animation_relaxed
                        AnimationSpeed.Slow -> R.string.settings_animation_slow
                        AnimationSpeed.VerySlow -> R.string.settings_animation_very_slow
                    },
                )
        }
    ChoiceRow(
        title = R.string.settings_animation_speed,
        icon = YouTubelyIcons.Schedule,
        options = options,
        selected = selected,
        position = RowPosition(0, 1),
        onChange = onSelect,
    )
}
