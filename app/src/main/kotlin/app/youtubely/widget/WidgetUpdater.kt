// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import app.youtubely.core.data.SubscriptionFeed
import app.youtubely.core.player.PlaybackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Redraws the widgets when what they show changes while the app runs. */
@Singleton
class WidgetUpdater
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playback: PlaybackManager,
        private val feed: SubscriptionFeed,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun start() {
            scope.launch {
                combine(
                    playback.nowPlaying,
                    playback.isPlaying,
                ) { playing, isPlaying -> playing?.item?.id to isPlaying }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { NowPlayingWidget().updateAll(context) }
            }
            scope.launch {
                feed.state
                    .map { state -> state.items.take(FEED_ITEMS).map { it.id } }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { NewVideosWidget().updateAll(context) }
            }
        }

        private companion object {
            const val FEED_ITEMS = 8
        }
    }
