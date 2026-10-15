// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import app.youtubely.core.common.Outcome
import app.youtubely.core.common.outcomeOf
import app.youtubely.core.data.HistoryRepository
import app.youtubely.core.data.PlaylistRepository
import app.youtubely.core.data.SubscriptionRepository
import app.youtubely.core.data.WatchLaterRepository
import app.youtubely.core.datastore.SponsorBlockSettingsRepository
import app.youtubely.core.download.DownloadManager
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.Download
import app.youtubely.core.model.DownloadFormat
import app.youtubely.core.model.LocalQueue
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.Playlist
import app.youtubely.core.model.PlaylistId
import app.youtubely.core.model.SponsorSegment
import app.youtubely.core.model.StreamInfo
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.StreamType
import app.youtubely.core.model.VideoId
import app.youtubely.core.player.PlaybackManager
import app.youtubely.core.player.SegmentSkipper
import app.youtubely.core.player.SkipNotice
import app.youtubely.core.provider.ProviderError
import app.youtubely.core.provider.StreamProvider
import app.youtubely.core.sponsorblock.SponsorBlockClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WatchUiState {
    data object Loading : WatchUiState

    data class Error(
        val error: Throwable,
    ) : WatchUiState

    data class Content(
        val info: StreamInfo,
    ) : WatchUiState
}

/** The playlist a video was opened from; [index] is the item playing now. */
data class PlaylistQueue(
    val playlist: Playlist,
    val items: List<StreamItem>,
    val index: Int,
) {
    val next: StreamItem? get() = items.getOrNull(index + 1)
}

/**
 * One video on the shared [PlaybackManager] player. Several watch screens can be on the back
 * stack; the visible one reclaims the player (see [onVisible]) and resumes where it left off.
 * Opened from a playlist, the screen plays the playlist in place and moves on to the next video
 * by itself, also in the background.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = WatchViewModel.Factory::class)
class WatchViewModel
    @AssistedInject
    constructor(
        @Assisted("video") videoId: String,
        @Assisted("playlist") playlistId: String?,
        private val provider: StreamProvider,
        private val playback: PlaybackManager,
        private val downloadManager: DownloadManager,
        private val history: HistoryRepository,
        private val subscriptions: SubscriptionRepository,
        private val watchLater: WatchLaterRepository,
        private val localPlaylists: PlaylistRepository,
        private val sponsorBlock: SponsorBlockClient,
        private val sponsorSettings: SponsorBlockSettingsRepository,
        private val skipper: SegmentSkipper,
    ) : ViewModel() {
        private val currentId = MutableStateFlow(VideoId(videoId))
        private val id: VideoId get() = currentId.value
        val player: Player get() = playback.player
        val settings: StateFlow<PlaybackSettings> get() = playback.settings

        private val _state = MutableStateFlow<WatchUiState>(WatchUiState.Loading)
        val state: StateFlow<WatchUiState> = _state.asStateFlow()

        private val _queue = MutableStateFlow<PlaylistQueue?>(null)
        val queue: StateFlow<PlaylistQueue?> = _queue.asStateFlow()

        /** `null` until the stream info is known. */
        private var savedPositionMs: Long? = null
        private var loading: Job? = null

        /** This video's downloads, any quality. */
        val downloads: StateFlow<List<Download>> =
            combine(downloadManager.downloads, currentId) { all, current -> all.filter { it.videoId == current } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        val isSubscribed: StateFlow<Boolean> =
            _state
                .flatMapLatest { state ->
                    val channel = (state as? WatchUiState.Content)?.info?.item?.channel
                    if (channel == null) flowOf(false) else subscriptions.isSubscribed(channel.id)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

        fun downloadFormats(info: StreamInfo): List<DownloadFormat> = downloadManager.formats(info)

        fun download(format: DownloadFormat) {
            val content = _state.value as? WatchUiState.Content ?: return
            viewModelScope.launch { downloadManager.start(content.info, format) }
        }

        fun toggleSubscription(channel: ChannelRef) {
            viewModelScope.launch {
                if (isSubscribed.value) subscriptions.unsubscribe(channel.id) else subscriptions.subscribe(channel)
            }
        }

        fun setAudioOnly(enabled: Boolean) = playback.setAudioOnly(enabled)

        val playbackManager: PlaybackManager get() = playback
        val sleepTimer get() = playback.sleepTimer
        val abLoop get() = playback.abLoop

        /** Stretches SponsorBlock marks in the current video (empty while it is off or nothing is marked). */
        val sponsorSegments: StateFlow<List<SponsorSegment>> get() = skipper.segments

        /** The last automatic skip, shown for a few seconds with an undo button. */
        val skipNotice: StateFlow<SkipNotice?> get() = skipper.notice

        private var segmentsJob: Job? = null

        /** Puts the player back where the skipped stretch began and leaves that stretch alone from now on. */
        fun undoSkip() = skipper.undo()

        /** Looks up the SponsorBlock stretches of [video]; the skipper keeps them while it plays. */
        private fun loadSegments(video: VideoId) {
            if (skipper.video == video) return
            segmentsJob?.cancel()
            skipper.clear()
            segmentsJob =
                viewModelScope.launch {
                    val settings = sponsorSettings.settings.first()
                    if (!settings.enabled) return@launch
                    val found =
                        outcomeOf { sponsorBlock.segments(video, settings) } as? Outcome.Success ?: return@launch
                    if (playback.currentVideo.value == video) skipper.start(video, found.value)
                }
        }

        private val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && steersPlayback && !playback.consumeSleepAtEnd()) {
                        playNext()
                    }
                }
            }

        init {
            player.addListener(listener)
            load()
            playlistId?.let { loadQueue(PlaylistId(it)) }
        }

        val isCurrent: Boolean get() = playback.currentVideo.value == id

        /** This screen shows what plays and was the last to take over; other video screens leave it alone. */
        private val steersPlayback: Boolean get() = isCurrent && playback.steeringScreen === this

        fun retry() = load(positionMs = savedPositionMs ?: 0, refetch = true)

        /** The screen is shown again (e.g. back from another video): take the player back. */
        fun onVisible() {
            val content = _state.value as? WatchUiState.Content ?: return
            playback.steeringScreen = this
            if (!isCurrent) {
                if (playback.play(content.info, savedPositionMs ?: 0)) {
                    // The player singleton forgot this video's SponsorBlock marks while another one played.
                    loadSegments(content.info.item.id)
                } else {
                    _state.value = WatchUiState.Error(ProviderError.ExtractorOutdated())
                }
            }
        }

        /** Another screen covers this one: remember the position for [onVisible]. */
        fun onHidden() {
            if (isCurrent) savedPositionMs = player.currentPosition
        }

        fun setQuality(height: Int?) = playback.setQuality(height)

        fun setVideoSuspended(suspended: Boolean) = playback.setVideoSuspended(suspended)

        val pinnedQuality: Int? get() = playback.pinnedHeight

        /** Plays another video of the queue on this screen. */
        fun playQueueItem(index: Int) {
            val queue = _queue.value ?: return
            val item = queue.items.getOrNull(index) ?: return
            _queue.value = queue.copy(index = index)
            currentId.value = item.id
            savedPositionMs = null
            _state.value = WatchUiState.Loading
            load()
        }

        /** The next video of the queue, or the first suggested one when autoplay is on. */
        private fun playNext() {
            val queue = _queue.value
            val related = (_state.value as? WatchUiState.Content)?.info?.related.orEmpty()
            // A video on repeat goes on repeating; nothing else starts.
            if (player.repeatMode != Player.REPEAT_MODE_OFF) return
            when {
                queue?.next != null -> {
                    playQueueItem(queue.index + 1)
                }

                settings.value.autoplayNext -> {
                    related.firstOrNull { it.id != id && it.type == StreamType.Video }?.let(::playSuggested)
                }
            }
        }

        /** Plays [item] on this screen, like the next video of a queue. */
        private fun playSuggested(item: StreamItem) {
            currentId.value = item.id
            savedPositionMs = null
            _state.value = WatchUiState.Loading
            load()
        }

        private fun loadQueue(playlistId: PlaylistId) {
            if (LocalQueue.isLocal(playlistId.value)) {
                loadLocalQueue(playlistId)
                return
            }
            viewModelScope.launch {
                val page = outcomeOf { provider.playlist(playlistId, null) } as? Outcome.Success ?: return@launch
                val items = page.value.items.items
                _queue.value =
                    PlaylistQueue(
                        playlist = page.value.playlist,
                        items = items,
                        index = items.indexOfFirst { it.id == id }.coerceAtLeast(0),
                    )
            }
        }

        /** "Watch later" or a playlist of this device, as it is when playback starts. */
        private fun loadLocalQueue(queueId: PlaylistId) {
            viewModelScope.launch {
                val localId = LocalQueue.playlistIdOf(queueId.value)
                val items =
                    if (localId == null) {
                        watchLater.videos.first().map { it.item }
                    } else {
                        localPlaylists.itemsOnce(localId)
                    }
                val name = localId?.let { localPlaylists.playlist(it).first() }.orEmpty()
                _queue.value =
                    PlaylistQueue(
                        playlist =
                            Playlist(
                                queueId,
                                name,
                                uploader = null,
                                thumbnails = emptyList(),
                                streamCount = items.size.toLong(),
                            ),
                        items = items,
                        index = items.indexOfFirst { it.id == id }.coerceAtLeast(0),
                    )
            }
        }

        private fun load(
            positionMs: Long? = null,
            refetch: Boolean = false,
        ) {
            val previous = (_state.value as? WatchUiState.Content)?.takeIf { it.info.item.id == id }
            if (previous == null) _state.value = WatchUiState.Loading
            loading?.cancel()
            loading =
                viewModelScope.launch {
                    val requested = id
                    playback.steeringScreen = this@WatchViewModel
                    // Back from the mini player: the video is still loaded, keep playing as it is.
                    val playing = playback.currentInfo?.takeIf { it.item.id == requested && isCurrent && !refetch }
                    if (playing != null) {
                        savedPositionMs = player.currentPosition
                        loadSegments(requested)
                        _state.value = WatchUiState.Content(playing)
                        return@launch
                    }
                    val info =
                        if (previous != null && !refetch) {
                            previous.info
                        } else {
                            when (val result = outcomeOf { provider.streamInfo(requested) }) {
                                is Outcome.Success -> {
                                    result.value
                                }

                                is Outcome.Failure -> {
                                    _state.value = WatchUiState.Error(result.error)
                                    return@launch
                                }
                            }
                        }
                    val start = positionMs ?: history.resumePosition(requested)
                    savedPositionMs = start
                    _state.value =
                        if (playback.play(info, start)) {
                            loadSegments(requested)
                            WatchUiState.Content(info)
                        } else {
                            // Extraction succeeded but produced nothing playable: the extractor needs an update.
                            WatchUiState.Error(ProviderError.ExtractorOutdated())
                        }
                }
        }

        /** Leaving the video (back) keeps it playing in the mini player, which can stop it. */
        override fun onCleared() {
            player.removeListener(listener)
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }

        @AssistedFactory
        interface Factory {
            fun create(
                @Assisted("video") videoId: String,
                @Assisted("playlist") playlistId: String?,
            ): WatchViewModel
        }
    }
