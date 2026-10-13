// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youtubely.core.data.FeedState
import app.youtubely.core.designsystem.component.ExpressiveFilterChip
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.ExploreKind
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.VideoId
import app.youtubely.core.model.bestFor
import app.youtubely.core.ui.ErrorState
import app.youtubely.core.ui.LoadingState
import app.youtubely.core.ui.VideoCard
import app.youtubely.core.ui.sharedVideoFrame
import app.youtubely.core.ui.videoFrameKey
import app.youtubely.core.ui.videoMetadataText
import coil3.compose.AsyncImage

private const val HERO_COUNT = 6
private const val HERO_ORIGIN = "home-hero"
private const val LIST_ORIGIN = "home-list"
private const val FEED_ORIGIN = "home-feed"

@Composable
fun HomeScreen(
    onOpenVideo: (VideoId, sharedKey: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val feed by viewModel.feedState.collectAsStateWithLifecycle()
    val hasSubscriptions by viewModel.hasSubscriptions.collectAsStateWithLifecycle()
    val showFeed by viewModel.showFeed.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val refreshFailed = stringResource(R.string.home_refresh_failed)
    val refreshError = (state as? HomeUiState.Content)?.refreshError
    LaunchedEffect(refreshError) {
        if (refreshError != null) {
            snackbar.showSnackbar(refreshFailed)
            viewModel.refreshErrorShown()
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.home_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when (val s = state) {
            HomeUiState.Loading -> {
                LoadingState(Modifier.padding(padding))
            }

            is HomeUiState.Error -> {
                ErrorState(s.error, onRetry = { viewModel.load() }, Modifier.padding(padding))
            }

            is HomeUiState.Content -> {
                HomeContent(
                    state = s,
                    feed = if (hasSubscriptions && showFeed) feed else null,
                    hasSubscriptions = hasSubscriptions,
                    contentPadding = padding,
                    onRefresh = {
                        if (hasSubscriptions &&
                            showFeed
                        ) {
                            viewModel.refreshFeed()
                        } else {
                            viewModel.load(refresh = true)
                        }
                    },
                    onSelect = viewModel::select,
                    onSelectFeed = viewModel::showFeed,
                    onOpenVideo = onOpenVideo,
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Content,
    feed: FeedState?,
    hasSubscriptions: Boolean,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onSelect: (ExploreKind) -> Unit,
    onSelectFeed: () -> Unit,
    onOpenVideo: (VideoId, String) -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    val refreshing = if (feed != null) feed.loading && feed.items.isNotEmpty() else state.refreshing
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        val items = feed?.items ?: state.current.items
        val hero = if (feed != null) emptyList() else items.take(HERO_COUNT)
        val rest = if (feed != null) items else items.drop(HERO_COUNT)
        val origin = if (feed != null) FEED_ORIGIN else LIST_ORIGIN
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            contentPadding =
                PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
                SectionChips(state, feedSelected = feed != null, hasSubscriptions, onSelect, onSelectFeed)
            }
            if (feed != null && feed.items.isEmpty()) {
                item(key = "feed-status", span = { GridItemSpan(maxLineSpan) }) {
                    FeedStatus(feed, onRetry = onRefresh)
                }
            }
            if (hero.isNotEmpty()) {
                item(key = "hero-${state.selected}", span = { GridItemSpan(maxLineSpan) }) {
                    HeroCarousel(hero, onOpenVideo, Modifier.animateItem())
                }
            }
            items(rest, key = { "$origin-${state.selected}-${it.id.value}" }) { item ->
                VideoCard(
                    item = item,
                    sharedKey = videoFrameKey(origin, item.id),
                    onClick = { onOpenVideo(item.id, videoFrameKey(origin, item.id)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** Loading, error or "nothing new" for the subscription feed while it has no items. */
@Composable
private fun FeedStatus(
    feed: FeedState,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        when {
            feed.loading || feed.updatedAt == null -> {
                LoadingState()
            }

            feed.error != null -> {
                ErrorState(feed.error!!, onRetry = onRetry)
            }

            else -> {
                Text(
                    stringResource(R.string.home_feed_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionChips(
    state: HomeUiState.Content,
    feedSelected: Boolean,
    hasSubscriptions: Boolean,
    onSelect: (ExploreKind) -> Unit,
    onSelectFeed: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (hasSubscriptions) {
            item(key = "subscriptions") {
                ExpressiveFilterChip(
                    selected = feedSelected,
                    onClick = onSelectFeed,
                    label = stringResource(R.string.home_subscriptions),
                    icon = YouTubelyIcons.Subscriptions.outlined,
                )
            }
        }
        items(state.sections, key = { it.kind }) { section ->
            ExpressiveFilterChip(
                selected = !feedSelected && section.kind == state.selected,
                onClick = { onSelect(section.kind) },
                label = stringResource(section.kind.label),
                icon = section.kind.icon,
            )
        }
    }
}

@Composable
private fun HeroCarousel(
    items: List<StreamItem>,
    onOpenVideo: (VideoId, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    HorizontalCenteredHeroCarousel(
        state = rememberCarouselState { items.size },
        modifier = modifier.fillMaxWidth().height(240.dp).padding(vertical = 8.dp),
        itemSpacing = 8.dp,
    ) { index ->
        val item = items[index]
        val key = videoFrameKey(HERO_ORIGIN, item.id)
        Box(
            Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.extraLarge)
                .clickable { onOpenVideo(item.id, key) },
        ) {
            AsyncImage(
                model = item.thumbnails.bestFor(1280)?.url,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().sharedVideoFrame(key),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(16.dp),
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    videoMetadataText(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val ExploreKind.label: Int
    get() =
        when (this) {
            ExploreKind.Trending -> R.string.explore_trending
            ExploreKind.Music -> R.string.explore_music
            ExploreKind.Gaming -> R.string.explore_gaming
            ExploreKind.Live -> R.string.explore_live
            ExploreKind.Podcasts -> R.string.explore_podcasts
            ExploreKind.MoviesAndShows -> R.string.explore_movies
        }

private val ExploreKind.icon: Int
    get() =
        when (this) {
            ExploreKind.Trending -> YouTubelyIcons.Trending
            ExploreKind.Music -> YouTubelyIcons.Music
            ExploreKind.Gaming -> YouTubelyIcons.Gaming
            ExploreKind.Live -> YouTubelyIcons.Live
            ExploreKind.Podcasts -> YouTubelyIcons.Podcasts
            ExploreKind.MoviesAndShows -> YouTubelyIcons.Movie
        }
