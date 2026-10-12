// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.common.Outcome
import app.youtubely.core.common.outcomeOf
import app.youtubely.core.model.Page
import app.youtubely.core.model.SearchFilter
import app.youtubely.core.model.SearchItem
import app.youtubely.core.provider.StreamProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchResults(
    val query: String = "",
    val filter: SearchFilter = SearchFilter(),
    val items: ImmutableList<SearchItem> = persistentListOf(),
    val nextPage: Page? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: Throwable? = null,
) {
    val isIdle: Boolean get() = query.isBlank()
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val provider: StreamProvider,
    ) : ViewModel() {
        private val typed = MutableStateFlow("")

        /** Suggestions follow typing with a short debounce; failures just show none. */
        val suggestions: StateFlow<ImmutableList<String>> =
            typed
                .debounce(SUGGEST_DEBOUNCE_MS)
                .distinctUntilChanged()
                .mapLatest { query ->
                    (outcomeOf { provider.suggestions(query) } as? Outcome.Success)?.value.orEmpty().toImmutableList()
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

        private val _results = MutableStateFlow(SearchResults())
        val results: StateFlow<SearchResults> = _results.asStateFlow()

        private var searchJob: Job? = null

        fun onQueryTyped(text: String) {
            typed.value = text
        }

        fun search(
            query: String,
            filter: SearchFilter = _results.value.filter,
        ) {
            if (query.isBlank()) return
            searchJob?.cancel()
            _results.value = SearchResults(query = query.trim(), filter = filter, loading = true)
            searchJob =
                viewModelScope.launch {
                    val result = outcomeOf { provider.search(query.trim(), filter, null) }
                    _results.update { current ->
                        when (result) {
                            is Outcome.Success -> {
                                current.copy(
                                    items = result.value.items.toImmutableList(),
                                    nextPage = result.value.nextPage,
                                    loading = false,
                                )
                            }

                            is Outcome.Failure -> {
                                current.copy(loading = false, error = result.error)
                            }
                        }
                    }
                }
        }

        fun setFilter(filter: SearchFilter) {
            val current = _results.value
            if (filter == current.filter) return
            if (current.isIdle) _results.value = current.copy(filter = filter) else search(current.query, filter)
        }

        fun retry() = search(_results.value.query)

        /** Called when the list nears its end; ignored while a page is loading or at the last page. */
        fun loadMore() {
            val current = _results.value
            val page = current.nextPage ?: return
            if (current.loading || current.loadingMore) return
            _results.value = current.copy(loadingMore = true)
            searchJob =
                viewModelScope.launch {
                    val result = outcomeOf { provider.search(current.query, current.filter, page) }
                    _results.update { latest ->
                        if (latest.query != current.query || latest.filter != current.filter) return@update latest
                        when (result) {
                            is Outcome.Success -> {
                                latest.copy(
                                    items = (latest.items + result.value.items).distinctBy(::itemKey).toImmutableList(),
                                    nextPage = result.value.nextPage,
                                    loadingMore = false,
                                )
                            }

                            // Keep what we have; the next scroll retries.
                            is Outcome.Failure -> {
                                latest.copy(loadingMore = false)
                            }
                        }
                    }
                }
        }

        private companion object {
            const val SUGGEST_DEBOUNCE_MS = 200L
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

internal fun itemKey(item: SearchItem): String =
    when (item) {
        is SearchItem.Stream -> "v:${item.item.id.value}"
        is SearchItem.ChannelResult -> "c:${item.channel.id.value}"
        is SearchItem.PlaylistResult -> "p:${item.playlist.id.value}"
    }
