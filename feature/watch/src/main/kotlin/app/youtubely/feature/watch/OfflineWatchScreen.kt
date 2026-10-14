// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.watch

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import app.youtubely.core.data.HistoryRepository
import app.youtubely.core.download.DownloadManager
import app.youtubely.core.model.Download
import app.youtubely.core.model.DownloadFormat
import app.youtubely.core.model.DownloadState
import app.youtubely.core.model.Image
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.StreamType
import app.youtubely.core.player.PlaybackManager
import app.youtubely.core.ui.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val VIDEO_ASPECT = 16f / 9f

/** Plays a finished download from disk on the shared player: works without a connection. */
@HiltViewModel(assistedFactory = OfflineWatchViewModel.Factory::class)
class OfflineWatchViewModel
    @AssistedInject
    constructor(
        @Assisted downloadId: String,
        downloadManager: DownloadManager,
        private val playback: PlaybackManager,
        private val history: HistoryRepository,
    ) : ViewModel() {
        val player: Player get() = playback.player
        val settings: StateFlow<PlaybackSettings> get() = playback.settings

        val download: StateFlow<Download?> =
            downloadManager
                .download(downloadId)
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private var savedPositionMs: Long? = null

        private val isCurrent: Boolean
            get() = download.value?.let { playback.currentVideo.value == it.videoId } == true

        fun onVisible() {
            val item = download.value?.takeIf { it.state == DownloadState.Completed }
            val path = item?.filePath
            if (path != null && !isCurrent) {
                viewModelScope.launch {
                    val position = savedPositionMs ?: history.resumePosition(item.videoId)
                    if (!isCurrent) playback.playLocal(item.toStreamItem(), path, position)
                }
            }
        }

        fun onHidden() {
            if (isCurrent) savedPositionMs = player.currentPosition
        }

        fun setQuality(height: Int?) = playback.setQuality(height)

        fun setAudioOnly(enabled: Boolean) = playback.setAudioOnly(enabled)

        val playbackManager: PlaybackManager get() = playback

        override fun onCleared() {
            if (isCurrent) playback.stop()
        }

        @AssistedFactory
        interface Factory {
            fun create(downloadId: String): OfflineWatchViewModel
        }
    }

private fun Download.toStreamItem() =
    StreamItem(
        id = videoId,
        title = title,
        channel = null,
        thumbnails = listOfNotNull(thumbnailPath?.let { Image("file://$it", null, null) }),
        type = StreamType.Video,
        duration = duration,
        viewCount = null,
        uploadedAt = null,
        uploadedAtText = null,
    )

@OptIn(UnstableApi::class)
@Composable
fun OfflineWatchScreen(
    downloadId: String,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        hiltViewModel<OfflineWatchViewModel, OfflineWatchViewModel.Factory>(key = downloadId) {
            it.create(downloadId)
        }
    val download by viewModel.download.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.playbackManager.sleepTimer.collectAsStateWithLifecycle()
    val abLoop by viewModel.playbackManager.abLoop.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val player = viewModel.player
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val inPip = rememberIsInPictureInPicture()
    val item = download

    // The record arrives asynchronously from storage; start once it is there.
    LifecycleStartEffect(item?.id) {
        viewModel.onVisible()
        onStopOrDispose {
            if (!settings.backgroundPlayback) player.pause()
        }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.onHidden() } }

    if (item == null) {
        LoadingState(modifier)
        return
    }
    if (inPip) {
        ContentFrame(player = player, modifier = modifier.fillMaxSize())
        return
    }

    Column(modifier.fillMaxSize()) {
        Box(
            Modifier
                .background(Color.Black)
                .statusBarsPadding()
                .fillMaxWidth()
                .aspectRatio(VIDEO_ASPECT),
        ) {
            val audioFile = item.format == DownloadFormat.Audio
            VideoPlayer(
                player = player,
                fullscreen = false,
                onToggleFullscreen = {},
                onOpenSettings = { showSettings = true },
                modifier = Modifier.fillMaxSize().pictureInPicture(settings.pictureInPicture),
                showFullscreen = false,
                seekStepSeconds = settings.seekStepSeconds,
                audioOnly = audioFile || settings.audioOnly,
                onToggleAudioOnly = if (audioFile) null else ({ viewModel.setAudioOnly(!settings.audioOnly) }),
                artworkUrl = item.thumbnailPath?.let { "file://$it" },
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleLargeEmphasized)
            item.channelName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showSettings) {
        PlayerSettingsSheet(
            player = player,
            pinnedHeight = null,
            networkLimit = null,
            settings = settings,
            sleepTimer = sleepTimer,
            abLoop = abLoop,
            actions = remember(viewModel) { viewModel.playbackManager.sheetActions(context) },
            onQuality = viewModel::setQuality,
            onDismiss = { showSettings = false },
        )
    }
}
