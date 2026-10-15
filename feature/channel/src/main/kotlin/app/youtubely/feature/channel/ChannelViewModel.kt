// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.common.Outcome
import app.youtubely.core.common.outcomeOf
import app.youtubely.core.data.SubscriptionRepository
import app.youtubely.core.model.Channel
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.ChannelTab
import app.youtubely.core.model.Page
import app.youtubely.core.model.SearchItem
import app.youtubely.core.provider.StreamProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One tab of a channel, loaded page by page. */
data class TabContent(
    val items: List<SearchItem> = emptyList(),
    val nextPage: Page? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    /** The next page failed; no new attempt starts until the user asks. */
    val loadMoreFailed: Boolean = false,
    val error: Throwable? = null,
)

data class ChannelUiState(
    /** `null` until the first page arrives. */
    val channel: Channel? = null,
    val tab: ChannelTab = ChannelTab.Videos,
    val tabs: Map<ChannelTab, TabContent> = emptyMap(),
    /** The channel itself could not be loaded. */
    val error: Throwable? = null,
) {
    val content: TabContent get() = tabs[tab] ?: TabContent()
}

@HiltViewModel(assistedFactory = ChannelViewModel.Factory::class)
class ChannelViewModel
    @AssistedInject
    constructor(
        @Assisted channelId: String,
        private val provider: StreamProvider,
        private val subscriptions: SubscriptionRepository,
    ) : ViewModel() {
        private val id = ChannelId(channelId)

        private val _state = MutableStateFlow(ChannelUiState())
        val state: StateFlow<ChannelUiState> = _state.asStateFlow()

        val isSubscribed: StateFlow<Boolean> =
            subscriptions
                .isSubscribed(id)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

        init {
            load(ChannelTab.Videos)
        }

        fun selectTab(tab: ChannelTab) {
            _state.update { it.copy(tab = tab) }
            val content = _state.value.tabs[tab]
            if (content == null || content.error != null) load(tab)
        }

        fun retry() = load(_state.value.tab)

        fun retryLoadMore() {
            setTab(_state.value.tab) { it.copy(loadMoreFailed = false) }
            loadMore()
        }

        fun loadMore() {
            val tab = _state.value.tab
            val content = _state.value.content
            val next = content.nextPage ?: return
            if (content.loading || content.loadingMore || content.loadMoreFailed) return
            setTab(tab) { it.copy(loadingMore = true) }
            viewModelScope.launch {
                when (val result = outcomeOf { provider.channel(id, tab, next) }) {
                    is Outcome.Success -> {
                        setTab(tab) {
                            it.copy(
                                items = (it.items + result.value.items.items).distinctBy(::key),
                                nextPage = result.value.items.nextPage,
                                loadingMore = false,
                            )
                        }
                    }

                    is Outcome.Failure -> {
                        // Keep what is shown; the footer offers a retry.
                        setTab(tab) { it.copy(loadingMore = false, loadMoreFailed = true) }
                    }
                }
            }
        }

        fun toggleSubscription() {
            val channel = _state.value.channel ?: return
            viewModelScope.launch {
                if (isSubscribed.value) {
                    subscriptions.unsubscribe(id)
                } else {
                    subscriptions.subscribe(ChannelRef(channel.id, channel.name, channel.avatars, channel.isVerified))
                }
            }
        }

        private fun load(tab: ChannelTab) {
            setTab(tab) { TabContent(loading = true) }
            _state.update { it.copy(error = null) }
            viewModelScope.launch {
                when (val result = outcomeOf { provider.channel(id, tab, null) }) {
                    is Outcome.Success -> {
                        val page = result.value
                        _state.update { state ->
                            // A channel without a videos tab opens on its first available tab.
                            val first = state.channel == null && tab !in page.channel.tabs
                            val fallback = page.channel.tabs.firstOrNull()
                            state
                                .copy(
                                    channel = page.channel,
                                    tabs =
                                        state.tabs +
                                            (
                                                tab to
                                                    TabContent(
                                                        items = page.items.items.distinctBy(::key),
                                                        nextPage = page.items.nextPage,
                                                        loading = false,
                                                    )
                                            ),
                                ).let { if (first && fallback != null) it.copy(tab = fallback) else it }
                        }
                        if (_state.value.tab != tab) selectTab(_state.value.tab)
                    }

                    is Outcome.Failure -> {
                        if (_state.value.channel == null) {
                            _state.update { it.copy(error = result.error) }
                        } else {
                            setTab(tab) { TabContent(loading = false, error = result.error) }
                        }
                    }
                }
            }
        }

        private fun setTab(
            tab: ChannelTab,
            change: (TabContent) -> TabContent,
        ) = _state.update { it.copy(tabs = it.tabs + (tab to change(it.tabs[tab] ?: TabContent()))) }

        private fun key(item: SearchItem): String =
            when (item) {
                is SearchItem.Stream -> item.item.id.value
                is SearchItem.ChannelResult -> item.channel.id.value
                is SearchItem.PlaylistResult -> item.playlist.id.value
            }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }

        @AssistedFactory
        interface Factory {
            fun create(channelId: String): ChannelViewModel
        }
    }
