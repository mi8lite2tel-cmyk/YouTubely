// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import app.youtubely.core.common.Outcome
import app.youtubely.core.common.outcomeOf
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.StreamInfo
import app.youtubely.core.model.VideoId
import app.youtubely.core.player.PlaybackManager
import app.youtubely.core.provider.ProviderError
import app.youtubely.core.provider.StreamProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/**
 * One video on the shared [PlaybackManager] player. Several watch screens can be on the back
 * stack; the visible one reclaims the player (see [onVisible]) and resumes where it left off.
 */
@HiltViewModel(assistedFactory = WatchViewModel.Factory::class)
class WatchViewModel
    @AssistedInject
    constructor(
        @Assisted videoId: String,
        private val provider: StreamProvider,
        private val playback: PlaybackManager,
    ) : ViewModel() {
        private val id = VideoId(videoId)
        val player: Player get() = playback.player
        val settings: StateFlow<PlaybackSettings> get() = playback.settings

        private val _state = MutableStateFlow<WatchUiState>(WatchUiState.Loading)
        val state: StateFlow<WatchUiState> = _state.asStateFlow()

        private var savedPositionMs = 0L

        /** Media URLs are signed for a few hours; one expired-URL error triggers a silent reload. */
        private var reloadedAfterHttpError = false

        private val listener =
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    val expiredUrl = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                    if (isCurrent && expiredUrl && !reloadedAfterHttpError) {
                        reloadedAfterHttpError = true
                        load(positionMs = player.currentPosition, refetch = true)
                    }
                }
            }

        init {
            player.addListener(listener)
            load()
        }

        val isCurrent: Boolean get() = playback.currentVideo.value == id

        fun retry() = load(positionMs = savedPositionMs, refetch = true)

        /** The screen is shown again (e.g. back from another video): take the player back. */
        fun onVisible() {
            val content = _state.value as? WatchUiState.Content ?: return
            if (!isCurrent && !playback.play(content.info, savedPositionMs)) {
                _state.value = WatchUiState.Error(ProviderError.ExtractorOutdated())
            }
        }

        /** Another screen covers this one: remember the position for [onVisible]. */
        fun onHidden() {
            if (isCurrent) savedPositionMs = player.currentPosition
        }

        fun setQuality(height: Int?) = playback.setQuality(height)

        val pinnedQuality: Int? get() = playback.pinnedHeight

        private fun load(
            positionMs: Long = 0,
            refetch: Boolean = false,
        ) {
            val previous = _state.value as? WatchUiState.Content
            if (previous == null) _state.value = WatchUiState.Loading
            viewModelScope.launch {
                val info =
                    if (previous != null && !refetch) {
                        previous.info
                    } else {
                        when (val result = outcomeOf { provider.streamInfo(id) }) {
                            is Outcome.Success -> {
                                result.value
                            }

                            is Outcome.Failure -> {
                                _state.value = WatchUiState.Error(result.error)
                                return@launch
                            }
                        }
                    }
                _state.value =
                    if (playback.play(info, positionMs)) {
                        WatchUiState.Content(info)
                    } else {
                        // Extraction succeeded but produced nothing playable: the extractor needs an update.
                        WatchUiState.Error(ProviderError.ExtractorOutdated())
                    }
            }
        }

        /** Leaving the video (back) ends its playback; the shared player stays for the next one. */
        override fun onCleared() {
            player.removeListener(listener)
            if (isCurrent) playback.stop()
        }

        @AssistedFactory
        interface Factory {
            fun create(videoId: String): WatchViewModel
        }
    }
