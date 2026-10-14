// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package app.youtubely.feature.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import app.youtubely.core.model.PlaybackSettings
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

private const val CONTROLS_TIMEOUT_MS = 3_000L
private val SUBTITLES_LIFTED = 96.dp
private val SUBTITLES_LOW = 12.dp
private val SKIP_NOTICE_BOTTOM = 112.dp

/**
 * Video surface with expressive controls: tap to show/hide, auto-hide while playing, shape-
 * morphing play button, ±10 s, a Material 3 progress slider and audio / quality / fullscreen
 * actions, plus the gestures from [playerGestures]. In audio-only mode the artwork
 * replaces the picture.
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
    seekStepSeconds: Int = PlaybackSettings.DEFAULT_SEEK_STEP,
    audioOnly: Boolean = false,
    onToggleAudioOnly: (() -> Unit)? = null,
    artworkUrl: String? = null,
    extras: PlayerExtras = PlayerExtras(),
) {
    var controlsVisible by remember { mutableStateOf(true) }
    val gestures = rememberPlayerGestureState()
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
                .clipToBounds()
                .onSizeChanged { extras.onViewport(it.width, it.height) }
                .playerKeys(player, onToggleFullscreen) { controlsVisible = true }
                .playerGestures(
                    player = player,
                    state = gestures,
                    seekStepSeconds = seekStepSeconds,
                    fullscreen = fullscreen,
                    environment =
                        rememberGestureEnvironment(gestures, fullscreen) { controlsVisible = !controlsVisible },
                ),
    ) {
        if (audioOnly) {
            AudioArtwork(artworkUrl, Modifier.fillMaxSize())
        } else {
            ContentFrame(
                player = player,
                contentScale = if (gestures.zoomed) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val problem = rememberPlayerProblem(player)
        if (problem != null) {
            PlayerErrorOverlay(
                problem = problem,
                onRetry = { player.prepare() },
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (buffering) {
            LoadingIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(64.dp))
        }
        GestureFeedbackOverlay(gestures)
        SubtitleOverlay(
            player = player,
            settings = extras.settings,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = if (controlsVisible) SUBTITLES_LIFTED else SUBTITLES_LOW),
        )
        extras.skipNotice?.let { notice ->
            SkipNoticeChip(
                notice = notice,
                onUndo = extras.onUndoSkip,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = SKIP_NOTICE_BOTTOM),
            )
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
                    audioOnly = audioOnly,
                    onToggleAudioOnly = onToggleAudioOnly,
                    extras = extras,
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
    audioOnly: Boolean,
    onToggleAudioOnly: (() -> Unit)?,
    extras: PlayerExtras,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val (position, duration) = rememberPlaybackClock(player)
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PositionAndDurationText(player, color = Color.White, scope = scope)
            extras.chapters.titleAt(position)?.let { title ->
                Text(
                    "· $title",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (onToggleAudioOnly != null) {
                IconToggleButton(
                    checked = audioOnly,
                    onCheckedChange = { onToggleAudioOnly() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    colors =
                        IconButtonDefaults.iconToggleButtonColors(
                            contentColor = Color.White,
                            checkedContentColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    Icon(
                        painterResource(YouTubelyIcons.Headphones),
                        stringResource(if (audioOnly) R.string.watch_audio_only_off else R.string.watch_audio_only_on),
                    )
                }
            }
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
        SeekMarkers(duration, extras.chapters, extras.segments, Modifier.padding(horizontal = 8.dp))
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

/** Audio-only mode: the blurred thumbnail fills the frame and a sharp copy sits in the middle. */
@Composable
private fun AudioArtwork(
    url: String?,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(24.dp).alpha(0.6f),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth(0.4f)
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.large),
            )
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Icon(
                        painterResource(YouTubelyIcons.Headphones),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(R.string.watch_audio_only),
                        style = MaterialTheme.typography.labelMediumEmphasized,
                    )
                }
            }
        }
    }
}
