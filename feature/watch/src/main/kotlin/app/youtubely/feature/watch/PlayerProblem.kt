// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import app.youtubely.core.designsystem.icon.YouTubelyIcons

/** What went wrong, in terms the viewer can act on. */
internal enum class PlayerProblem(
    val message: Int,
) {
    /** The device cannot decode this file or the file is damaged. */
    Unplayable(R.string.watch_error_unplayable),
    Network(R.string.watch_error_network),
    Other(R.string.watch_error_other),
}

internal fun PlaybackException.toProblem(): PlayerProblem =
    when (errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        -> PlayerProblem.Unplayable

        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        -> PlayerProblem.Network

        else -> PlayerProblem.Other
    }

/** The player's current problem, `null` while everything works; clears itself on the next try. */
@Composable
internal fun rememberPlayerProblem(player: Player): PlayerProblem? {
    var problem by remember(player) { mutableStateOf(player.playerError?.toProblem()) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    problem = error.toProblem()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) problem = null
                }

                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int,
                ) {
                    problem = null
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return problem
}

@Composable
internal fun PlayerErrorOverlay(
    problem: PlayerProblem,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(YouTubelyIcons.Error),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp),
        )
        Text(
            stringResource(problem.message),
            style = MaterialTheme.typography.bodyMediumEmphasized,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        if (problem != PlayerProblem.Unplayable) {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.watch_error_retry))
            }
        }
    }
}
