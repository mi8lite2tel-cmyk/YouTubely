// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package app.youtubely.feature.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.material3.indicator.PositionAndDurationText
import androidx.media3.ui.compose.material3.indicator.ProgressSlider
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberSeekBackButtonState
import androidx.media3.ui.compose.state.rememberSeekForwardButtonState
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import kotlinx.coroutines.delay

private const val CONTROLS_TIMEOUT_MS = 3_000L

/**
 * Video surface with expressive controls: tap to show/hide, auto-hide while playing, shape-
 * morphing play button, ±10 s, a Material 3 progress slider and quality / fullscreen actions.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayer(
    player: Player,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    showFullscreen: Boolean = true,
    /** Audio-only playback shows artwork behind the controls instead of a black frame. */
    transparentBackground: Boolean = false,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    val playPause = rememberPlayPauseButtonState(player)
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var interactions by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    buffering = playbackState == Player.STATE_BUFFERING
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(controlsVisible, playPause.showPlay, interactions) {
        if (controlsVisible && !playPause.showPlay) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    Box(
        modifier =
            modifier
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    controlsVisible = !controlsVisible
                },
    ) {
        if (!transparentBackground) ContentFrame(player = player, modifier = Modifier.fillMaxSize())

        if (buffering) {
            LoadingIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(64.dp))
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                CenterControls(player, Modifier.align(Alignment.Center)) { interactions++ }
                BottomControls(
                    player = player,
                    fullscreen = fullscreen,
                    showFullscreen = showFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun CenterControls(
    player: Player,
    modifier: Modifier,
    onInteraction: () -> Unit,
) {
    val playPause = rememberPlayPauseButtonState(player)
    val back = rememberSeekBackButtonState(player)
    val forward = rememberSeekForwardButtonState(player)
    val tonal =
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color.White.copy(alpha = 0.2f),
            contentColor = Color.White,
        )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = {
                back.onClick()
                onInteraction()
            },
            enabled = back.isEnabled,
            shapes = IconButtonDefaults.shapes(),
            colors = tonal,
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
        ) {
            Icon(painterResource(YouTubelyIcons.Replay10), stringResource(R.string.watch_seek_back))
        }
        FilledIconButton(
            onClick = {
                playPause.onClick()
                onInteraction()
            },
            enabled = playPause.isEnabled,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
        ) {
            Icon(
                painter = painterResource(if (playPause.showPlay) YouTubelyIcons.Play else YouTubelyIcons.Pause),
                contentDescription =
                    stringResource(
                        if (playPause.showPlay) R.string.watch_play else R.string.watch_pause,
                    ),
                modifier = Modifier.size(IconButtonDefaults.largeIconSize),
            )
        }
        FilledTonalIconButton(
            onClick = {
                forward.onClick()
                onInteraction()
            },
            enabled = forward.isEnabled,
            shapes = IconButtonDefaults.shapes(),
            colors = tonal,
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
        ) {
            Icon(painterResource(YouTubelyIcons.Forward10), stringResource(R.string.watch_seek_forward))
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class, ExperimentalApi::class)
@Composable
private fun BottomControls(
    player: Player,
    fullscreen: Boolean,
    showFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PositionAndDurationText(player, color = Color.White, scope = scope)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(YouTubelyIcons.Tune), stringResource(R.string.watch_settings), tint = Color.White)
            }
            if (showFullscreen) {
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        painterResource(if (fullscreen) YouTubelyIcons.FullscreenExit else YouTubelyIcons.Fullscreen),
                        stringResource(if (fullscreen) R.string.watch_exit_fullscreen else R.string.watch_fullscreen),
                        tint = Color.White,
                    )
                }
            }
        }
        ProgressSlider(
            player = player,
            modifier = Modifier.fillMaxWidth(),
            onValueChangeFinished = null,
            scope = scope,
            colors =
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            interactionSource = remember { MutableInteractionSource() },
        )
    }
}
