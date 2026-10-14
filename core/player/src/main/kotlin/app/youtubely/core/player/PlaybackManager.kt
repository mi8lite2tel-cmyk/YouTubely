// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
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
import java.util.Locale
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

        private val _sleepTimer = MutableStateFlow<SleepTimer>(SleepTimer.Off)
        val sleepTimer: StateFlow<SleepTimer> = _sleepTimer.asStateFlow()
        private var sleepJob: Job? = null

        private val _abLoop = MutableStateFlow(AbLoop())
        val abLoop: StateFlow<AbLoop> = _abLoop.asStateFlow()
        private var loopJob: Job? = null

        /** Volume of the current video after normalization, before any fade-out. */
        private var baseVolume = 1f

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
            clearLoop()
            baseVolume = VolumeNormalizer.volumeFor(info.loudnessDb).takeIf { _settings.value.normalizeVolume } ?: 1f
            if (sleepJob?.isActive != true) player.volume = baseVolume
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
            clearLoop()
            baseVolume = 1f
            if (sleepJob?.isActive != true) player.volume = baseVolume
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
            clearLoop()
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
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !settings.subtitlesEnabled)
                    .setPreferredTextLanguage(settings.subtitleLanguage ?: Locale.getDefault().language)
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
            player.skipSilenceEnabled = settings.skipSilence
            if (sleepJob?.isActive != true) player.volume = if (settings.normalizeVolume) baseVolume else 1f
        }

        /** Pauses after [minutes], turning the volume down over the last few seconds. */
        fun startSleepTimer(minutes: Int) {
            cancelSleepTimer()
            val endsAt = SystemClock.elapsedRealtime() + minutes * MILLIS_PER_MINUTE
            _sleepTimer.value = SleepTimer.In(endsAt)
            sleepJob =
                scope.launch {
                    delay((minutes * MILLIS_PER_MINUTE - FADE_MS).coerceAtLeast(0))
                    fadeOutAndPause()
                    _sleepTimer.value = SleepTimer.Off
                }
        }

        /** Pauses when the current video ends instead of going on to the next one. */
        fun sleepAtEndOfVideo() {
            cancelSleepTimer()
            _sleepTimer.value = SleepTimer.AtEndOfVideo
        }

        fun cancelSleepTimer() {
            sleepJob?.cancel()
            sleepJob = null
            if (_sleepTimer.value != SleepTimer.Off) player.volume = baseVolume
            _sleepTimer.value = SleepTimer.Off
        }

        /** `true` once when the video that was playing at "sleep at the end" has ended: do not go on. */
        fun consumeSleepAtEnd(): Boolean {
            if (_sleepTimer.value != SleepTimer.AtEndOfVideo) return false
            _sleepTimer.value = SleepTimer.Off
            return true
        }

        private suspend fun fadeOutAndPause() {
            val steps = (FADE_MS / FADE_STEP_MS).toInt()
            for (step in 1..steps) {
                player.volume = baseVolume * (1f - step.toFloat() / steps)
                delay(FADE_STEP_MS)
            }
            player.pause()
            player.volume = baseVolume
        }

        /** Marks the current position as the start of a stretch to repeat. */
        fun markLoopStart() {
            _abLoop.value = AbLoop(startMs = player.currentPosition, endMs = null)
            loopJob?.cancel()
        }

        /** Marks the current position as the end and starts repeating from the start mark. */
        fun markLoopEnd() {
            val start = _abLoop.value.startMs ?: return
            val end = player.currentPosition
            if (end <= start + MIN_LOOP_MS) return
            _abLoop.value = AbLoop(start, end)
            loopJob?.cancel()
            loopJob =
                scope.launch {
                    while (isActive) {
                        delay(LOOP_CHECK_MS)
                        if (player.isPlaying && player.currentPosition >= end) player.seekTo(start)
                    }
                }
            player.seekTo(start)
        }

        fun clearLoop() {
            loopJob?.cancel()
            loopJob = null
            _abLoop.value = AbLoop()
        }

        /** For the system equalizer. */
        val audioSessionId: Int get() = player.audioSessionId

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
            const val MILLIS_PER_MINUTE = 60_000L
            const val FADE_MS = 8_000L
            const val FADE_STEP_MS = 250L
            const val MIN_LOOP_MS = 1_000L
            const val LOOP_CHECK_MS = 100L
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

/** The sleep timer: off, after a time, or when the current video ends. */
sealed interface SleepTimer {
    data object Off : SleepTimer

    /** [endsAtElapsedMs] is on the [SystemClock.elapsedRealtime] clock. */
    data class In(
        val endsAtElapsedMs: Long,
    ) : SleepTimer

    data object AtEndOfVideo : SleepTimer
}

/** The stretch to repeat (A–B repeat); [endMs] is `null` until the end mark is set. */
data class AbLoop(
    val startMs: Long? = null,
    val endMs: Long? = null,
) {
    val isActive: Boolean get() = startMs != null && endMs != null
}

/** Turns loud videos down to a common level; quiet ones are left alone. */
object VolumeNormalizer {
    private const val MIN_VOLUME = 0.25f
    private const val DB_PER_DECADE = 20.0

    /** [loudnessDb] is how much louder than YouTube's reference level the video is (`null` = unknown). */
    fun volumeFor(loudnessDb: Float?): Float {
        if (loudnessDb == null || loudnessDb <= 0f) return 1f
        return Math
            .pow(10.0, -loudnessDb / DB_PER_DECADE)
            .toFloat()
            .coerceIn(MIN_VOLUME, 1f)
    }
}
