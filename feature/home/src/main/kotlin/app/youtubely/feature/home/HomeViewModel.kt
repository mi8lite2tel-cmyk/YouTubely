// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.common.Outcome
import app.youtubely.core.common.outcomeOf
import app.youtubely.core.data.FeedState
import app.youtubely.core.data.HistoryRepository
import app.youtubely.core.data.RecommendationRepository
import app.youtubely.core.data.RecommendationState
import app.youtubely.core.data.SubscriptionFeed
import app.youtubely.core.data.SubscriptionRepository
import app.youtubely.core.datastore.SyncSettingsRepository
import app.youtubely.core.model.ExploreKind
import app.youtubely.core.model.ExploreSection
import app.youtubely.core.model.HistoryEntry
import app.youtubely.core.model.Region
import app.youtubely.core.model.StreamItem
import app.youtubely.core.provider.StreamProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** What the chips of the home screen choose between. */
sealed interface HomeTab {
    data object ForYou : HomeTab

    data object Subscriptions : HomeTab

    data class Explore(
        val kind: ExploreKind,
    ) : HomeTab
}

/** The public charts. They are loaded only when needed and never block the personal tabs. */
sealed interface ExploreUiState {
    data object Loading : ExploreUiState

    data class Error(
        val error: Throwable,
    ) : ExploreUiState

    data class Content(
        val sections: ImmutableList<ExploreSection>,
        val refreshing: Boolean = false,
        /** The last pull-to-refresh failed; the old content stays and a message is shown once. */
        val refreshError: Throwable? = null,
    ) : ExploreUiState {
        fun items(kind: ExploreKind): List<StreamItem> = sections.firstOrNull { it.kind == kind }?.items.orEmpty()
    }
}

/** The subscription feed as shown: filtered by group, with how many videos are new. */
data class FeedUi(
    val state: FeedState,
    val items: ImmutableList<StreamItem>,
    val groups: ImmutableList<String>,
    val group: String?,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val provider: StreamProvider,
        private val feed: SubscriptionFeed,
        private val recommendations: RecommendationRepository,
        subscriptions: SubscriptionRepository,
        private val sync: SyncSettingsRepository,
        history: HistoryRepository,
    ) : ViewModel() {
        private val sharing = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS)

        private val _tab = MutableStateFlow<HomeTab?>(null)

        /** `null` until the start tab is chosen (a moment after launch). */
        val tab: StateFlow<HomeTab?> = _tab.asStateFlow()

        private val _explore = MutableStateFlow<ExploreUiState>(ExploreUiState.Loading)
        val explore: StateFlow<ExploreUiState> = _explore.asStateFlow()

        val forYou: StateFlow<RecommendationState> = recommendations.state

        /** Started videos to pick up where they were left. */
        val continueWatching: StateFlow<ImmutableList<HistoryEntry>> =
            history.continueWatching
                .map { it.toImmutableList() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

        private val group = MutableStateFlow<String?>(null)

        val feedUi: StateFlow<FeedUi> =
            combine(feed.state, subscriptions.details, group) { state, details, selected ->
                val groups = details.mapNotNull { it.group }.distinct().sorted()
                val current = selected?.takeIf { it in groups }
                val members = details.filter { current == null || it.group == current }.map { it.channel.id }.toSet()
                FeedUi(
                    state = state,
                    items = state.items.filter { it.channel?.id in members }.toImmutableList(),
                    groups = groups.toImmutableList(),
                    group = current,
                )
            }.stateIn(
                viewModelScope,
                sharing,
                FeedUi(FeedState(), persistentListOf(), persistentListOf(), null),
            )

        /** Videos found since the feed was last looked at, for the badge on the chip. */
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val newVideos: StateFlow<Int> =
            sync.feedViewedAt
                .flatMapLatest { since -> feed.newCountSince(since) }
                .stateIn(viewModelScope, sharing, 0)

        val hasSubscriptions: StateFlow<Boolean> =
            subscriptions.subscriptions
                .map { it.isNotEmpty() }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

        private val region =
            Locale
                .getDefault()
                .country
                .takeIf { it.length == 2 && it.all(Char::isUpperCase) }
                ?.let(::Region) ?: Region("US")

        init {
            viewModelScope.launch {
                // Start on "For you" when there is anything to base it on, otherwise on the charts.
                val personal = recommendations.hasSignals.first()
                _tab.value = if (personal) HomeTab.ForYou else HomeTab.Explore(ExploreKind.Trending)
                if (personal) recommendations.refreshIfStale()
            }
            loadExplore()
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

        fun select(tab: HomeTab) {
            val previous = _tab.value
            _tab.value = tab
            if (previous == HomeTab.Subscriptions && tab != HomeTab.Subscriptions) markFeedViewed()
            if (tab == HomeTab.ForYou) viewModelScope.launch { recommendations.refreshIfStale() }
        }

        fun selectGroup(name: String?) {
            group.value = name
        }

        /** The feed counts as seen once it has been on screen for a moment. */
        fun markFeedViewed() {
            viewModelScope.launch { sync.markFeedViewed(System.currentTimeMillis()) }
        }

        fun refreshFeed() {
            viewModelScope.launch { feed.refresh() }
        }

        fun refreshForYou() {
            viewModelScope.launch {
                feed.refresh()
                recommendations.refresh()
            }
        }

        /** Charts are fetched live each time: they change through the day. */
        fun loadExplore(refresh: Boolean = false) {
            val previous = _explore.value as? ExploreUiState.Content
            _explore.value =
                if (refresh &&
                    previous != null
                ) {
                    previous.copy(refreshing = true, refreshError = null)
                } else {
                    ExploreUiState.Loading
                }
            viewModelScope.launch {
                _explore.value =
                    when (val result = outcomeOf { provider.explore(region) }) {
                        is Outcome.Success -> {
                            ExploreUiState.Content(result.value.toImmutableList())
                        }

                        // A failed pull-to-refresh keeps the old content on screen.
                        is Outcome.Failure -> {
                            previous?.copy(refreshing = false, refreshError = result.error)
                                ?: ExploreUiState.Error(result.error)
                        }
                    }
            }
        }

        fun refreshErrorShown() {
            _explore.update { (it as? ExploreUiState.Content)?.copy(refreshError = null) ?: it }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
