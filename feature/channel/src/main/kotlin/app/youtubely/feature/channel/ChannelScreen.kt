// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.channel

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.Channel
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelTab
import app.youtubely.core.model.PlaylistId
import app.youtubely.core.model.SearchItem
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.VideoId
import app.youtubely.core.model.bestFor
import app.youtubely.core.ui.ChannelAvatar
import app.youtubely.core.ui.ChannelListItem
import app.youtubely.core.ui.ErrorState
import app.youtubely.core.ui.LoadMoreEffect
import app.youtubely.core.ui.LoadingState
import app.youtubely.core.ui.PlaylistListItem
import app.youtubely.core.ui.StateTransition
import app.youtubely.core.ui.SubscribeButton
import app.youtubely.core.ui.VerifiedBadge
import app.youtubely.core.ui.VideoRow
import app.youtubely.core.ui.motionItem
import app.youtubely.core.ui.subscriberCountText
import app.youtubely.core.ui.videoFrameKey
import app.youtubely.core.ui.viewCountText
import coil3.compose.AsyncImage

private const val BANNER_ASPECT = 16f / 3f
private const val SHORT_ASPECT = 9f / 16f
private const val COLLAPSED_DESCRIPTION_LINES = 2

/**
 * A channel page: banner, avatar, local subscribe button and description, then the videos,
 * shorts, live streams and playlists tabs, each paged as the list scrolls.
 */
@Composable
fun ChannelScreen(
    channelId: ChannelId,
    onBack: () -> Unit,
    onOpenVideo: (VideoId, sharedKey: String) -> Unit,
    onOpenChannel: (ChannelId) -> Unit,
    onOpenPlaylist: (PlaylistId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        hiltViewModel<ChannelViewModel, ChannelViewModel.Factory>(key = channelId.value) {
            it.create(channelId.value)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subscribed by viewModel.isSubscribed.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val channel = state.channel

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(channel?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = channel?.subscriberCount?.let { { Text(subscriberCountText(it)) } },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(YouTubelyIcons.ArrowBack), stringResource(R.string.channel_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val pane =
                when {
                    state.error != null -> ChannelPane.Error
                    channel == null -> ChannelPane.Loading
                    else -> ChannelPane.Content
                }
            StateTransition(pane, label = "channelPane") {
                when (it) {
                    ChannelPane.Error -> {
                        state.error?.let { error -> ErrorState(error, onRetry = viewModel::retry) }
                    }

                    ChannelPane.Loading -> {
                        LoadingState()
                    }

                    ChannelPane.Content -> {
                        if (channel != null) {
                            ChannelContent(
                                state = state,
                                channel = channel,
                                subscribed = subscribed,
                                actions =
                                    ChannelActions(
                                        onSelectTab = viewModel::selectTab,
                                        onToggleSubscription = viewModel::toggleSubscription,
                                        onRetry = viewModel::retry,
                                        onLoadMore = viewModel::loadMore,
                                        navigation = ChannelNavigation(onOpenVideo, onOpenChannel, onOpenPlaylist),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class ChannelPane { Error, Loading, Content }

private class ChannelActions(
    val onSelectTab: (ChannelTab) -> Unit,
    val onToggleSubscription: () -> Unit,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val navigation: ChannelNavigation,
)

private class ChannelNavigation(
    val onOpenVideo: (VideoId, String) -> Unit,
    val onOpenChannel: (ChannelId) -> Unit,
    val onOpenPlaylist: (PlaylistId) -> Unit,
)

@Composable
private fun ChannelContent(
    state: ChannelUiState,
    channel: Channel,
    subscribed: Boolean,
    actions: ChannelActions,
) {
    val gridState = rememberLazyGridState()
    LoadMoreEffect(gridState, actions.onLoadMore)
    val shorts = state.tab == ChannelTab.Shorts
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(if (shorts) 140.dp else 360.dp),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Header(channel, subscribed, actions.onToggleSubscription)
        }
        if (channel.tabs.size > 1) {
            item(key = "tabs", span = { GridItemSpan(maxLineSpan) }) {
                PrimaryTabRow(selectedTabIndex = channel.tabs.indexOf(state.tab).coerceAtLeast(0)) {
                    channel.tabs.forEach { tab ->
                        Tab(
                            selected = tab == state.tab,
                            onClick = { actions.onSelectTab(tab) },
                            text = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        }
        tabContent(state, actions)
    }
}

private fun LazyGridScope.tabContent(
    state: ChannelUiState,
    actions: ChannelActions,
) {
    val content = state.content
    when {
        content.loading -> {
            item(key = "loading", span = { GridItemSpan(maxLineSpan) }) { CenteredLoading() }
        }

        content.error != null -> {
            item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) { ErrorState(content.error, onRetry = actions.onRetry) }
            }
        }

        content.items.isEmpty() -> {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.channel_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }

        else -> {
            items(content.items, key = { "${state.tab}-${it.key}" }) { item ->
                ChannelItem(item, state.tab, actions, motionItem())
            }
            if (content.loadingMore) {
                item(key = "loading-more", span = { GridItemSpan(maxLineSpan) }) { CenteredLoading() }
            }
        }
    }
}

@Composable
private fun ChannelItem(
    item: SearchItem,
    tab: ChannelTab,
    actions: ChannelActions,
    modifier: Modifier,
) {
    when (item) {
        is SearchItem.Stream -> {
            val key = videoFrameKey("channel-$tab", item.item.id)
            if (tab == ChannelTab.Shorts) {
                ShortCard(
                    item.item,
                    onClick = { actions.navigation.onOpenVideo(item.item.id, key) },
                    modifier = modifier,
                )
            } else {
                VideoRow(
                    item = item.item,
                    sharedKey = key,
                    onClick = { actions.navigation.onOpenVideo(item.item.id, key) },
                    modifier = modifier,
                )
            }
        }

        is SearchItem.PlaylistResult -> {
            PlaylistListItem(
                item.playlist,
                onClick = { actions.navigation.onOpenPlaylist(item.playlist.id) },
                modifier = modifier,
            )
        }

        is SearchItem.ChannelResult -> {
            ChannelListItem(
                item.channel,
                onClick = { actions.navigation.onOpenChannel(item.channel.id) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun Header(
    channel: Channel,
    subscribed: Boolean,
    onToggleSubscription: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        channel.banners.bestFor(minWidth = 1080)?.let { banner ->
            AsyncImage(
                model = banner.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(BANNER_ASPECT)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChannelAvatar(channel.avatars, contentDescription = null, size = 72.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (channel.isVerified) VerifiedBadge()
                }
                SubscribeButton(subscribed = subscribed, onClick = onToggleSubscription)
            }
        }
        if (channel.description.isNotBlank()) Description(channel.description)
    }
}

@Composable
private fun Description(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable { expanded = !expanded }
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_DESCRIPTION_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** A short in a vertical 9:16 card. */
@Composable
private fun ShortCard(
    item: StreamItem,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Box {
            AsyncImage(
                model = item.thumbnails.bestFor(minWidth = 360)?.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(SHORT_ASPECT)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                    .padding(8.dp),
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.viewCount?.let {
                    Text(
                        viewCountText(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredLoading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
}

private val SearchItem.key: String
    get() =
        when (this) {
            is SearchItem.Stream -> item.id.value
            is SearchItem.ChannelResult -> channel.id.value
            is SearchItem.PlaylistResult -> playlist.id.value
        }

private val ChannelTab.label: Int
    get() =
        when (this) {
            ChannelTab.Videos -> R.string.channel_tab_videos
            ChannelTab.Shorts -> R.string.channel_tab_shorts
            ChannelTab.Live -> R.string.channel_tab_live
            ChannelTab.Playlists -> R.string.channel_tab_playlists
        }
