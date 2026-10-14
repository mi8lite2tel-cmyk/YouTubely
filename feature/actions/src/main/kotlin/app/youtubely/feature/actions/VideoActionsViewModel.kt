// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.data.PlaylistRepository
import app.youtubely.core.data.WatchLaterRepository
import app.youtubely.core.model.LocalPlaylist
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.VideoId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Watch later" and playlists for the video whose actions are open. */
@HiltViewModel
class VideoActionsViewModel
    @Inject
    constructor(
        private val watchLater: WatchLaterRepository,
        private val playlists: PlaylistRepository,
    ) : ViewModel() {
        private val sharing = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS)

        val savedForLater: StateFlow<Set<VideoId>> = watchLater.ids.stateIn(viewModelScope, sharing, emptySet())

        val playlistList: StateFlow<ImmutableList<LocalPlaylist>> =
            playlists.playlists.map { it.toImmutableList() }.stateIn(viewModelScope, sharing, persistentListOf())

        /** Ids of the playlists that contain [id]. */
        fun containing(id: VideoId): Flow<Set<Long>> = playlists.containing(id)

        fun toggleWatchLater(item: StreamItem) {
            viewModelScope.launch {
                if (item.id in savedForLater.value) watchLater.remove(item.id) else watchLater.add(item)
            }
        }

        fun setInPlaylist(
            playlist: Long,
            item: StreamItem,
            present: Boolean,
        ) {
            viewModelScope.launch {
                if (present) playlists.add(playlist, listOf(item)) else playlists.remove(playlist, item.id)
            }
        }

        fun createPlaylist(
            name: String,
            item: StreamItem,
        ) {
            viewModelScope.launch { playlists.add(playlists.create(name), listOf(item)) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
