// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.watch

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.youtubely.core.designsystem.component.ExpressiveFilterChip
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.player.AbLoop
import app.youtubely.core.player.PlaybackManager
import app.youtubely.core.player.SleepTimer
import app.youtubely.core.player.SubtitleOption
import app.youtubely.core.ui.formatDuration
import kotlinx.coroutines.delay
import java.time.Duration

private val SleepChoices = listOf(15, 30, 45, 60)
private const val COUNTDOWN_TICK_MS = 15_000L
private const val MILLIS_PER_MINUTE = 60_000L

/** What the sheet can do to the player; the screen wires these to the view model. */
@Suppress("LongParameterList") // A bundle of callbacks, one per button.
internal class PlayerSheetActions(
    val onSkipSilence: (Boolean) -> Unit,
    val onNormalizeVolume: (Boolean) -> Unit,
    val onAutoplay: (Boolean) -> Unit,
    val onSleepMinutes: (Int) -> Unit,
    val onSleepAtEnd: () -> Unit,
    val onSleepCancel: () -> Unit,
    val onLoopStart: () -> Unit,
    val onLoopEnd: () -> Unit,
    val onLoopClear: () -> Unit,
    /** `null` turns subtitles off, otherwise the language to show. */
    val onSubtitles: (String?) -> Unit,
    val onEqualizer: (() -> Unit)?,
    /** Sets the playback speed (and remembers it for the channel). */
    val onSpeed: (Float) -> Unit,
)

@Composable
internal fun SleepTimerSection(
    timer: SleepTimer,
    actions: PlayerSheetActions,
) {
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val status =
            when (timer) {
                SleepTimer.Off -> {
                    stringResource(R.string.watch_sleep_off)
                }

                SleepTimer.AtEndOfVideo -> {
                    stringResource(R.string.watch_sleep_at_end)
                }

                is SleepTimer.In -> {
                    val minutes by produceState(timer.minutesLeft(), timer) {
                        while (true) {
                            value = timer.minutesLeft()
                            delay(COUNTDOWN_TICK_MS)
                        }
                    }
                    stringResource(R.string.watch_sleep_in, minutes)
                }
            }
        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SleepChoices.forEach { minutes ->
                ExpressiveFilterChip(
                    selected = timer is SleepTimer.In && timer.minutesLeft() in (minutes - 1)..minutes,
                    onClick = { actions.onSleepMinutes(minutes) },
                    label = stringResource(R.string.watch_sleep_minutes, minutes),
                )
            }
            ExpressiveFilterChip(
                selected = timer == SleepTimer.AtEndOfVideo,
                onClick = actions.onSleepAtEnd,
                label = stringResource(R.string.watch_sleep_end_chip),
            )
            if (timer != SleepTimer.Off) {
                ExpressiveFilterChip(
                    selected = false,
                    onClick = actions.onSleepCancel,
                    label = stringResource(R.string.watch_sleep_cancel),
                )
            }
        }
    }
}

internal fun SleepTimer.In.minutesLeft(): Int =
    ((endsAtElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0) / MILLIS_PER_MINUTE).toInt() + 1

/** A–B repeat: mark a start and an end, and the stretch in between repeats. */
@Composable
internal fun LoopSection(
    loop: AbLoop,
    actions: PlayerSheetActions,
) {
    val start = loop.startMs
    val end = loop.endMs
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text =
                when {
                    start == null -> {
                        stringResource(R.string.watch_loop_hint)
                    }

                    end == null -> {
                        stringResource(R.string.watch_loop_start_set, formatDuration(Duration.ofMillis(start)))
                    }

                    else -> {
                        stringResource(
                            R.string.watch_loop_active,
                            formatDuration(Duration.ofMillis(start)),
                            formatDuration(Duration.ofMillis(end)),
                        )
                    }
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (start == null || loop.isActive) {
                FilledTonalButton(onClick = actions.onLoopStart) { Text(stringResource(R.string.watch_loop_set_a)) }
            } else {
                FilledTonalButton(onClick = actions.onLoopEnd) { Text(stringResource(R.string.watch_loop_set_b)) }
            }
            if (start != null) {
                OutlinedButton(onClick = actions.onLoopClear) { Text(stringResource(R.string.watch_loop_clear)) }
            }
        }
    }
}

/** The sheet's actions wired to the player; [context] is used to find the system equalizer. */
internal fun PlaybackManager.sheetActions(context: Context): PlayerSheetActions =
    PlayerSheetActions(
        onSkipSilence = { enabled -> updateSettings { setSkipSilence(enabled) } },
        onNormalizeVolume = { enabled -> updateSettings { setNormalizeVolume(enabled) } },
        onAutoplay = { enabled -> updateSettings { setAutoplayNext(enabled) } },
        onSleepMinutes = ::startSleepTimer,
        onSleepAtEnd = ::sleepAtEndOfVideo,
        onSleepCancel = ::cancelSleepTimer,
        onLoopStart = ::markLoopStart,
        onLoopEnd = ::markLoopEnd,
        onLoopClear = ::clearLoop,
        onSubtitles = { language ->
            updateSettings {
                setSubtitlesEnabled(language != null)
                if (language != null) setSubtitleLanguage(language)
            }
        },
        onEqualizer = equalizerLauncher(context, audioSessionId),
        onSpeed = ::setSpeed,
    )

/** Opens the system equalizer for this player, or `null` when the device has none. */
private fun equalizerLauncher(
    context: Context,
    audioSessionId: Int,
): (() -> Unit)? {
    val intent =
        Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE)
    return if (intent.resolveActivity(context.packageManager) != null) {
        { context.startActivity(intent) }
    } else {
        null
    }
}
