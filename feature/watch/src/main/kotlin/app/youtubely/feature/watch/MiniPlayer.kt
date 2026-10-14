// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.watch

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.bestFor
import app.youtubely.core.player.NowPlaying
import app.youtubely.core.player.PlaybackManager
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

private const val PROGRESS_TICK_MS = 500L
private const val THUMBNAIL_WIDTH = 320

@HiltViewModel
class MiniPlayerViewModel
    @Inject
    constructor(
        private val playback: PlaybackManager,
    ) : ViewModel() {
        val nowPlaying: StateFlow<NowPlaying?> get() = playback.nowPlaying
        val player: Player get() = playback.player
        val backgroundPlayback: Boolean get() = playback.settings.value.backgroundPlayback

        fun close() = playback.stop()
    }

/**
 * The video that keeps playing after its screen was left: a bar with the thumbnail, title,
 * play / pause and close. A tap opens the video again, without loading it anew.
 */
@Composable
fun MiniPlayer(
    visible: Boolean,
    onOpen: (NowPlaying) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiniPlayerViewModel = hiltViewModel(),
) {
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val motion = MaterialTheme.motionScheme
    val current = nowPlaying
    // Without background playback, leaving the app pauses like the video screen does.
    val activity = LocalActivity.current
    LifecycleStartEffect(viewModel) {
        onStopOrDispose {
            if (shouldPauseForBackground(activity, viewModel.backgroundPlayback)) viewModel.player.pause()
        }
    }
    AnimatedVisibility(
        visible = visible && current != null,
        enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.defaultSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier,
    ) {
        if (current != null) MiniPlayerBar(current, viewModel.player, { onOpen(current) }, viewModel::close)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MiniPlayerBar(
    nowPlaying: NowPlaying,
    player: Player,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    val playPause = rememberPlayPauseButtonState(player)
    val progress by produceState(0f, player) {
        while (true) {
            val duration = player.duration
            value = if (duration > 0) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(PROGRESS_TICK_MS)
        }
    }
    val item = nowPlaying.item
    val openLabel = stringResource(R.string.watch_mini_open)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column {
            Row(
                Modifier
                    .clickable(onClick = onOpen)
                    .semantics { contentDescription = openLabel }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.height(54.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium)) {
                    AsyncImage(
                        model = item.thumbnails.bestFor(THUMBNAIL_WIDTH)?.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.channel?.let {
                        Text(
                            it.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                FilledTonalIconButton(
                    onClick = playPause::onClick,
                    enabled = playPause.isEnabled,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painterResource(if (playPause.showPlay) YouTubelyIcons.Play else YouTubelyIcons.Pause),
                        stringResource(if (playPause.showPlay) R.string.watch_play else R.string.watch_pause),
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(painterResource(YouTubelyIcons.Close), stringResource(R.string.watch_mini_close))
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp)
                        .height(3.dp),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}
