// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.youtubely.core.model.AutoDownloadRule
import app.youtubely.core.testing.saveScreenshot
import app.youtubely.core.testing.setScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h1200dp-xxhdpi")
class RuleScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun ruleEditor() {
        compose.setScreen {
            Column(Modifier.padding(16.dp)) {
                RuleDetails(
                    rule = AutoDownloadRule(maxHeight = 720, keepLast = 5, maxMinutes = 60, deleteWatched = true),
                    include = "podcast, episode",
                    exclude = "",
                    onRule = {},
                    onInclude = {},
                    onExclude = {},
                )
            }
        }
        compose.saveScreenshot("rule-editor")
    }

    @Test
    fun stats() {
        val now = java.time.Instant.parse("2026-10-14T20:00:00Z")
        val channel =
            app.youtubely.core.model.ChannelRef(
                app.youtubely.core.model
                    .ChannelId("a"),
                "Science Channel",
                emptyList(),
                false,
            )
        val history =
            (0 until 30).map { i ->
                app.youtubely.core.model.HistoryEntry(
                    app.youtubely.core.model.StreamItem(
                        app.youtubely.core.model
                            .VideoId("v$i"),
                        "Video $i",
                        channel.copy(
                            id =
                                app.youtubely.core.model
                                    .ChannelId("c${i % 4}"),
                            name = "Channel ${i % 4}",
                        ),
                        emptyList(),
                        app.youtubely.core.model.StreamType.Video,
                        java.time.Duration.ofMinutes(20),
                        null,
                        null,
                        null,
                    ),
                    positionMs = (i % 5 + 1) * 180_000L,
                    watchedAt = now.minus(java.time.Duration.ofHours(i * 9L)),
                )
            }
        val stats =
            app.youtubely.core.model.WatchStats
                .from(history, now)
        compose.setScreen {
            StatsContent(
                stats,
                app.youtubely.core.model
                    .TrafficSummary(monthMobile = 900_000_000, monthWifi = 4_000_000_000),
                androidx.compose.foundation.layout
                    .PaddingValues(16.dp),
            )
        }
        compose.saveScreenshot("stats")
    }
}
