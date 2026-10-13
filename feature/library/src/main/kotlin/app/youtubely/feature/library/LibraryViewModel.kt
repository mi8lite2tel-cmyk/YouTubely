// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.data.HistoryRepository
import app.youtubely.core.data.SubscriptionRepository
import app.youtubely.core.datastore.PlaybackSettingsRepository
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.HistoryEntry
import app.youtubely.core.model.VideoId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** History and subscriptions, both stored only on this device. */
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val history: HistoryRepository,
        private val subscriptions: SubscriptionRepository,
        private val settings: PlaybackSettingsRepository,
    ) : ViewModel() {
        private val sharing = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS)

        /** `null` until loaded, so the empty state doesn't flash. */
        val historyEntries: StateFlow<ImmutableList<HistoryEntry>?> =
            history.history.map { it.toImmutableList() }.stateIn(viewModelScope, sharing, null)

        val channels: StateFlow<ImmutableList<ChannelRef>?> =
            subscriptions.subscriptions.map { it.toImmutableList() }.stateIn(viewModelScope, sharing, null)

        val historyEnabled: StateFlow<Boolean> =
            settings.settings.map { it.historyEnabled }.stateIn(viewModelScope, sharing, true)

        fun remove(id: VideoId) = launch { history.remove(id) }

        fun restore(entry: HistoryEntry) = launch { history.record(entry.item, entry.positionMs) }

        fun clearHistory() = launch { history.clear() }

        fun setHistoryEnabled(enabled: Boolean) = launch { settings.setHistoryEnabled(enabled) }

        fun unsubscribe(channel: ChannelRef) = launch { subscriptions.unsubscribe(channel.id) }

        fun resubscribe(channel: ChannelRef) = launch { subscriptions.subscribe(channel) }

        private fun launch(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
