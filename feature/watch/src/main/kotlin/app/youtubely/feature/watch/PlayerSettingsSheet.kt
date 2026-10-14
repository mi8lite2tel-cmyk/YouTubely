// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.player.AbLoop
import app.youtubely.core.player.QualityOption
import app.youtubely.core.player.SleepTimer
import app.youtubely.core.player.qualityOptions
import app.youtubely.core.player.subtitleOptions
import app.youtubely.core.ui.currentLocale
import java.util.Locale
import kotlin.math.roundToInt

private val SpeedPresets = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private const val MIN_SPEED = 0.25f
private const val MAX_SPEED = 4f
private const val STANDARD_FPS = 30
private const val SPEED_STEP = 0.05f
private const val SPEED_STEPS = 74 // 0.05 increments between 0.25 and 4
private const val BITS_PER_MEGABIT = 1_000_000.0

/** Quality (with bitrate), playback speed and repeat in one expressive sheet. */
@Composable
internal fun PlayerSettingsSheet(
    player: Player,
    pinnedHeight: Int?,
    networkLimit: Int?,
    settings: PlaybackSettings,
    sleepTimer: SleepTimer,
    abLoop: AbLoop,
    actions: PlayerSheetActions,
    onQuality: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = remember(player) { player.qualityOptions() }
    val subtitles = remember(player) { player.subtitleOptions() }
    var selected by remember { mutableStateOf(pinnedHeight) }
    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    var repeat by remember { mutableIntStateOf(player.repeatMode) }
    val sliderState = remember { SliderState(value = speed, steps = SPEED_STEPS, trackRange = MIN_SPEED..MAX_SPEED) }

    fun setSpeed(value: Float) {
        speed = value
        sliderState.value = value
        player.playbackParameters = PlaybackParameters(value)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            SectionTitle(stringResource(R.string.watch_speed))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                SpeedPresets.forEachIndexed { index, preset ->
                    ToggleButton(
                        checked = speed == preset,
                        onCheckedChange = { setSpeed(preset) },
                        shapes =
                            when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                SpeedPresets.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(formatSpeed(preset), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Slider(
                    state = sliderState,
                    onValueChange = { setSpeed((it / SPEED_STEP).roundToInt() * SPEED_STEP) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatSpeed(speed),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                checked = repeat == Player.REPEAT_MODE_ONE,
                onCheckedChange = {
                    repeat = if (it) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    player.repeatMode = repeat
                },
                leadingContent = { Icon(painterResource(YouTubelyIcons.Refresh), contentDescription = null) },
                trailingContent = { Switch(checked = repeat == Player.REPEAT_MODE_ONE, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) { Text(stringResource(R.string.watch_repeat)) }

            SectionTitle(stringResource(R.string.watch_loop))
            LoopSection(abLoop, actions)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.watch_sleep))
            SleepTimerSection(sleepTimer, actions)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.watch_audio))
            ToggleRow(
                R.string.watch_skip_silence,
                YouTubelyIcons.FastForward,
                settings.skipSilence,
                actions.onSkipSilence,
            )
            ToggleRow(
                R.string.watch_normalize,
                YouTubelyIcons.Volume,
                settings.normalizeVolume,
                actions.onNormalizeVolume,
                R.string.watch_normalize_summary,
            )
            ToggleRow(R.string.watch_autoplay, YouTubelyIcons.SkipNext, settings.autoplayNext, actions.onAutoplay)
            actions.onEqualizer?.let { open ->
                ListItem(
                    onClick = open,
                    leadingContent = { Icon(painterResource(YouTubelyIcons.Tune), contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) { Text(stringResource(R.string.watch_equalizer)) }
            }

            if (subtitles.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle(stringResource(R.string.watch_subtitles))
                SubtitleSection(subtitles, settings.subtitlesEnabled, actions)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.watch_quality))
            QualityRow(
                title = stringResource(R.string.watch_quality_auto),
                subtitle =
                    networkLimit?.let { stringResource(R.string.watch_quality_auto_limited, it) }
                        ?: stringResource(R.string.watch_quality_auto_hint),
                selected = selected == null,
            ) {
                selected = null
                onQuality(null)
            }
            options.forEach { option ->
                QualityRow(
                    title = option.label(),
                    subtitle = option.bitrate?.let { bitrateText(it) },
                    selected = selected == option.height,
                ) {
                    selected = option.height
                    onQuality(option.height)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun QualityRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Text(title, style = MaterialTheme.typography.run { if (selected) bodyLargeEmphasized else bodyLarge })
    }
}

private fun QualityOption.label(): String =
    buildString {
        append(height).append('p')
        if (fps > STANDARD_FPS) append(fps)
    }

@Composable
private fun bitrateText(bitrate: Int): String =
    stringResource(R.string.watch_bitrate, String.format(currentLocale(), "%.1f", bitrate / BITS_PER_MEGABIT))

private fun formatSpeed(speed: Float): String {
    val text =
        if (speed == speed.toInt().toFloat()) {
            speed.toInt().toString()
        } else {
            "%.2f".format(Locale.ROOT, speed).trimEnd('0')
        }
    return "$text×"
}
