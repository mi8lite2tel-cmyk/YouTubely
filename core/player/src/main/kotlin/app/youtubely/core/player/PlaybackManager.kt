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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.youtubely.core.datastore.PlaybackSettingsRepository
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.StreamInfo
import app.youtubely.core.model.VideoId
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's single player. Screens load videos into it and [PlaybackService] wraps it in a
 * media session, so playback continues in the background, in picture-in-picture and on the lock
 * screen. Quality is capped per network type from [PlaybackSettings] unless the user picked one.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlaybackManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val factory: YouTubelyPlayer,
        settingsRepository: PlaybackSettingsRepository,
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

        init {
            observeNetwork()
            combine(settingsRepository.settings, unmetered) { settings, free -> settings to free }
                .onEach { (settings, free) ->
                    _settings.value = settings
                    applyLimits(settings, free)
                }.launchIn(scope)
        }

        /** Loads [info] and starts playing at [positionMs]. Returns `false` when nothing is playable. */
        @MainThread
        fun play(
            info: StreamInfo,
            positionMs: Long = 0,
        ): Boolean {
            val source = factory.mediaSource(info) ?: return false
            pinnedHeight = null
            applyLimits(_settings.value, unmetered.value)
            player.setMediaSource(source, positionMs)
            player.prepare()
            player.playWhenReady = true
            _currentVideo.value = info.item.id
            connectSession()
            return true
        }

        /** Plays a downloaded file; works offline. [id] identifies it like a video id does online. */
        @MainThread
        fun playLocal(
            id: VideoId,
            path: String,
            title: String,
            artist: String?,
            positionMs: Long = 0,
        ) {
            pinnedHeight = null
            applyLimits(_settings.value, unmetered.value)
            val item =
                MediaItem
                    .Builder()
                    .setMediaId(id.value)
                    .setUri(File(path).toUri())
                    .setMediaMetadata(
                        MediaMetadata
                            .Builder()
                            .setTitle(title)
                            .setArtist(artist)
                            .build(),
                    ).build()
            player.setMediaItem(item, positionMs)
            player.prepare()
            player.playWhenReady = true
            _currentVideo.value = id
            connectSession()
        }

        /** `null` returns to automatic quality within the network limit. */
        @MainThread
        fun setQuality(height: Int?) {
            pinnedHeight = height
            if (height == null) applyLimits(_settings.value, unmetered.value) else player.setVideoHeight(height)
        }

        @MainThread
        fun stop() {
            player.stop()
            player.clearMediaItems()
            _currentVideo.value = null
        }

        private fun applyLimits(
            settings: PlaybackSettings,
            isUnmetered: Boolean,
        ) {
            if (pinnedHeight != null) return
            val limit = if (isUnmetered) settings.maxHeightUnmetered else settings.maxHeightMetered
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setMinVideoSize(0, 0)
                    .setMaxVideoSize(Int.MAX_VALUE, limit ?: Int.MAX_VALUE)
                    .setPreferredAudioRoleFlags(C.ROLE_FLAG_MAIN)
                    .build()
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
