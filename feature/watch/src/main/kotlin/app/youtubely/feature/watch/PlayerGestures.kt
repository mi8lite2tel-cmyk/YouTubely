// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.watch

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.media3.common.C
import androidx.media3.common.Player
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val SIDE_ZONE = 0.4f
private const val MILLIS_PER_SECOND = 1_000L
private const val SEEK_FEEDBACK_MS = 750L
private const val LEVEL_FEEDBACK_MS = 900L
private const val BOOST_SPEED = 2f
private const val DRAG_RANGE = 0.8f
private const val ZOOM_IN = 1.12f
private const val ZOOM_OUT = 0.9f
private const val MAX_BRIGHTNESS = 255f
private val EdgeZone = 48.dp

internal enum class SeekSide { Back, Forward }

internal enum class Level { Brightness, Volume }

/** A burst of double taps on one side; [seconds] keeps growing while the user keeps tapping. */
internal data class SeekBurst(
    val side: SeekSide,
    val seconds: Int,
    val tick: Int,
)

/** Transient feedback for the gestures, drawn on top of the video by [GestureFeedbackOverlay]. */
@Stable
internal class PlayerGestureState {
    var seek by mutableStateOf<SeekBurst?>(null)
    var boosting by mutableStateOf(false)
    var level by mutableStateOf<Level?>(null)
    var levelValue by mutableFloatStateOf(0f)
    var zoomed by mutableStateOf(false)
    var zoomHint by mutableStateOf<Boolean?>(null)
    internal var speedBeforeBoost = 1f
    internal var levelTick by mutableIntStateOf(0)
}

@Composable
internal fun rememberPlayerGestureState(): PlayerGestureState = remember { PlayerGestureState() }

/**
 * YouTube-style gestures:
 * - double tap on the left / right side seeks back / forward by [seekStepSeconds]; further taps
 *   on the same side keep adding to the jump; a double tap in the middle plays or pauses;
 * - touch and hold plays at 2× until released;
 * - in fullscreen, a vertical swipe on the left changes brightness, on the right the volume, and a
 *   pinch switches between fitting the video and filling the screen.
 */
internal fun Modifier.playerGestures(
    player: Player,
    state: PlayerGestureState,
    seekStepSeconds: Int,
    fullscreen: Boolean,
    environment: GestureEnvironment,
): Modifier {
    val taps = tapGestures(player, state, seekStepSeconds, environment.haptics) { environment.onTap.value() }
    if (!fullscreen) return taps
    return taps
        .levelDrags(state, environment.activity, environment.audio)
        .pinchToZoom(state, environment.haptics)
}

/** What the gestures need from the composition: haptics, the window and the audio service. */
@Stable
internal class GestureEnvironment(
    val haptics: HapticFeedback,
    val activity: Activity?,
    val audio: AudioManager?,
    val onTap: State<() -> Unit>,
)

@Composable
internal fun rememberGestureEnvironment(
    state: PlayerGestureState,
    fullscreen: Boolean,
    onTap: () -> Unit,
): GestureEnvironment {
    val haptics = LocalHapticFeedback.current
    val activity = LocalActivity.current
    val context = LocalContext.current
    val currentOnTap = rememberUpdatedState(onTap)
    ResetBrightnessOnExit(activity, fullscreen)
    LaunchedEffect(fullscreen) { if (!fullscreen) state.zoomed = false }
    return remember(haptics, activity, context) {
        GestureEnvironment(haptics, activity, context.getSystemService<AudioManager>(), currentOnTap)
    }
}

/** Double tap to seek or play/pause, touch and hold for 2×. */
private fun Modifier.tapGestures(
    player: Player,
    state: PlayerGestureState,
    seekStepSeconds: Int,
    haptics: HapticFeedback,
    onTap: () -> Unit,
): Modifier =
    pointerInput(player, seekStepSeconds) {
        fun sideOf(offset: Offset): SeekSide? = seekSideOf(offset.x, size.width)

        fun seek(side: SeekSide) = player.seekInBurst(state, side, seekStepSeconds)

        detectTapGestures(
            onTap = { offset ->
                // While a burst is on screen, single taps on the same side keep seeking.
                val side = sideOf(offset)
                if (side != null && state.seek?.side == side) seek(side) else onTap()
            },
            onDoubleTap = { offset ->
                val side = sideOf(offset)
                when {
                    side != null -> seek(side)
                    player.isPlaying -> player.pause()
                    else -> player.play()
                }
            },
            onLongPress = {
                if (player.isPlaying) {
                    state.speedBeforeBoost = player.playbackParameters.speed
                    player.setPlaybackSpeed(maxOf(BOOST_SPEED, state.speedBeforeBoost))
                    state.boosting = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onPress = {
                tryAwaitRelease()
                if (state.boosting) {
                    player.setPlaybackSpeed(state.speedBeforeBoost)
                    state.boosting = false
                }
            },
        )
    }

private fun seekSideOf(
    x: Float,
    width: Int,
): SeekSide? =
    when {
        x < width * SIDE_ZONE -> SeekSide.Back
        x > width * (1 - SIDE_ZONE) -> SeekSide.Forward
        else -> null
    }

/** Seeks one step and grows the on-screen burst for consecutive taps on the same side. */
private fun Player.seekInBurst(
    state: PlayerGestureState,
    side: SeekSide,
    seconds: Int,
) {
    val step = seconds * MILLIS_PER_SECOND * if (side == SeekSide.Back) -1 else 1
    val duration = duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
    seekTo((currentPosition + step).coerceIn(0, duration))
    val previous = state.seek?.takeIf { it.side == side }
    state.seek = SeekBurst(side, (previous?.seconds ?: 0) + seconds, (previous?.tick ?: 0) + 1)
}

/** Vertical swipes: brightness on the left half, volume on the right. */
private fun Modifier.levelDrags(
    state: PlayerGestureState,
    activity: Activity?,
    audio: AudioManager?,
): Modifier =
    pointerInput(activity, audio) {
        val edge = EdgeZone.toPx()
        var target: Level? = null
        detectVerticalDragGestures(
            onDragStart = { offset ->
                // Edges belong to the system gestures (notification shade, home).
                val inEdge = offset.y < edge || offset.y > size.height - edge
                target =
                    when {
                        inEdge -> null
                        offset.x < size.width / 2 -> activity?.let { Level.Brightness }
                        else -> audio?.let { Level.Volume }
                    }
                target?.let {
                    state.level = it
                    state.levelValue = if (it == Level.Brightness) activity!!.brightness() else audio!!.volume()
                }
            },
            onDragEnd = { target = null },
            onDragCancel = { target = null },
            onVerticalDrag = { change, dragAmount ->
                target?.let { level ->
                    change.consume()
                    val value = (state.levelValue - dragAmount / (size.height * DRAG_RANGE)).coerceIn(0f, 1f)
                    state.levelValue = value
                    state.levelTick++
                    if (level == Level.Brightness) activity?.setBrightness(value) else audio?.setVolume(value)
                }
            },
        )
    }

/** Pinch out fills the screen (cropping the edges), pinch in fits the whole picture again. */
private fun Modifier.pinchToZoom(
    state: PlayerGestureState,
    haptics: HapticFeedback,
): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var scale = 1f
            var decided = false
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) {
                    scale *= event.calculateZoom()
                    val zoom =
                        if (scale > ZOOM_IN) {
                            true
                        } else if (scale < ZOOM_OUT) {
                            false
                        } else {
                            null
                        }
                    if (zoom != null) {
                        event.changes.forEach { it.consume() }
                        decided = true
                        if (state.zoomed != zoom) {
                            state.zoomed = zoom
                            state.zoomHint = zoom
                            haptics.tick()
                        }
                    }
                }
            } while (!decided && event.changes.any { it.pressed })
        }
    }

private fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.SegmentTick)

/** The brightness set by swiping applies only while the player is in fullscreen. */
@Composable
private fun ResetBrightnessOnExit(
    activity: Activity?,
    fullscreen: Boolean,
) {
    DisposableEffect(activity, fullscreen) {
        onDispose {
            activity?.window?.let { window ->
                window.attributes =
                    window.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
            }
        }
    }
}

private fun Activity.brightness(): Float {
    val override = window.attributes.screenBrightness
    if (override >= 0) return override
    val system = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, MAX_BRIGHTNESS.toInt())
    return system / MAX_BRIGHTNESS
}

private fun Activity.setBrightness(value: Float) {
    // Exactly 0 would turn the backlight off on some devices.
    window.attributes = window.attributes.apply { screenBrightness = value.coerceAtLeast(MIN_BRIGHTNESS) }
}

private const val MIN_BRIGHTNESS = 0.01f

private fun AudioManager.volume(): Float =
    getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / getStreamMaxVolume(AudioManager.STREAM_MUSIC)

private fun AudioManager.setVolume(value: Float) {
    // No system volume panel: the player shows its own indicator.
    setStreamVolume(AudioManager.STREAM_MUSIC, (value * getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0)
}

/** Seek bursts on the sides, the 2× pill, brightness/volume level and the zoom hint. */
@Composable
internal fun BoxScope.GestureFeedbackOverlay(state: PlayerGestureState) {
    val motion = MaterialTheme.motionScheme
    val burst = state.seek
    LaunchedEffect(burst) {
        if (burst != null) {
            delay(SEEK_FEEDBACK_MS)
            state.seek = null
        }
    }
    LaunchedEffect(state.level, state.levelTick) {
        if (state.level != null) {
            delay(LEVEL_FEEDBACK_MS)
            state.level = null
        }
    }
    LaunchedEffect(state.zoomHint) {
        if (state.zoomHint != null) {
            delay(LEVEL_FEEDBACK_MS)
            state.zoomHint = null
        }
    }

    SeekSide.entries.forEach { side ->
        val visible = burst?.side == side
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(motion.fastEffectsSpec()),
            exit = fadeOut(motion.defaultEffectsSpec()),
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(SIDE_ZONE)
                    .align(if (side == SeekSide.Back) Alignment.CenterStart else Alignment.CenterEnd),
        ) {
            val shape =
                if (side == SeekSide.Back) {
                    RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                } else {
                    RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                }
            Box(
                Modifier.clip(shape).background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(
                            if (side ==
                                SeekSide.Back
                            ) {
                                YouTubelyIcons.FastRewind
                            } else {
                                YouTubelyIcons.FastForward
                            },
                        ),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        stringResource(R.string.watch_seconds, burst?.seconds ?: 0),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = Color.White,
                    )
                }
            }
        }
    }

    Pill(visible = state.boosting, modifier = Modifier.align(Alignment.TopCenter)) {
        Text("2×", style = MaterialTheme.typography.labelLargeEmphasized)
        Icon(painterResource(YouTubelyIcons.FastForward), contentDescription = null, modifier = Modifier.size(18.dp))
    }
    Pill(visible = state.zoomHint != null, modifier = Modifier.align(Alignment.TopCenter)) {
        Icon(painterResource(YouTubelyIcons.AspectRatio), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            stringResource(if (state.zoomHint == true) R.string.watch_zoom_fill else R.string.watch_zoom_fit),
            style = MaterialTheme.typography.labelLargeEmphasized,
        )
    }
    Pill(visible = state.level != null, modifier = Modifier.align(Alignment.TopCenter)) {
        Icon(
            painterResource(if (state.level == Level.Volume) YouTubelyIcons.Volume else YouTubelyIcons.Brightness),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        LinearWavyProgressIndicator(
            progress = { state.levelValue },
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.width(120.dp).height(12.dp),
        )
    }
}

@Composable
private fun Pill(
    visible: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec()),
        exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec()),
        modifier = modifier.padding(top = 16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { content() }
        }
    }
}
