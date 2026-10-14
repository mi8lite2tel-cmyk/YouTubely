// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.data.PlaylistRepository
import app.youtubely.core.model.StreamItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One playlist of this device: its name (`null` once it is deleted) and videos. */
@HiltViewModel(assistedFactory = LocalPlaylistViewModel.Factory::class)
class LocalPlaylistViewModel
    @AssistedInject
    constructor(
        @Assisted private val playlistId: Long,
        private val playlists: PlaylistRepository,
    ) : ViewModel() {
        private val sharing = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS)

        /** `null` until loaded or when the playlist does not exist (any more). */
        val name: StateFlow<String?> = playlists.playlist(playlistId).stateIn(viewModelScope, sharing, null)

        val items: StateFlow<ImmutableList<StreamItem>?> =
            playlists.items(playlistId).map { it.toImmutableList() }.stateIn(viewModelScope, sharing, null)

        fun rename(name: String) {
            viewModelScope.launch { playlists.rename(playlistId, name) }
        }

        fun delete() {
            viewModelScope.launch { playlists.delete(playlistId) }
        }

        fun remove(item: StreamItem) {
            viewModelScope.launch { playlists.remove(playlistId, item.id) }
        }

        fun restore(item: StreamItem) {
            viewModelScope.launch { playlists.add(playlistId, listOf(item)) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }

        @AssistedFactory
        interface Factory {
            fun create(playlistId: Long): LocalPlaylistViewModel
        }
    }
