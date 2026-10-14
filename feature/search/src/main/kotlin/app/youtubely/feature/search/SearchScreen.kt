// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youtubely.core.designsystem.component.ExpressiveFilterChip
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.PlaylistId
import app.youtubely.core.model.SearchFilter
import app.youtubely.core.model.SearchItem
import app.youtubely.core.model.VideoId
import app.youtubely.core.ui.ChannelListItem
import app.youtubely.core.ui.ErrorState
import app.youtubely.core.ui.LoadingState
import app.youtubely.core.ui.PlaylistListItem
import app.youtubely.core.ui.StateTransition
import app.youtubely.core.ui.VideoRow
import app.youtubely.core.ui.subscriberCountText
import app.youtubely.core.ui.videoFrameKey
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private const val ORIGIN = "search"
private const val LOAD_MORE_THRESHOLD = 5

@Composable
fun SearchScreen(
    onOpenVideo: (VideoId, sharedKey: String) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    onOpenPlaylist: (PlaylistId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val textState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }.collectLatest(viewModel::onQueryTyped)
    }

    fun submit(query: String) {
        textState.setTextAndPlaceCursorAtEnd(query)
        viewModel.search(query)
        scope.launch { searchBarState.animateToCollapsed() }
    }

    val inputField = @Composable {
        SearchInputField(textState, searchBarState, onSearch = ::submit)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AppBarWithSearch(state = searchBarState, inputField = inputField)
            ExpandedFullScreenContainedSearchBar(state = searchBarState, inputField = inputField) {
                SuggestionList(
                    suggestions = suggestions,
                    onPick = ::submit,
                    onFill = { textState.setTextAndPlaceCursorAtEnd("$it ") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilterRow(results.filter, onChange = viewModel::setFilter)
            val pane =
                when {
                    results.isIdle -> SearchPane.Idle
                    results.loading -> SearchPane.Loading
                    results.error != null -> SearchPane.Error
                    results.items.isEmpty() -> SearchPane.Empty
                    else -> SearchPane.Results
                }
            StateTransition(pane, label = "searchPane") {
                when (it) {
                    SearchPane.Idle -> {
                        IdleState()
                    }

                    SearchPane.Loading -> {
                        LoadingState()
                    }

                    SearchPane.Error -> {
                        results.error?.let { error -> ErrorState(error, onRetry = viewModel::retry) }
                    }

                    SearchPane.Empty -> {
                        EmptyState(results.query)
                    }

                    SearchPane.Results -> {
                        ResultList(
                            results,
                            onOpenVideo,
                            onOpenChannel,
                            onOpenPlaylist,
                            viewModel::loadMore,
                        )
                    }
                }
            }
        }
    }
}

private enum class SearchPane { Idle, Loading, Error, Empty, Results }

@Composable
private fun SearchInputField(
    textState: TextFieldState,
    searchBarState: SearchBarState,
    onSearch: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val expanded = searchBarState.currentValue == SearchBarValue.Expanded
    SearchBarDefaults.InputField(
        textFieldState = textState,
        searchBarState = searchBarState,
        onSearch = onSearch,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = {
            if (expanded) {
                IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                    Icon(painterResource(YouTubelyIcons.ArrowBack), stringResource(R.string.search_close))
                }
            } else {
                Icon(painterResource(YouTubelyIcons.Search.outlined), contentDescription = null)
            }
        },
        trailingIcon = {
            if (textState.text.isNotEmpty()) {
                IconButton(onClick = { textState.clearText() }) {
                    Icon(painterResource(YouTubelyIcons.Close), stringResource(R.string.search_clear))
                }
            }
        },
    )
}

@Composable
private fun SuggestionList(
    suggestions: List<String>,
    onPick: (String) -> Unit,
    onFill: (String) -> Unit,
) {
    LazyColumn {
        items(suggestions, key = { it }) { suggestion ->
            ListItem(
                onClick = { onPick(suggestion) },
                leadingContent = { Icon(painterResource(YouTubelyIcons.Search.outlined), contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { onFill(suggestion) }) {
                        Icon(
                            painterResource(YouTubelyIcons.NorthWest),
                            stringResource(R.string.search_fill_suggestion, suggestion),
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun FilterRow(
    filter: SearchFilter,
    onChange: (SearchFilter) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(SearchFilter.Type.entries) { type ->
            ExpressiveFilterChip(
                selected = filter.type == type,
                onClick = { onChange(filter.copy(type = type)) },
                label = stringResource(type.label),
            )
        }
        items(SearchFilter.DurationFilter.entries.filter { it != SearchFilter.DurationFilter.Any }) { duration ->
            ExpressiveFilterChip(
                selected = filter.duration == duration,
                onClick = {
                    val next = if (filter.duration == duration) SearchFilter.DurationFilter.Any else duration
                    onChange(filter.copy(duration = next))
                },
                label = stringResource(duration.label),
                icon = YouTubelyIcons.Schedule,
            )
        }
    }
}

@Composable
private fun ResultList(
    results: SearchResults,
    onOpenVideo: (VideoId, String) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    onOpenPlaylist: (PlaylistId) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, results.items.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: 0
        }.distinctUntilChanged()
            .filter { it >= results.items.size - LOAD_MORE_THRESHOLD }
            .collectLatest { onLoadMore() }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(results.items, key = ::itemKey) { item ->
            when (item) {
                is SearchItem.Stream -> {
                    val key = videoFrameKey(ORIGIN, item.item.id)
                    VideoRow(
                        item = item.item,
                        sharedKey = key,
                        onClick = { onOpenVideo(item.item.id, key) },
                        modifier = Modifier.animateItem(),
                    )
                }

                is SearchItem.ChannelResult -> {
                    ChannelListItem(
                        channel = item.channel,
                        onClick = { onOpenChannel(item.channel.id) },
                        supporting = item.subscriberCount?.let { subscriberCountText(it) },
                        modifier = Modifier.animateItem(),
                    )
                }

                is SearchItem.PlaylistResult -> {
                    PlaylistListItem(
                        playlist = item.playlist,
                        onClick = { onOpenPlaylist(item.playlist.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        if (results.loadingMore) {
            item(key = "loading-more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        }
    }
}

@Composable
private fun IdleState() {
    CenteredMessage(icon = YouTubelyIcons.Search.filled, text = stringResource(R.string.search_idle))
}

@Composable
private fun EmptyState(query: String) {
    CenteredMessage(icon = YouTubelyIcons.Info, text = stringResource(R.string.search_empty, query))
}

@Composable
private fun CenteredMessage(
    icon: Int,
    text: String,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                shape = MaterialShapes.Clover4Leaf.toShape(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(104.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(44.dp))
                }
            }
            Text(text, style = MaterialTheme.typography.titleMediumEmphasized, textAlign = TextAlign.Center)
        }
    }
}

private val SearchFilter.Type.label: Int
    get() =
        when (this) {
            SearchFilter.Type.All -> R.string.filter_all
            SearchFilter.Type.Videos -> R.string.filter_videos
            SearchFilter.Type.Channels -> R.string.filter_channels
            SearchFilter.Type.Playlists -> R.string.filter_playlists
        }

private val SearchFilter.DurationFilter.label: Int
    get() =
        when (this) {
            SearchFilter.DurationFilter.Any -> R.string.filter_all
            SearchFilter.DurationFilter.Short -> R.string.filter_short
            SearchFilter.DurationFilter.Medium -> R.string.filter_medium
            SearchFilter.DurationFilter.Long -> R.string.filter_long
        }
