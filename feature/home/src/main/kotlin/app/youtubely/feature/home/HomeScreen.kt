// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youtubely.core.data.FeedState
import app.youtubely.core.data.Reason
import app.youtubely.core.data.RecommendationState
import app.youtubely.core.designsystem.component.ExpressiveFilterChip
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.ExploreKind
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.VideoId
import app.youtubely.core.model.bestFor
import app.youtubely.core.ui.ErrorState
import app.youtubely.core.ui.LoadingState
import app.youtubely.core.ui.VideoCard
import app.youtubely.core.ui.motionItem
import app.youtubely.core.ui.sharedVideoFrame
import app.youtubely.core.ui.videoFrameKey
import app.youtubely.core.ui.videoMetadataText
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

private const val HERO_COUNT = 6
private const val HERO_ORIGIN = "home-hero"
private const val LIST_ORIGIN = "home-list"
private const val FEED_ORIGIN = "home-feed"
private const val FOR_YOU_ORIGIN = "home-for-you"
private const val FEED_SEEN_DELAY_MS = 2_000L

@Composable
fun HomeScreen(
    onOpenVideo: (VideoId, sharedKey: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val explore by viewModel.explore.collectAsStateWithLifecycle()
    val forYou by viewModel.forYou.collectAsStateWithLifecycle()
    val feed by viewModel.feedUi.collectAsStateWithLifecycle()
    val newVideos by viewModel.newVideos.collectAsStateWithLifecycle()
    val hasSubscriptions by viewModel.hasSubscriptions.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val refreshFailed = stringResource(R.string.home_refresh_failed)
    val refreshError = (explore as? ExploreUiState.Content)?.refreshError
    LaunchedEffect(refreshError) {
        if (refreshError != null) {
            snackbar.showSnackbar(refreshFailed)
            viewModel.refreshErrorShown()
        }
    }
    val motion = MaterialTheme.motionScheme

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
        val current = tab
        if (current == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            SectionChips(current, hasSubscriptions, newVideos, viewModel::select)
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    // Content of one chip fades into the next one, with a short rise like a shared axis.
                    (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 24 })
                        .togetherWith(fadeOut(motion.fastEffectsSpec()))
                },
                label = "homeTab",
                modifier = Modifier.fillMaxSize(),
            ) { target ->
                val bottom = padding.calculateBottomPadding()
                when (target) {
                    HomeTab.ForYou -> {
                        ForYouTab(
                            state = forYou,
                            bottomPadding = bottom,
                            onRefresh = viewModel::refreshForYou,
                            onShowTrends = { viewModel.select(HomeTab.Explore(ExploreKind.Trending)) },
                            onOpenVideo = onOpenVideo,
                        )
                    }

                    HomeTab.Subscriptions -> {
                        SubscriptionsTab(
                            feed = feed,
                            bottomPadding = bottom,
                            onRefresh = viewModel::refreshFeed,
                            onSelectGroup = viewModel::selectGroup,
                            onSeen = viewModel::markFeedViewed,
                            onOpenVideo = onOpenVideo,
                        )
                    }

                    is HomeTab.Explore -> {
                        ExploreTab(
                            kind = target.kind,
                            state = explore,
                            bottomPadding = bottom,
                            onRefresh = { viewModel.loadExplore(refresh = true) },
                            onRetry = { viewModel.loadExplore() },
                            onOpenVideo = onOpenVideo,
                        )
                    }
                }
            }
        }
    }
}

/** A grid that fills the width with cards; [header] items come first. */
@Composable
private fun VideoGrid(
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun Refreshable(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) { content() }
}

/** Videos picked on this phone from the history and the subscriptions. */
@Composable
private fun ForYouTab(
    state: RecommendationState,
    bottomPadding: Dp,
    onRefresh: () -> Unit,
    onShowTrends: () -> Unit,
    onOpenVideo: (VideoId, String) -> Unit,
) {
    when (state) {
        RecommendationState.Loading -> {
            LoadingState()
        }

        RecommendationState.NeedsData -> {
            ForYouEmpty(onShowTrends)
        }

        is RecommendationState.Failed -> {
            ErrorState(state.error, onRetry = onRefresh)
        }

        is RecommendationState.Ready -> {
            Refreshable(refreshing = false, onRefresh = onRefresh) {
                VideoGrid(bottomPadding) {
                    item(key = "for-you-note", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.home_for_you_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).then(motionItem()),
                        )
                    }
                    items(state.items, key = { "$FOR_YOU_ORIGIN-${it.item.id.value}" }) { recommendation ->
                        Column(motionItem()) {
                            Text(
                                reasonText(recommendation.reason),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            val item = recommendation.item
                            VideoCard(
                                item = item,
                                sharedKey = videoFrameKey(FOR_YOU_ORIGIN, item.id),
                                onClick = { onOpenVideo(item.id, videoFrameKey(FOR_YOU_ORIGIN, item.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun reasonText(reason: Reason): String =
    when (reason) {
        is Reason.Watched -> stringResource(R.string.home_reason_watched, reason.title)
        Reason.Subscription -> stringResource(R.string.home_reason_subscription)
        Reason.FrequentChannel -> stringResource(R.string.home_reason_frequent)
    }

@Composable
private fun ForYouEmpty(onShowTrends: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(104.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(YouTubelyIcons.ForYou), contentDescription = null, modifier = Modifier.size(44.dp))
            }
        }
        Text(
            stringResource(R.string.home_for_you_empty_title),
            style = MaterialTheme.typography.titleMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.home_for_you_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onShowTrends) { Text(stringResource(R.string.home_show_trends)) }
    }
}

/** New videos of the followed channels, optionally of one group only. */
@Composable
private fun SubscriptionsTab(
    feed: FeedUi,
    bottomPadding: Dp,
    onRefresh: () -> Unit,
    onSelectGroup: (String?) -> Unit,
    onSeen: () -> Unit,
    onOpenVideo: (VideoId, String) -> Unit,
) {
    // The feed counts as seen once it has been on screen for a moment.
    LaunchedEffect(Unit) {
        delay(FEED_SEEN_DELAY_MS)
        onSeen()
    }
    Refreshable(refreshing = feed.state.loading && feed.items.isNotEmpty(), onRefresh = onRefresh) {
        VideoGrid(bottomPadding) {
            if (feed.groups.isNotEmpty()) {
                item(key = "groups", span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = motionItem(),
                    ) {
                        item(key = "all") {
                            ExpressiveFilterChip(
                                selected = feed.group == null,
                                onClick = { onSelectGroup(null) },
                                label = stringResource(R.string.home_group_all),
                            )
                        }
                        items(feed.groups, key = { it }) { name ->
                            ExpressiveFilterChip(
                                selected = feed.group == name,
                                onClick = { onSelectGroup(name) },
                                label = name,
                                icon = YouTubelyIcons.Label,
                            )
                        }
                    }
                }
            }
            if (feed.items.isEmpty()) {
                item(key = "feed-status", span = { GridItemSpan(maxLineSpan) }) {
                    FeedStatus(feed.state, onRetry = onRefresh)
                }
            }
            items(feed.items, key = { "$FEED_ORIGIN-${it.id.value}" }) { item ->
                VideoCard(
                    item = item,
                    sharedKey = videoFrameKey(FEED_ORIGIN, item.id),
                    onClick = { onOpenVideo(item.id, videoFrameKey(FEED_ORIGIN, item.id)) },
                    modifier = motionItem(),
                )
            }
        }
    }
}

/** One of the public charts: a hero carousel of the first videos, then the rest. */
@Composable
private fun ExploreTab(
    kind: ExploreKind,
    state: ExploreUiState,
    bottomPadding: Dp,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenVideo: (VideoId, String) -> Unit,
) {
    when (state) {
        ExploreUiState.Loading -> {
            LoadingState()
        }

        is ExploreUiState.Error -> {
            ErrorState(state.error, onRetry = onRetry)
        }

        is ExploreUiState.Content -> {
            val items = state.items(kind)
            Refreshable(refreshing = state.refreshing, onRefresh = onRefresh) {
                VideoGrid(bottomPadding) {
                    if (items.size > HERO_COUNT) {
                        item(key = "hero-$kind", span = { GridItemSpan(maxLineSpan) }) {
                            HeroCarousel(items.take(HERO_COUNT), onOpenVideo, motionItem())
                        }
                    }
                    val rest = if (items.size > HERO_COUNT) items.drop(HERO_COUNT) else items
                    items(rest, key = { "$LIST_ORIGIN-$kind-${it.id.value}" }) { item ->
                        VideoCard(
                            item = item,
                            sharedKey = videoFrameKey(LIST_ORIGIN, item.id),
                            onClick = { onOpenVideo(item.id, videoFrameKey(LIST_ORIGIN, item.id)) },
                            modifier = motionItem(),
                        )
                    }
                }
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

/** "For you", "Subscriptions" (with the number of new videos) and the charts as filter chips. */
@Composable
private fun SectionChips(
    selected: HomeTab,
    hasSubscriptions: Boolean,
    newVideos: Int,
    onSelect: (HomeTab) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        item(key = "for-you") {
            ExpressiveFilterChip(
                selected = selected == HomeTab.ForYou,
                onClick = { onSelect(HomeTab.ForYou) },
                label = stringResource(R.string.home_for_you),
                icon = YouTubelyIcons.ForYou,
            )
        }
        if (hasSubscriptions) {
            item(key = "subscriptions") {
                val label = stringResource(R.string.home_subscriptions)
                ExpressiveFilterChip(
                    selected = selected == HomeTab.Subscriptions,
                    onClick = { onSelect(HomeTab.Subscriptions) },
                    label = if (newVideos > 0) "$label · $newVideos" else label,
                    icon = YouTubelyIcons.Subscriptions.outlined,
                )
            }
        }
        items(ExploreKind.entries, key = { it }) { kind ->
            ExpressiveFilterChip(
                selected = (selected as? HomeTab.Explore)?.kind == kind,
                onClick = { onSelect(HomeTab.Explore(kind)) },
                label = stringResource(kind.label),
                icon = kind.icon,
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
