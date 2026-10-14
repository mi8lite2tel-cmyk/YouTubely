// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.youtubely.core.database.YouTubelyDatabase
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelPage
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.ChannelTab
import app.youtubely.core.model.Comment
import app.youtubely.core.model.CommentSort
import app.youtubely.core.model.ExploreSection
import app.youtubely.core.model.Page
import app.youtubely.core.model.Paged
import app.youtubely.core.model.PlaylistId
import app.youtubely.core.model.PlaylistPage
import app.youtubely.core.model.Region
import app.youtubely.core.model.SearchFilter
import app.youtubely.core.model.SearchItem
import app.youtubely.core.model.StreamInfo
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.StreamType
import app.youtubely.core.model.VideoId
import app.youtubely.core.provider.ProviderError
import app.youtubely.core.provider.StreamProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubscriptionFeedTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), YouTubelyDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .build()
    private val subscriptions = SubscriptionRepository(database.subscriptionDao(), database.feedDao())

    @After
    fun close() = database.close()

    @Test
    fun mergesChannelsNewestFirstAndReportsFailures() =
        runTest {
            val a = channel("A")
            val b = channel("B")
            val broken = channel("C")
            listOf(a, b, broken).forEach { subscriptions.subscribe(it) }
            val provider =
                FakeStreamProvider(
                    mapOf(
                        a.id to listOf(item("a1", day = 1), item("a3", day = 3)),
                        b.id to listOf(item("b2", day = 2)),
                    ),
                )
            val feed = SubscriptionFeed(provider, subscriptions, database.subscriptionDao(), database.feedDao())

            feed.refresh()

            val state = feed.state.first()
            assertEquals(listOf("a3", "b2", "a1"), state.items.map { it.id.value })
            assertEquals(listOf("C"), state.failed.map { it.name })
            assertNull(state.error)
            assertEquals(
                "A",
                state.items
                    .first()
                    .channel
                    ?.name,
            )
        }

    @Test
    fun everyChannelFailingIsAnError() =
        runTest {
            subscriptions.subscribe(channel("A"))
            val feed =
                SubscriptionFeed(
                    FakeStreamProvider(emptyMap()),
                    subscriptions,
                    database.subscriptionDao(),
                    database.feedDao(),
                )
            feed.refresh()
            assertNotNull(feed.state.first().error)
        }

    private fun channel(name: String) = ChannelRef(ChannelId("UC$name"), name, emptyList(), isVerified = false)

    private fun item(
        id: String,
        day: Long,
    ) = StreamItem(
        id = VideoId(id),
        title = id,
        channel = null,
        thumbnails = emptyList(),
        type = StreamType.Video,
        duration = null,
        viewCount = null,
        uploadedAt = Instant.ofEpochSecond(day * 86_400),
        uploadedAtText = null,
    )
}
