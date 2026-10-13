// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.common.Outcome
import app.youtubely.core.common.outcomeOf
import app.youtubely.core.data.FeedState
import app.youtubely.core.data.SubscriptionFeed
import app.youtubely.core.data.SubscriptionRepository
import app.youtubely.core.model.ExploreKind
import app.youtubely.core.model.ExploreSection
import app.youtubely.core.model.Region
import app.youtubely.core.provider.StreamProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Error(
        val error: Throwable,
    ) : HomeUiState

    data class Content(
        val sections: ImmutableList<ExploreSection>,
        val selected: ExploreKind,
        val refreshing: Boolean = false,
        /** The last pull-to-refresh failed; the old content stays and a message is shown once. */
        val refreshError: Throwable? = null,
    ) : HomeUiState {
        val current: ExploreSection get() = sections.first { it.kind == selected }
    }
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val provider: StreamProvider,
        private val feed: SubscriptionFeed,
        subscriptions: SubscriptionRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
        val state: StateFlow<HomeUiState> = _state.asStateFlow()

        /** New videos of subscribed channels; the home screen opens on them when there are any. */
        val feedState: StateFlow<FeedState> = feed.state

        val hasSubscriptions: StateFlow<Boolean> =
            subscriptions.subscriptions
                .map { it.isNotEmpty() }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

        private val _showFeed = MutableStateFlow(true)
        val showFeed: StateFlow<Boolean> = _showFeed.asStateFlow()

        private val region =
            Locale
                .getDefault()
                .country
                .takeIf { it.length == 2 && it.all(Char::isUpperCase) }
                ?.let(::Region) ?: Region("US")

        init {
            load()
            // A new or removed subscription changes the feed right away.
            viewModelScope.launch {
                subscriptions.subscriptions
                    .map { list -> list.map { it.id } }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { feed.refresh() }
            }
            viewModelScope.launch { feed.refreshIfStale() }
        }

        fun refreshFeed() {
            viewModelScope.launch { feed.refresh() }
        }

        fun showFeed() {
            _showFeed.value = true
        }

        /** Charts are fetched live each time: they change through the day. */
        fun load(refresh: Boolean = false) {
            val previous = _state.value as? HomeUiState.Content
            _state.value =
                when {
                    refresh && previous != null -> previous.copy(refreshing = true, refreshError = null)
                    else -> HomeUiState.Loading
                }
            viewModelScope.launch {
                _state.value =
                    when (val result = outcomeOf { provider.explore(region) }) {
                        is Outcome.Success -> {
                            val sections = result.value.toImmutableList()
                            val keep = previous?.selected?.takeIf { kind -> sections.any { it.kind == kind } }
                            HomeUiState.Content(sections, keep ?: sections.first().kind)
                        }

                        // A failed pull-to-refresh keeps the old content on screen.
                        is Outcome.Failure -> {
                            previous?.copy(refreshing = false, refreshError = result.error)
                                ?: HomeUiState.Error(result.error)
                        }
                    }
            }
        }

        fun refreshErrorShown() {
            _state.update { (it as? HomeUiState.Content)?.copy(refreshError = null) ?: it }
        }

        fun select(kind: ExploreKind) {
            _showFeed.value = false
            _state.update { (it as? HomeUiState.Content)?.copy(selected = kind) ?: it }
        }
    }
