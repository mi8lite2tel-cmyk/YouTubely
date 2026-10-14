// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.youtubely.R
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.LocalQueue
import app.youtubely.core.model.PlaylistId
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.VideoId
import app.youtubely.core.model.YouTubeLink
import app.youtubely.core.player.NowPlaying
import app.youtubely.core.ui.LocalSharedTransitionScope
import app.youtubely.core.ui.LocalVideoActions
import app.youtubely.feature.actions.VideoActionsSheet
import app.youtubely.feature.channel.ChannelScreen
import app.youtubely.feature.downloads.DownloadsScreen
import app.youtubely.feature.home.HomeScreen
import app.youtubely.feature.library.LibraryScreen
import app.youtubely.feature.library.LibrarySearchScreen
import app.youtubely.feature.library.LocalPlaylistScreen
import app.youtubely.feature.library.StatsScreen
import app.youtubely.feature.playlist.PlaylistScreen
import app.youtubely.feature.search.SearchScreen
import app.youtubely.feature.settings.SettingsScreen
import app.youtubely.feature.transfer.TransferScreen
import app.youtubely.feature.watch.MiniPlayer
import app.youtubely.feature.watch.OfflineWatchScreen
import app.youtubely.feature.watch.WatchScreen
import app.youtubely.navigation.ChannelRoute
import app.youtubely.navigation.DownloadsRoute
import app.youtubely.navigation.HomeRoute
import app.youtubely.navigation.LibraryRoute
import app.youtubely.navigation.LibrarySearchRoute
import app.youtubely.navigation.LocalPlaylistRoute
import app.youtubely.navigation.OfflineWatchRoute
import app.youtubely.navigation.PlaylistRoute
import app.youtubely.navigation.SearchRoute
import app.youtubely.navigation.SettingsRoute
import app.youtubely.navigation.StatsRoute
import app.youtubely.navigation.TopLevelRoute
import app.youtubely.navigation.TransferRoute
import app.youtubely.navigation.WatchRoute

private data class TopLevelItem(
    val route: TopLevelRoute,
    val label: Int,
    val icon: YouTubelyIcons.Toggle,
)

private val TopLevelItems =
    listOf(
        TopLevelItem(HomeRoute, R.string.nav_home, YouTubelyIcons.Home),
        TopLevelItem(SearchRoute, R.string.nav_search, YouTubelyIcons.Search),
        TopLevelItem(LibraryRoute, R.string.nav_library, YouTubelyIcons.Library),
        TopLevelItem(DownloadsRoute, R.string.nav_downloads, YouTubelyIcons.Downloads),
        TopLevelItem(SettingsRoute, R.string.nav_settings, YouTubelyIcons.Settings),
    )

/**
 * Adaptive shell: a short navigation bar on phones and a wide navigation rail on larger windows
 * (chosen by [NavigationSuiteScaffold] from the window size class). Each top-level destination
 * keeps its own back stack; the watch screen hides the navigation for an immersive player.
 */
@Composable
fun YouTubelyApp(
    versionName: String,
    pendingLink: YouTubeLink?,
    onLinkHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stacks: Map<TopLevelRoute, NavBackStack<NavKey>> =
        mapOf(
            HomeRoute to rememberNavBackStack(HomeRoute),
            SearchRoute to rememberNavBackStack(SearchRoute),
            LibraryRoute to rememberNavBackStack(LibraryRoute),
            DownloadsRoute to rememberNavBackStack(DownloadsRoute),
            SettingsRoute to rememberNavBackStack(SettingsRoute),
        )
    var currentName by rememberSaveable { mutableStateOf(HomeRoute.toString()) }
    val current = TopLevelItems.first { it.route.toString() == currentName }.route
    val backStack = stacks.getValue(current)
    val watching = backStack.lastOrNull().let { it is WatchRoute || it is OfflineWatchRoute }

    val motion = MaterialTheme.motionScheme
    val suiteState = rememberNavigationSuiteScaffoldState()
    LaunchedEffect(watching) { if (watching) suiteState.hide() else suiteState.show() }

    // Back on a non-home tab's root returns to Home, like most Android apps.
    BackHandler(enabled = backStack.size == 1 && current != HomeRoute) { currentName = HomeRoute.toString() }

    fun openVideo(
        id: VideoId,
        sharedKey: String,
    ) {
        backStack.add(WatchRoute(id.value, sharedKey))
    }

    fun openChannel(id: ChannelId) {
        // Opening the channel that is already on top (e.g. from its own video) just goes back to it.
        if (backStack.lastOrNull() != ChannelRoute(id.value)) backStack.add(ChannelRoute(id.value))
    }

    fun openPlaylist(id: PlaylistId) {
        backStack.add(PlaylistRoute(id.value))
    }

    // A link or notification the app was opened with: show it on top of the current tab.
    LaunchedEffect(pendingLink) {
        pendingLink?.let {
            backStack.show(it)
            onLinkHandled()
        }
    }
    var actionsFor by remember { mutableStateOf<StreamItem?>(null) }

    // Tabs with their back stacks; above the mini player, which shows the video left playing.
    val navigation: @Composable (Modifier) -> Unit = { navigationModifier ->
        SharedTransitionLayout(navigationModifier) {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                LocalVideoActions provides { actionsFor = it },
            ) {
                AnimatedContent(targetState = current, label = "tab", transitionSpec = {
                    fadeIn(motion.defaultEffectsSpec())
                        .togetherWith(fadeOut(motion.fastEffectsSpec()))
                }) { tab ->
                    val stack = stacks.getValue(tab)
                    NavDisplay(
                        backStack = stack,
                        onBack = { stack.removeLastOrNull() },
                        entryDecorators =
                            listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                        sharedTransitionScope = this@SharedTransitionLayout,
                        transitionSpec = {
                            (
                                slideInHorizontally(motion.defaultSpatialSpec()) { it / 4 } +
                                    fadeIn(motion.defaultEffectsSpec())
                            ).togetherWith(fadeOut(motion.fastEffectsSpec()))
                        },
                        popTransitionSpec = {
                            fadeIn(motion.defaultEffectsSpec())
                                .togetherWith(
                                    slideOutHorizontally(motion.defaultSpatialSpec()) { it / 4 } +
                                        fadeOut(motion.fastEffectsSpec()),
                                )
                        },
                        entryProvider =
                            entryProvider {
                                entry<HomeRoute> { HomeScreen(onOpenVideo = ::openVideo) }
                                entry<SearchRoute> {
                                    SearchScreen(
                                        onOpenVideo = ::openVideo,
                                        onOpenChannel = ::openChannel,
                                        onOpenPlaylist = ::openPlaylist,
                                    )
                                }
                                entry<LibraryRoute> {
                                    LibraryScreen(
                                        onOpenVideo = ::openVideo,
                                        onOpenChannel = ::openChannel,
                                        onOpenPlaylist = { stack.add(LocalPlaylistRoute(it)) },
                                        onPlayAllLater = {
                                            id,
                                            key,
                                            ->
                                            stack.add(WatchRoute(id.value, key, LocalQueue.WATCH_LATER))
                                        },
                                        onOpenTransfer = { stack.add(TransferRoute) },
                                        onOpenStats = { stack.add(StatsRoute) },
                                        onOpenSearch = { stack.add(LibrarySearchRoute) },
                                    )
                                }
                                entry<LibrarySearchRoute> {
                                    LibrarySearchScreen(
                                        onBack = { stack.removeLastOrNull() },
                                        onOpenVideo = ::openVideo,
                                        onPlayDownload = { stack.add(OfflineWatchRoute(it)) },
                                        onOpenPlaylist = { stack.add(LocalPlaylistRoute(it)) },
                                    )
                                }
                                entry<StatsRoute> { StatsScreen(onBack = { stack.removeLastOrNull() }) }
                                entry<LocalPlaylistRoute> { route ->
                                    LocalPlaylistScreen(
                                        playlistId = route.playlistId,
                                        onBack = { stack.removeLastOrNull() },
                                        onOpenVideo = ::openVideo,
                                        onPlayAll = { id, key ->
                                            val queue = LocalQueue.playlist(route.playlistId)
                                            stack.add(WatchRoute(id.value, key, queue))
                                        },
                                    )
                                }
                                entry<TransferRoute> { TransferScreen(onBack = { stack.removeLastOrNull() }) }
                                entry<DownloadsRoute> {
                                    DownloadsScreen(
                                        onPlay = { stack.add(OfflineWatchRoute(it)) },
                                        onOpenOnline = { openVideo(it, "downloads-${it.value}") },
                                    )
                                }
                                entry<ChannelRoute> { route ->
                                    ChannelScreen(
                                        channelId = ChannelId(route.channelId),
                                        onBack = { stack.removeLastOrNull() },
                                        onOpenVideo = ::openVideo,
                                        onOpenChannel = ::openChannel,
                                        onOpenPlaylist = ::openPlaylist,
                                    )
                                }
                                entry<PlaylistRoute> { route ->
                                    PlaylistScreen(
                                        playlistId = PlaylistId(route.playlistId),
                                        onBack = { stack.removeLastOrNull() },
                                        onPlay = {
                                            id,
                                            key,
                                            playlist,
                                            ->
                                            stack.add(WatchRoute(id.value, key, playlist.value))
                                        },
                                        onOpenChannel = ::openChannel,
                                    )
                                }
                                entry<OfflineWatchRoute> { route -> OfflineWatchScreen(downloadId = route.downloadId) }
                                entry<SettingsRoute> {
                                    SettingsScreen(
                                        versionName = versionName,
                                        onOpenTransfer = { stack.add(TransferRoute) },
                                    )
                                }
                                entry<WatchRoute> { route ->
                                    WatchScreen(
                                        videoId = VideoId(route.videoId),
                                        sharedKey = route.sharedKey,
                                        playlistId = route.playlistId?.let(::PlaylistId),
                                        onOpenVideo = ::openVideo,
                                        onOpenChannel = ::openChannel,
                                        onCollapse = { stack.removeLastOrNull() },
                                    )
                                }
                            },
                    )
                }
            }
        }
    }

    NavigationSuiteScaffold(
        modifier = modifier,
        state = suiteState,
        navigationItems = {
            TopLevelItems.forEach { item ->
                val selected = item.route == current
                NavigationSuiteItem(
                    selected = selected,
                    onClick = {
                        if (selected) {
                            // Re-selecting a tab returns to its root.
                            while (backStack.size > 1) backStack.removeLastOrNull()
                        } else {
                            currentName = item.route.toString()
                        }
                    },
                    icon = { NavigationIcon(item, selected) },
                    label = { Text(stringResource(item.label)) },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            navigation(Modifier.weight(1f))
            MiniPlayer(
                visible = !watching,
                onOpen = { backStack.add(it.route()) },
            )
        }
    }
    actionsFor?.let { item ->
        VideoActionsSheet(item = item, onDismiss = { actionsFor = null }, onOpenChannel = ::openChannel)
    }
}

/** The screen of what the mini player plays: the download or the online video. */
private fun NowPlaying.route(): NavKey =
    downloadId?.let(::OfflineWatchRoute) ?: WatchRoute(item.id.value, "mini-${item.id.value}")

/** Opens what a YouTube link points at on top of this back stack. */
private fun NavBackStack<NavKey>.show(link: YouTubeLink) {
    add(
        when (link) {
            is YouTubeLink.Video -> WatchRoute(link.id.value, "link-${link.id.value}", link.playlist?.value)
            is YouTubeLink.Playlist -> PlaylistRoute(link.id.value)
            is YouTubeLink.Channel -> ChannelRoute(link.id.value)
        },
    )
}

/** Outline morphs into the filled glyph on selection. */
@Composable
private fun NavigationIcon(
    item: TopLevelItem,
    selected: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec()))
                .togetherWith(fadeOut(motion.fastEffectsSpec()))
        },
        label = "navIcon",
    ) { filled ->
        Icon(painterResource(if (filled) item.icon.filled else item.icon.outlined), contentDescription = null)
    }
}
