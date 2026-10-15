// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.youtubely.core.data.HistoryRepository
import app.youtubely.core.data.PlaylistRepository
import app.youtubely.core.data.WatchLaterRepository
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.download.DownloadManager
import app.youtubely.core.model.Download
import app.youtubely.core.model.DownloadState
import app.youtubely.core.model.Image
import app.youtubely.core.model.LocalPlaylist
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.StreamType
import app.youtubely.core.model.VideoId
import app.youtubely.core.model.matchesQuery
import app.youtubely.core.ui.VideoRow
import app.youtubely.core.ui.motionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What a search of the library found, by where it was found. */
data class LibraryResults(
    val downloads: List<Download> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList(),
    val later: List<StreamItem> = emptyList(),
    val history: List<StreamItem> = emptyList(),
) {
    val isEmpty: Boolean get() = downloads.isEmpty() && playlists.isEmpty() && later.isEmpty() && history.isEmpty()
}

@HiltViewModel
class LibrarySearchViewModel
    @Inject
    constructor(
        history: HistoryRepository,
        watchLater: WatchLaterRepository,
        playlists: PlaylistRepository,
        downloads: DownloadManager,
    ) : ViewModel() {
        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        @OptIn(FlowPreview::class)
        val results: StateFlow<LibraryResults> =
            combine(
                _query.debounce(QUERY_DEBOUNCE_MS),
                history.history,
                watchLater.videos,
                playlists.playlists,
                downloads.downloads,
            ) { query, watched, later, lists, saved ->
                LibraryResults(
                    downloads =
                        saved
                            .filter {
                                it.state == DownloadState.Completed &&
                                    matchesQuery(query, it.title, it.channelName)
                            }.take(LIMIT),
                    playlists = lists.filter { matchesQuery(query, it.name) }.take(LIMIT),
                    later = later.map { it.item }.filter { it.matches(query) }.take(LIMIT),
                    history = watched.map { it.item }.filter { it.matches(query) }.take(LIMIT),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryResults())

        fun setQuery(text: String) {
            _query.value = text
        }

        private fun StreamItem.matches(query: String) = matchesQuery(query, title, channel?.name)

        private companion object {
            const val QUERY_DEBOUNCE_MS = 150L
            const val STOP_TIMEOUT_MS = 5_000L
            const val LIMIT = 30
        }
    }

/** One search over everything kept on the phone; works offline. */
@Composable
fun LibrarySearchScreen(
    onBack: () -> Unit,
    onOpenVideo: (VideoId, sharedKey: String) -> Unit,
    onPlayDownload: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibrarySearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(YouTubelyIcons.ArrowBack), stringResource(R.string.stats_back))
                    }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text(stringResource(R.string.library_search_hint), maxLines = 1) },
                        singleLine = true,
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                actions = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(painterResource(YouTubelyIcons.Close), stringResource(R.string.library_search_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (query.isNotBlank() && results.isEmpty) {
            Box(Modifier.padding(padding)) {
                EmptyPage(YouTubelyIcons.Search.outlined, R.string.library_search_nothing)
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding =
                PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = padding.calculateTopPadding(),
                    bottom = 24.dp,
                ),
            modifier = Modifier.fillMaxSize(),
        ) {
            section(R.string.library_search_downloads, results.downloads, { "d-${it.id}" }) { download ->
                VideoRow(
                    item = download.toItem(),
                    sharedKey = "search-d-${download.id}",
                    onClick = { onPlayDownload(download.id) },
                    modifier = motionItem(),
                )
            }
            section(R.string.library_search_playlists, results.playlists, { "p-${it.id}" }) { playlist ->
                ListItem(
                    onClick = { onOpenPlaylist(playlist.id) },
                    leadingContent = { Icon(painterResource(YouTubelyIcons.PlaylistPlay), contentDescription = null) },
                    supportingContent = {
                        Text(
                            pluralStringResource(
                                R.plurals.library_playlist_count,
                                playlist.itemCount,
                                playlist.itemCount,
                            ),
                        )
                    },
                    modifier = motionItem(),
                ) { Text(playlist.name) }
            }
            videos(R.string.library_search_later, "l", results.later, onOpenVideo)
            videos(R.string.library_search_history, "h", results.history, onOpenVideo)
        }
    }
}

private fun <T> LazyListScope.section(
    title: Int,
    rows: List<T>,
    key: (T) -> String,
    row: @Composable LazyItemScope.(T) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "title-$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).then(motionItem()),
        )
    }
    items(rows, key = key) { row(it) }
}

private fun LazyListScope.videos(
    title: Int,
    origin: String,
    items: List<StreamItem>,
    onOpenVideo: (VideoId, String) -> Unit,
) = section(title, items, { "$origin-${it.id.value}" }) { item ->
    val key = "search-$origin-${item.id.value}"
    VideoRow(item = item, sharedKey = key, onClick = { onOpenVideo(item.id, key) }, modifier = motionItem())
}

private fun Download.toItem() =
    StreamItem(
        id = videoId,
        title = title,
        channel = null,
        thumbnails = listOfNotNull(thumbnailPath?.let { Image("file://$it", null, null) }),
        type = StreamType.Video,
        duration = duration,
        viewCount = null,
        uploadedAt = null,
        uploadedAtText = null,
    )
