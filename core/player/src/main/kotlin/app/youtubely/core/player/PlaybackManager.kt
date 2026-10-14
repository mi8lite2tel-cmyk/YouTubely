// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.youtubely.core.data.HistoryRepository
import app.youtubely.core.datastore.PlaybackSettingsRepository
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.StreamInfo
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.VideoId
import app.youtubely.core.model.bestFor
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's single player. Screens load videos into it and [PlaybackService] wraps it in a
 * media session, so playback continues in the background, in picture-in-picture and on the lock
 * screen. Quality is capped per network type from [PlaybackSettings] unless the user picked one.
 * In audio-only mode the video track is disabled, so the player doesn't download it at all, and
 * the lowest video quality is selected in case it is enabled again.
 * The position of the current video is saved to the local history while it plays.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlaybackManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val factory: YouTubelyPlayer,
        private val settingsRepository: PlaybackSettingsRepository,
        private val history: HistoryRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val player: ExoPlayer by lazy { factory.createPlayer(context) }

        private val _currentVideo = MutableStateFlow<VideoId?>(null)
        val currentVideo: StateFlow<VideoId?> = _currentVideo.asStateFlow()

        private val _settings = MutableStateFlow(PlaybackSettings())
        val settings: StateFlow<PlaybackSettings> = _settings.asStateFlow()

        private val unmetered = MutableStateFlow(true)

        /** Resolution the user picked in the player for the current video; `null` = automatic. */
        var pinnedHeight: Int? = null
            private set

        private var controller: ListenableFuture<MediaController>? = null

        /** What is playing, for the history. */
        private var currentItem: StreamItem? = null
        private var positionSaver: Job? = null

        init {
            observeNetwork()
            combine(settingsRepository.settings, unmetered) { settings, free -> settings to free }
                .onEach { (settings, free) ->
                    _settings.value = settings
                    applyLimits(settings, free)
                }.launchIn(scope)
        }

        private val historyListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) startSavingPosition() else savePosition()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) savePosition()
                }
            }

        /** Loads [info] and starts playing at [positionMs]. Returns `false` when nothing is playable. */
        @MainThread
        fun play(
            info: StreamInfo,
            positionMs: Long = 0,
        ): Boolean {
            val source = factory.mediaSource(info) ?: return false
            savePosition()
            pinnedHeight = null
            applyLimits(_settings.value, unmetered.value)
            player.setMediaSource(source, positionMs)
            start(info.item)
            return true
        }

        /**
         * Plays a downloaded file; works offline. [item] describes it like an online video, so it
         * shows up in the history and the media notification the same way.
         */
        @MainThread
        fun playLocal(
            item: StreamItem,
            path: String,
            positionMs: Long = 0,
        ) {
            savePosition()
            pinnedHeight = null
            applyLimits(_settings.value, unmetered.value)
            val mediaItem =
                MediaItem
                    .Builder()
                    .setMediaId(item.id.value)
                    .setUri(File(path).toUri())
                    .setMediaMetadata(item.toMediaMetadata())
                    .build()
            player.setMediaItem(mediaItem, positionMs)
            start(item)
        }

        private fun start(item: StreamItem) {
            player.prepare()
            player.playWhenReady = true
            _currentVideo.value = item.id
            currentItem = item
            player.removeListener(historyListener)
            player.addListener(historyListener)
            scope.launch { history.record(item) }
            connectSession()
        }

        /** Sound only, without downloading the picture. Kept for the next videos too. */
        fun setAudioOnly(enabled: Boolean) {
            _settings.value = _settings.value.copy(audioOnly = enabled)
            applyLimits(_settings.value, unmetered.value)
            scope.launch { settingsRepository.setAudioOnly(enabled) }
        }

        /** `null` returns to automatic quality within the network limit. */
        @MainThread
        fun setQuality(height: Int?) {
            pinnedHeight = height
            applyLimits(_settings.value, unmetered.value)
        }

        @MainThread
        fun stop() {
            savePosition()
            currentItem = null
            player.removeListener(historyListener)
            player.stop()
            player.clearMediaItems()
            _currentVideo.value = null
        }

        private fun applyLimits(
            settings: PlaybackSettings,
            isUnmetered: Boolean,
        ) {
            val parameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, settings.audioOnly)
                    .setPreferredAudioRoleFlags(C.ROLE_FLAG_MAIN)
            val pinned = pinnedHeight
            when {
                // Sound only: besides not loading the picture, fall back to the smallest video
                // rendition so that switching back to video never starts with a heavy stream.
                settings.audioOnly -> {
                    parameters.setMinVideoSize(0, 0).setMaxVideoSize(Int.MAX_VALUE, LOWEST_HEIGHT)
                }

                pinned != null -> {
                    parameters.setMinVideoSize(0, pinned).setMaxVideoSize(Int.MAX_VALUE, pinned)
                }

                else -> {
                    val limit = if (isUnmetered) settings.maxHeightUnmetered else settings.maxHeightMetered
                    parameters.setMinVideoSize(0, 0).setMaxVideoSize(Int.MAX_VALUE, limit ?: Int.MAX_VALUE)
                }
            }
            player.trackSelectionParameters = parameters.build()
        }

        private fun startSavingPosition() {
            positionSaver?.cancel()
            positionSaver =
                scope.launch {
                    while (isActive) {
                        delay(SAVE_INTERVAL_MS)
                        savePosition()
                    }
                }
        }

        private fun savePosition() {
            if (!player.isPlaying) positionSaver?.cancel()
            val item = currentItem ?: return
            if (player.currentMediaItem?.mediaId != item.id.value) return
            val position =
                if (player.playbackState == Player.STATE_ENDED) player.duration else player.currentPosition
            if (position > 0) scope.launch { history.savePosition(item.id, position) }
        }

        private companion object {
            const val SAVE_INTERVAL_MS = 10_000L
            const val LOWEST_HEIGHT = 144
        }

        /** Binding a controller starts [PlaybackService], which then shows the media notification. */
        private fun connectSession() {
            if (controller != null) return
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            controller = MediaController.Builder(context, token).buildAsync()
        }

        private fun observeNetwork() {
            val connectivity = context.getSystemService<ConnectivityManager>() ?: return
            connectivity.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        unmetered.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    }
                },
            )
        }
    }

internal fun StreamItem.toMediaMetadata(): MediaMetadata =
    MediaMetadata
        .Builder()
        .setTitle(title)
        .setArtist(channel?.name)
        // Shown on the lock screen and in the media notification, also in audio-only mode.
        .setArtworkUri(thumbnails.bestFor(ARTWORK_WIDTH)?.url?.toUri())
        .build()

private const val ARTWORK_WIDTH = 720
