// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.data.HistoryRepository
import app.youtubely.core.data.TrafficMeter
import app.youtubely.core.model.TrafficSummary
import app.youtubely.core.model.WatchStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        history: HistoryRepository,
        traffic: TrafficMeter,
    ) : ViewModel() {
        private val sharing = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS)

        /** `null` while the history loads. */
        val stats: StateFlow<WatchStats?> =
            history.history.map { WatchStats.from(it, Instant.now()) }.stateIn(viewModelScope, sharing, null)

        val traffic: StateFlow<TrafficSummary> = traffic.summary.stateIn(viewModelScope, sharing, TrafficSummary())

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
