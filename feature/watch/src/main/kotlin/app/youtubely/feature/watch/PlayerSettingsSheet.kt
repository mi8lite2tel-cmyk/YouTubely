// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.watch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.QualityCap
import app.youtubely.core.model.QualityReason
import app.youtubely.core.player.AbLoop
import app.youtubely.core.player.QualityOption
import app.youtubely.core.player.SleepTimer
import app.youtubely.core.player.qualityOptions
import app.youtubely.core.player.subtitleOptions
import app.youtubely.core.ui.currentLocale
import app.youtubely.core.ui.formatDuration
import app.youtubely.core.ui.groupedListItemColors
import app.youtubely.core.ui.groupedListItemShapes
import java.time.Duration

private const val STANDARD_FPS = 30
private const val BITS_PER_MEGABIT = 1_000_000.0
private const val SLIDE_FRACTION = 4

/** The pages of the sheet: a short overview, and one page per thing that needs more room. */
internal enum class SheetPage(
    val title: Int,
) {
    Main(R.string.watch_settings),
    Quality(R.string.watch_quality),
    Speed(R.string.watch_speed),
    Subtitles(R.string.watch_subtitles),
    Sleep(R.string.watch_sleep),
    Loop(R.string.watch_loop),
}

/** What the sheet shows about the player right now. */
internal class PlayerSheetState(
    val pinnedHeight: Int?,
    val qualityCap: QualityCap?,
    val settings: PlaybackSettings,
    val sleepTimer: SleepTimer,
    val abLoop: AbLoop,
)

/**
 * Playback settings as an overview of short rows with their current value; quality, speed,
 * subtitles, sleep timer and A–B repeat open their own page inside the sheet.
 */
@Composable
internal fun PlayerSettingsSheet(
    player: Player,
    state: PlayerSheetState,
    actions: PlayerSheetActions,
    onQuality: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SheetPage.Main) }
    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    var repeat by remember { mutableIntStateOf(player.repeatMode) }
    var pinned by remember { mutableStateOf(state.pinnedHeight) }
    val motion = MaterialTheme.motionScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetHeader(page) { page = SheetPage.Main }
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val direction = if (targetState == SheetPage.Main) -1 else 1
                    val enter = slideInHorizontally(motion.defaultSpatialSpec()) { direction * it / SLIDE_FRACTION }
                    (enter + fadeIn(motion.defaultEffectsSpec())).togetherWith(fadeOut(motion.fastEffectsSpec()))
                },
                label = "sheet-page",
            ) { current ->
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    when (current) {
                        SheetPage.Main -> {
                            MainPage(
                                player = player,
                                state = state.copyPinned(pinned),
                                speed = speed,
                                repeat = repeat == Player.REPEAT_MODE_ONE,
                                actions = actions,
                                onRepeat = {
                                    repeat = if (it) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                    player.repeatMode = repeat
                                },
                                onOpen = { page = it },
                            )
                        }

                        SheetPage.Quality -> {
                            QualityPage(player, pinned, state.qualityCap) {
                                pinned = it
                                onQuality(it)
                            }
                        }

                        SheetPage.Speed -> {
                            SpeedPage(speed) {
                                speed = it
                                actions.onSpeed(it)
                            }
                        }

                        SheetPage.Subtitles -> {
                            SubtitlePage(player, state.settings.subtitlesEnabled, actions)
                        }

                        SheetPage.Sleep -> {
                            SleepTimerSection(state.sleepTimer, actions)
                        }

                        SheetPage.Loop -> {
                            LoopSection(state.abLoop, actions)
                        }
                    }
                }
            }
        }
    }
}

private fun PlayerSheetState.copyPinned(height: Int?) =
    PlayerSheetState(height, qualityCap, settings, sleepTimer, abLoop)

@Composable
private fun SheetHeader(
    page: SheetPage,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (page != SheetPage.Main) {
            IconButton(onClick = onBack) {
                Icon(painterResource(YouTubelyIcons.ArrowBack), stringResource(R.string.watch_back))
            }
        }
        Text(
            stringResource(page.title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.padding(start = if (page == SheetPage.Main) 16.dp else 4.dp),
        )
    }
}

@Suppress("LongParameterList") // State of the overview rows; each needs its own value.
@Composable
internal fun MainPage(
    player: Player,
    state: PlayerSheetState,
    speed: Float,
    repeat: Boolean,
    actions: PlayerSheetActions,
    onRepeat: (Boolean) -> Unit,
    onOpen: (SheetPage) -> Unit,
) {
    val subtitles = remember(player) { player.subtitleOptions() }
    val navigation =
        buildList {
            add(Triple(SheetPage.Quality, YouTubelyIcons.Tune, qualityValue(player, state.pinnedHeight)))
            add(Triple(SheetPage.Speed, YouTubelyIcons.Speed, formatSpeed(speed)))
            if (subtitles.isNotEmpty()) {
                val language = subtitles.firstOrNull { it.selected }?.label?.takeIf { state.settings.subtitlesEnabled }
                add(
                    Triple(
                        SheetPage.Subtitles,
                        YouTubelyIcons.Subtitles,
                        language ?: stringResource(R.string.watch_subtitles_off),
                    ),
                )
            }
            add(Triple(SheetPage.Sleep, YouTubelyIcons.Schedule, sleepValue(state.sleepTimer)))
            add(Triple(SheetPage.Loop, YouTubelyIcons.Repeat, loopValue(state.abLoop)))
        }
    navigation.forEachIndexed { index, (page, icon, value) ->
        SegmentedListItem(
            onClick = { onOpen(page) },
            shapes = groupedListItemShapes(index, navigation.size),
            colors = groupedListItemColors(),
            leadingContent = { Icon(painterResource(icon), contentDescription = null) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Icon(painterResource(YouTubelyIcons.ChevronRight), contentDescription = null)
                }
            },
        ) { Text(stringResource(page.title)) }
    }
    SheetGap()
    val toggles =
        listOf(
            Triple(R.string.watch_repeat, YouTubelyIcons.RepeatOne, repeat) to onRepeat,
            Triple(R.string.watch_skip_silence, YouTubelyIcons.FastForward, state.settings.skipSilence) to
                actions.onSkipSilence,
            Triple(R.string.watch_normalize, YouTubelyIcons.Volume, state.settings.normalizeVolume) to
                actions.onNormalizeVolume,
            Triple(R.string.watch_autoplay, YouTubelyIcons.SkipNext, state.settings.autoplayNext) to actions.onAutoplay,
        )
    toggles.forEachIndexed { index, (row, onChange) ->
        ToggleRow(row.first, row.second, row.third, onChange, index, toggles.size)
    }
    actions.onEqualizer?.let { open ->
        SheetGap()
        SegmentedListItem(
            onClick = open,
            shapes = groupedListItemShapes(0, 1),
            colors = groupedListItemColors(),
            leadingContent = { Icon(painterResource(YouTubelyIcons.Tune), contentDescription = null) },
            trailingContent = { Icon(painterResource(YouTubelyIcons.ChevronRight), contentDescription = null) },
        ) { Text(stringResource(R.string.watch_equalizer)) }
    }
}

@Composable
private fun SheetGap() {
    Row(Modifier.padding(top = 8.dp)) {}
}

@Composable
internal fun ToggleRow(
    title: Int,
    icon: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        shapes = groupedListItemShapes(index, count),
        colors = groupedListItemColors(),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) { Text(stringResource(title)) }
}

@Composable
private fun qualityValue(
    player: Player,
    pinned: Int?,
): String {
    if (pinned != null) return "${pinned}p"
    val now = player.videoSize.height.takeIf { it > 0 }
    val auto = stringResource(R.string.watch_quality_auto_value)
    return if (now == null) auto else "$auto · ${now}p"
}

@Composable
private fun sleepValue(timer: SleepTimer): String =
    when (timer) {
        SleepTimer.Off -> stringResource(R.string.watch_sleep_off)
        SleepTimer.AtEndOfVideo -> stringResource(R.string.watch_sleep_end_chip)
        is SleepTimer.In -> stringResource(R.string.watch_sleep_minutes, timer.minutesLeft())
    }

@Composable
private fun loopValue(loop: AbLoop): String {
    val start = loop.startMs
    val end = loop.endMs
    return when {
        start == null -> stringResource(R.string.watch_loop_off)
        end == null -> formatDuration(Duration.ofMillis(start)) + " –"
        else -> formatDuration(Duration.ofMillis(start)) + " – " + formatDuration(Duration.ofMillis(end))
    }
}

/** Automatic quality with the reason for its ceiling, then every resolution of the video. */
@Composable
private fun QualityPage(
    player: Player,
    pinned: Int?,
    cap: QualityCap?,
    onPick: (Int?) -> Unit,
) {
    val options = remember(player) { player.qualityOptions() }
    val now = player.videoSize.height.takeIf { it > 0 }
    val reason = cap?.let { reasonText(it) } ?: stringResource(R.string.watch_reason_unlimited)
    val autoDetail =
        if (now != null &&
            pinned == null
        ) {
            stringResource(R.string.watch_quality_now, now) + " · " + reason
        } else {
            reason
        }
    val count = options.size + 1
    ChoiceItem(stringResource(R.string.watch_quality_auto), autoDetail, pinned == null, 0, count) { onPick(null) }
    options.forEachIndexed { index, option ->
        ChoiceItem(option.label(), option.bitrate?.let { bitrateText(it) }, pinned == option.height, index + 1, count) {
            onPick(option.height)
        }
    }
}

@Composable
private fun reasonText(cap: QualityCap): String {
    val height = cap.maxHeight ?: 0
    return when (cap.reason) {
        QualityReason.Unlimited -> stringResource(R.string.watch_reason_unlimited)
        QualityReason.MeteredLimit -> stringResource(R.string.watch_reason_metered, height)
        QualityReason.UnmeteredLimit -> stringResource(R.string.watch_reason_unmetered, height)
        QualityReason.DataSaver -> stringResource(R.string.watch_reason_saver, height)
        QualityReason.PowerSave -> stringResource(R.string.watch_reason_power, height)
        QualityReason.MonthlyLimit -> stringResource(R.string.watch_reason_monthly, height)
        QualityReason.AudioOnly -> stringResource(R.string.watch_reason_audio)
        QualityReason.Pinned -> stringResource(R.string.watch_reason_pinned)
    }
}

/** Subtitle languages of the video: off, or one of them. */
@Composable
private fun SubtitlePage(
    player: Player,
    enabled: Boolean,
    actions: PlayerSheetActions,
) {
    val options = remember(player) { player.subtitleOptions() }
    val selected = options.firstOrNull { it.selected }?.language.takeIf { enabled }
    val count = options.size + 1
    ChoiceItem(
        stringResource(R.string.watch_subtitles_off),
        null,
        selected == null,
        0,
        count,
    ) { actions.onSubtitles(null) }
    options.forEachIndexed { index, option ->
        ChoiceItem(
            option.label,
            null,
            selected == option.language,
            index + 1,
            count,
        ) { actions.onSubtitles(option.language) }
    }
}

/** One of several choices, with a radio button. */
@Composable
private fun ChoiceItem(
    title: String,
    detail: String?,
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = groupedListItemShapes(index, count),
        colors = groupedListItemColors(),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        supportingContent = detail?.let { { Text(it) } },
    ) { Text(title, style = MaterialTheme.typography.run { if (selected) bodyLargeEmphasized else bodyLarge }) }
}

private fun QualityOption.label(): String =
    buildString {
        append(height).append('p')
        if (fps > STANDARD_FPS) append(fps)
    }

@Composable
private fun bitrateText(bitrate: Int): String =
    stringResource(R.string.watch_bitrate, String.format(currentLocale(), "%.1f", bitrate / BITS_PER_MEGABIT))
