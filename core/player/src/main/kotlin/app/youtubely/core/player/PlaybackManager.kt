// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.youtubely.core.data.ChannelPlaybackRepository
import app.youtubely.core.data.HistoryRepository
import app.youtubely.core.data.NetworkMonitor
import app.youtubely.core.datastore.PlaybackSettingsRepository
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.QualityCap
import app.youtubely.core.model.QualityReason
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
        private val quality: QualityController,
        private val channelPlayback: ChannelPlaybackRepository,
        private val bandwidth: BandwidthStore,
        private val network: NetworkMonitor,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val player: ExoPlayer by lazy {
            factory
                .createPlayer(
                    context,
                ).also { it.addAnalyticsListener(bandwidthListener) }
        }

        private val _currentVideo = MutableStateFlow<VideoId?>(null)
        val currentVideo: StateFlow<VideoId?> = _currentVideo.asStateFlow()

        private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)

        /** What is loaded in the player, for the mini player; `null` after [stop]. */
        val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

        /** Stream info of the online video in the player; reopening it must not load it again. */
        var currentInfo: StreamInfo? = null
            private set

        private val _settings = MutableStateFlow(PlaybackSettings())
        val settings: StateFlow<PlaybackSettings> = _settings.asStateFlow()

        private var situation = QualitySituation()

        /** The ceiling of the automatic quality and why; shown next to "Auto" in the player. */
        val qualityCap: StateFlow<QualityCap?> get() = quality.cap

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
            combine(settingsRepository.settings, quality.situation) { settings, now -> settings to now }
                .onEach { (settings, now) ->
                    _settings.value = settings
                    situation = now
                    applyLimits(settings)
                }.launchIn(scope)
        }

        private val bandwidthListener =
            object : AnalyticsListener {
                private var lastSavedAt = 0L

                override fun onBandwidthEstimate(
                    eventTime: AnalyticsListener.EventTime,
                    totalLoadTimeMs: Int,
                    totalBytesLoaded: Long,
                    bitrateEstimate: Long,
                ) {
                    val now = SystemClock.elapsedRealtime()
                    // Only real video transfers say something about the connection, and not too often.
                    if (_settings.value.audioOnly || now - lastSavedAt < BANDWIDTH_SAVE_INTERVAL_MS) return
                    lastSavedAt = now
                    bandwidth.record(network.isMetered, bitrateEstimate)
                }
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
            applyLimits(_settings.value)
            player.setMediaSource(source, positionMs)
            currentInfo = info
            start(info.item, downloadId = null)
            restoreChannelChoices(info.item.channel?.id)
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
            downloadId: String? = null,
        ) {
            savePosition()
            pinnedHeight = null
            clearLoop()
            baseVolume = 1f
            if (sleepJob?.isActive != true) player.volume = baseVolume
            applyLimits(_settings.value)
            val mediaItem =
                MediaItem
                    .Builder()
                    .setMediaId(item.id.value)
                    .setUri(File(path).toUri())
                    .setMediaMetadata(item.toMediaMetadata())
                    .build()
            player.setMediaItem(mediaItem, positionMs)
            currentInfo = null
            start(item, downloadId)
        }

        private fun start(
            item: StreamItem,
            downloadId: String?,
        ) {
            _nowPlaying.value = NowPlaying(item, downloadId)
            player.prepare()
            player.playWhenReady = true
            _currentVideo.value = item.id
            currentItem = item
            player.removeListener(historyListener)
            player.addListener(historyListener)
            scope.launch { history.record(item) }
            connectSession()
        }

        /** Applies one change to the stored playback settings; the player follows through the settings flow. */
        fun updateSettings(change: suspend PlaybackSettingsRepository.() -> Unit) {
            scope.launch { settingsRepository.change() }
        }

        /** Sound only, without downloading the picture. Kept for the next videos too. */
        fun setAudioOnly(enabled: Boolean) {
            _settings.value = _settings.value.copy(audioOnly = enabled)
            applyLimits(_settings.value)
            scope.launch { settingsRepository.setAudioOnly(enabled) }
        }

        /** `null` returns to automatic quality within the network limit. */
        @MainThread
        fun setQuality(height: Int?) {
            pinnedHeight = height
            applyLimits(_settings.value)
            remember { channelPlayback.setHeight(it, height) }
        }

        /** Sets the speed and remembers it for the channel of the current video. */
        @MainThread
        fun setSpeed(speed: Float) {
            player.setPlaybackSpeed(speed)
            remember { channelPlayback.setSpeed(it, speed) }
        }

        /** The video is shown in a window of this size; a small window does not need the sharpest picture. */
        fun setViewport(
            width: Int,
            height: Int,
        ) = quality.setViewport(Viewport(width, height))

        /** Applies what was last chosen for [channel], once the video has been handed to the player. */
        private fun restoreChannelChoices(channel: ChannelId?) {
            if (channel == null || !_settings.value.rememberPerChannel) return
            scope.launch {
                val saved = channelPlayback.get(channel) ?: return@launch
                if (currentItem?.channel?.id != channel) return@launch
                saved.height?.let {
                    pinnedHeight = it
                    applyLimits(_settings.value)
                }
                saved.speed?.let(player::setPlaybackSpeed)
            }
        }

        private fun remember(save: suspend (ChannelId) -> Unit) {
            val channel = currentItem?.channel?.id ?: return
            if (!_settings.value.rememberPerChannel) return
            scope.launch { save(channel) }
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
            _nowPlaying.value = null
            currentInfo = null
        }

        private fun applyLimits(settings: PlaybackSettings) {
            val parameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, settings.audioOnly)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !settings.subtitlesEnabled)
                    .setPreferredTextLanguage(settings.subtitleLanguage ?: Locale.getDefault().language)
                    .setPreferredAudioRoleFlags(C.ROLE_FLAG_MAIN)
            val cap = quality.configure(parameters, settings, situation, pinnedHeight)
            savingLoadControl(settings, cap)
            player.trackSelectionParameters = parameters.build()
            player.skipSilenceEnabled = settings.skipSilence
            if (sleepJob?.isActive != true) player.volume = if (settings.normalizeVolume) baseVolume else 1f
        }

        /** A short buffer wastes less when the data saver is on or the monthly allowance is nearly spent. */
        private fun savingLoadControl(
            settings: PlaybackSettings,
            cap: QualityCap,
        ) {
            val saving = cap.reason == QualityReason.DataSaver || cap.reason == QualityReason.MonthlyLimit
            factory.loadControl.maxBufferMs = if (saving && !settings.audioOnly) SAVER_BUFFER_MS else null
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
            const val BANDWIDTH_SAVE_INTERVAL_MS = 30_000L
            const val SAVER_BUFFER_MS = 20_000L
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

/** The video in the player; [downloadId] is set when it plays from a download. */
data class NowPlaying(
    val item: StreamItem,
    val downloadId: String?,
)

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
    private const val DECIBEL_BASE = 10.0

    /** [loudnessDb] is how much louder than YouTube's reference level the video is (`null` = unknown). */
    fun volumeFor(loudnessDb: Float?): Float {
        if (loudnessDb == null || loudnessDb <= 0f) return 1f
        return Math
            .pow(DECIBEL_BASE, -loudnessDb / DB_PER_DECADE)
            .toFloat()
            .coerceIn(MIN_VOLUME, 1f)
    }
}
