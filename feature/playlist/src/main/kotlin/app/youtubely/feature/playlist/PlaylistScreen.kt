// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.Playlist
import app.youtubely.core.model.PlaylistId
import app.youtubely.core.model.VideoId
import app.youtubely.core.model.bestFor
import app.youtubely.core.ui.DownloadAllButton
import app.youtubely.core.ui.ErrorState
import app.youtubely.core.ui.LoadMoreEffect
import app.youtubely.core.ui.LoadingState
import app.youtubely.core.ui.StateTransition
import app.youtubely.core.ui.VideoRow
import app.youtubely.core.ui.motionItem
import app.youtubely.core.ui.videoFrameKey
import coil3.compose.AsyncImage

private const val COVER_ASPECT = 16f / 9f

/** A playlist: cover, owner, "Play all" and the videos, paged as the list scrolls. */
@Composable
fun PlaylistScreen(
    playlistId: PlaylistId,
    onBack: () -> Unit,
    onPlay: (VideoId, sharedKey: String, PlaylistId) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        hiltViewModel<PlaylistViewModel, PlaylistViewModel.Factory>(key = playlistId.value) {
            it.create(playlistId.value)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val playlist = state.playlist
    val batchSize = (playlist?.streamCount?.toInt()?.takeIf { it > 0 } ?: state.items.size).coerceAtMost(BATCH_LIMIT)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(playlist?.name.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                subtitle = playlist?.let { { Text(subtitle(it), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(YouTubelyIcons.ArrowBack), stringResource(R.string.playlist_back))
                    }
                },
                actions = {
                    if (state.items.isNotEmpty()) DownloadAllButton(batchSize, viewModel::downloadAll)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val pane =
                when {
                    state.error != null -> PlaylistPane.Error
                    playlist == null -> PlaylistPane.Loading
                    else -> PlaylistPane.Content
                }
            StateTransition(pane, label = "playlistPane") {
                when (it) {
                    PlaylistPane.Error -> {
                        state.error?.let { error -> ErrorState(error, onRetry = viewModel::retry) }
                    }

                    PlaylistPane.Loading -> {
                        LoadingState()
                    }

                    PlaylistPane.Content -> {
                        if (playlist != null) {
                            val listState = rememberLazyListState()
                            LoadMoreEffect(listState, viewModel::loadMore)
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 24.dp),
                            ) {
                                item(key = "header") {
                                    Header(
                                        playlist = playlist,
                                        onPlayAll = {
                                            state.items.firstOrNull()?.let {
                                                onPlay(it.id, videoFrameKey("playlist", it.id), playlist.id)
                                            }
                                        },
                                        onOpenChannel = onOpenChannel,
                                        canPlay = state.items.isNotEmpty(),
                                    )
                                }
                                itemsIndexed(state.items, key = { _, item -> item.id.value }) { index, item ->
                                    val key = videoFrameKey("playlist", item.id)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = motionItem(),
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            style = MaterialTheme.typography.labelLargeEmphasized,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.widthIn(min = 32.dp).padding(start = 8.dp),
                                        )
                                        VideoRow(
                                            item = item,
                                            sharedKey = key,
                                            onClick = { onPlay(item.id, key, playlist.id) },
                                            thumbnailWidth = 144.dp,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                if (state.loadingMore) {
                                    item(key = "loading-more") {
                                        Box(
                                            Modifier.fillMaxWidth().padding(24.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            LoadingIndicator()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class PlaylistPane { Error, Loading, Content }

@Composable
private fun subtitle(playlist: Playlist): String =
    listOfNotNull(
        playlist.uploader?.name,
        playlist.streamCount?.let { pluralStringResource(R.plurals.playlist_count, it.toInt(), it) },
    ).joinToString(" · ")

@Composable
private fun Header(
    playlist: Playlist,
    onPlayAll: () -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    canPlay: Boolean,
) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AsyncImage(
            model = playlist.thumbnails.bestFor(minWidth = 720)?.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(COVER_ASPECT)
                    .clip(MaterialTheme.shapes.extraLarge),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPlayAll,
                enabled = canPlay,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.MediumContentPadding,
                modifier = Modifier.weight(1f),
            ) {
                Icon(painterResource(YouTubelyIcons.Play), contentDescription = null, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.playlist_play_all), Modifier.padding(start = 8.dp))
            }
            playlist.uploader?.let { uploader ->
                OutlinedButton(
                    onClick = { onOpenChannel(uploader.id) },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.MediumContentPadding,
                ) {
                    Text(uploader.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private const val BATCH_LIMIT = 300
