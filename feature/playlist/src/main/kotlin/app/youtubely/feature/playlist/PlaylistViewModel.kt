// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.common.Outcome
import app.youtubely.core.common.outcomeOf
import app.youtubely.core.download.DownloadManager
import app.youtubely.core.model.Page
import app.youtubely.core.model.Playlist
import app.youtubely.core.model.PlaylistId
import app.youtubely.core.model.StreamItem
import app.youtubely.core.provider.StreamProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistUiState(
    val playlist: Playlist? = null,
    val items: List<StreamItem> = emptyList(),
    val nextPage: Page? = null,
    val loadingMore: Boolean = false,
    val error: Throwable? = null,
)

@HiltViewModel(assistedFactory = PlaylistViewModel.Factory::class)
class PlaylistViewModel
    @AssistedInject
    constructor(
        @Assisted playlistId: String,
        private val provider: StreamProvider,
        private val downloads: DownloadManager,
    ) : ViewModel() {
        val id = PlaylistId(playlistId)

        private val _state = MutableStateFlow(PlaylistUiState())
        val state: StateFlow<PlaylistUiState> = _state.asStateFlow()

        init {
            load()
        }

        fun retry() = load()

        /** Saves the whole playlist: the pages not shown yet are fetched first, up to the batch limit. */
        fun downloadAll(maxHeight: Int?) {
            val current = _state.value
            viewModelScope.launch {
                val items = current.items.toMutableList()
                var next = current.nextPage
                while (next != null && items.size < BATCH_LIMIT) {
                    val page = (outcomeOf { provider.playlist(id, next) } as? Outcome.Success)?.value ?: break
                    items += page.items.items
                    next = page.items.nextPage
                }
                downloads.startAll(items.distinctBy { it.id }.map { it.id }, maxHeight)
            }
        }

        fun loadMore() {
            val current = _state.value
            val next = current.nextPage ?: return
            if (current.loadingMore) return
            _state.update { it.copy(loadingMore = true) }
            viewModelScope.launch {
                val result = outcomeOf { provider.playlist(id, next) }
                _state.update { state ->
                    if (result is Outcome.Success) {
                        state.copy(
                            items = (state.items + result.value.items.items).distinctBy { it.id },
                            nextPage = result.value.items.nextPage,
                            loadingMore = false,
                        )
                    } else {
                        state.copy(loadingMore = false)
                    }
                }
            }
        }

        private fun load() {
            _state.value = PlaylistUiState()
            viewModelScope.launch {
                _state.value =
                    when (val result = outcomeOf { provider.playlist(id, null) }) {
                        is Outcome.Success -> {
                            PlaylistUiState(
                                playlist = result.value.playlist,
                                items = result.value.items.items,
                                nextPage = result.value.items.nextPage,
                            )
                        }

                        is Outcome.Failure -> {
                            PlaylistUiState(error = result.error)
                        }
                    }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(playlistId: String): PlaylistViewModel
        }
    }

/** Pages are fetched for "download all" until this many videos are known. */
private const val BATCH_LIMIT = 300
