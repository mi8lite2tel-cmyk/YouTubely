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
import app.youtubely.core.model.VideoId
import app.youtubely.core.ui.LocalSharedTransitionScope
import app.youtubely.feature.home.HomeScreen
import app.youtubely.feature.search.SearchScreen
import app.youtubely.feature.settings.SettingsScreen
import app.youtubely.feature.watch.WatchScreen
import app.youtubely.navigation.HomeRoute
import app.youtubely.navigation.SearchRoute
import app.youtubely.navigation.SettingsRoute
import app.youtubely.navigation.TopLevelRoute
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
    modifier: Modifier = Modifier,
) {
    val stacks: Map<TopLevelRoute, NavBackStack<NavKey>> =
        mapOf(
            HomeRoute to rememberNavBackStack(HomeRoute),
            SearchRoute to rememberNavBackStack(SearchRoute),
            SettingsRoute to rememberNavBackStack(SettingsRoute),
        )
    var currentName by rememberSaveable { mutableStateOf(HomeRoute.toString()) }
    val current = TopLevelItems.first { it.route.toString() == currentName }.route
    val backStack = stacks.getValue(current)
    val watching = backStack.lastOrNull() is WatchRoute

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
                    icon = {
                        // Outline morphs into the filled glyph on selection.
                        AnimatedContent(
                            targetState = selected,
                            transitionSpec = {
                                (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec()))
                                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
                            },
                            label = "navIcon",
                        ) { filled ->
                            Icon(
                                painterResource(if (filled) item.icon.filled else item.icon.outlined),
                                contentDescription = null,
                            )
                        }
                    },
                    label = { Text(stringResource(item.label)) },
                )
            }
        },
    ) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
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
                                entry<SearchRoute> { SearchScreen(onOpenVideo = ::openVideo) }
                                entry<SettingsRoute> { SettingsScreen(versionName = versionName) }
                                entry<WatchRoute> { route ->
                                    WatchScreen(
                                        videoId = VideoId(route.videoId),
                                        sharedKey = route.sharedKey,
                                        onOpenVideo = ::openVideo,
                                    )
                                }
                            },
                    )
                }
            }
        }
    }
}
