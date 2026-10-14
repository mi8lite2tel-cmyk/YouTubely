// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.youtubely.core.database.YouTubelyDatabase
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.StreamType
import app.youtubely.core.model.VideoId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryRepositoriesTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), YouTubelyDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .build()
    private val subscriptions = SubscriptionRepository(database.subscriptionDao(), database.feedDao())
    private val watchLater = WatchLaterRepository(database.watchLaterDao())
    private val playlists = PlaylistRepository(database.playlistDao())

    @After
    fun close() = database.close()

    @Test
    fun subscribingAgainKeepsTheChannelSettings() =
        runTest {
            val channel = channel("A")
            subscriptions.subscribe(channel)
            subscriptions.setNotify(channel.id, true)
            subscriptions.setAutoDownload(channel.id, true)
            subscriptions.setGroup(channel.id, "  Music ")

            subscriptions.subscribe(channel.copy(name = "A renamed"))

            val saved = subscriptions.get(channel.id)!!
            assertEquals("A renamed", saved.channel.name)
            assertTrue(saved.notify)
            assertTrue(saved.autoDownload)
            assertEquals("Music", saved.group)
            assertEquals(listOf("Music"), subscriptions.groups.first())
        }

    @Test
    fun unsubscribingDropsTheChannelsFeed() =
        runTest {
            val channel = channel("A")
            subscriptions.subscribe(channel)
            val feed = feedOf(mapOf(channel.id to listOf(item("a1"))))
            feed.refresh()
            assertEquals(
                1,
                feed.state
                    .first()
                    .items.size,
            )

            subscriptions.unsubscribe(channel.id)

            assertTrue(
                feed.state
                    .first()
                    .items
                    .isEmpty(),
            )
        }

    @Test
    fun firstLoadSeedsTheFeedAndLaterLoadsFindFreshVideos() =
        runTest {
            val channel = channel("A")
            subscriptions.subscribe(channel)
            val videos = mutableListOf(item("old"))
            val feed = feedOf(mapOf(channel.id to videos))

            feed.refresh()
            // Not "new": a channel just followed must not announce its back catalogue.
            assertTrue(feed.freshToAnnounce().isEmpty())

            videos += item("new")
            feed.refresh()
            val fresh = feed.freshToAnnounce()
            assertEquals(listOf("new"), fresh.map { it.item.id.value })
            assertEquals(
                channel.id,
                fresh
                    .single()
                    .subscription.channel.id,
            )

            feed.markAnnounced(fresh)
            assertTrue(feed.freshToAnnounce().isEmpty())
            // The automatic download keeps its own list.
            assertEquals(listOf("new"), feed.freshToDownload().map { it.item.id.value })
            feed.markDownloadHandled(feed.freshToDownload())
            assertTrue(feed.freshToDownload().isEmpty())
            assertEquals(
                2,
                feed.state
                    .first()
                    .items.size,
            )
        }

    @Test
    fun watchLaterKeepsTheNewestFirstAndCanRemove() =
        runTest {
            watchLater.add(item("a"), Instant.ofEpochSecond(1))
            watchLater.add(item("b"), Instant.ofEpochSecond(2))
            assertEquals(listOf("b", "a"), watchLater.videos.first().map { it.item.id.value })
            assertEquals(setOf(VideoId("a"), VideoId("b")), watchLater.ids.first())

            watchLater.remove(VideoId("b"))

            assertEquals(listOf("a"), watchLater.videos.first().map { it.item.id.value })
        }

    @Test
    fun playlistsHoldEachVideoOnceAndDisappearWithTheirItems() =
        runTest {
            val id = playlists.create(" Road ")
            playlists.add(id, listOf(item("a"), item("b")))
            playlists.add(id, listOf(item("b"), item("c")))

            assertEquals(listOf("a", "b", "c"), playlists.items(id).first().map { it.id.value })
            val summary = playlists.playlists.first().single()
            assertEquals("Road", summary.name)
            assertEquals(3, summary.itemCount)
            assertEquals(setOf(id), playlists.containing(VideoId("a")).first())

            playlists.remove(id, VideoId("a"))
            assertEquals(
                2,
                playlists.playlists
                    .first()
                    .single()
                    .itemCount,
            )

            playlists.delete(id)
            assertTrue(playlists.playlists.first().isEmpty())
            assertFalse(playlists.containing(VideoId("b")).first().contains(id))
        }

    private fun feedOf(feeds: Map<ChannelId, List<StreamItem>>) =
        SubscriptionFeed(
            FakeStreamProvider(feeds),
            subscriptions,
            database.subscriptionDao(),
            database.feedDao(),
        )

    private fun channel(name: String) = ChannelRef(ChannelId("UC$name"), name, emptyList(), isVerified = false)

    private fun item(id: String) =
        StreamItem(
            id = VideoId(id),
            title = id,
            channel = null,
            thumbnails = emptyList(),
            type = StreamType.Video,
            duration = Duration.ofMinutes(1),
            viewCount = null,
            uploadedAt = Instant.now(),
            uploadedAtText = null,
        )
}
