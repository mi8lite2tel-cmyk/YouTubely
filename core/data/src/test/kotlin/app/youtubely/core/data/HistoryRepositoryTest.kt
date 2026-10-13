// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.youtubely.core.database.YouTubelyDatabase
import app.youtubely.core.datastore.PlaybackSettingsRepository
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.Image
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.StreamType
import app.youtubely.core.model.VideoId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), YouTubelyDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .build()

    private val settings by lazy {
        PlaybackSettingsRepository(PreferenceDataStoreFactory.create { File(folder.root, "test.preferences_pb") })
    }
    private val repository by lazy { HistoryRepository(database.historyDao(), settings) }

    @After
    fun close() = database.close()

    @Test
    fun resumesFromSavedPositionButNotNearTheEnd() =
        runTest {
            repository.record(video("a", minutes = 10))
            repository.savePosition(VideoId("a"), 120_000)
            assertEquals(120_000, repository.resumePosition(VideoId("a")))

            repository.savePosition(VideoId("a"), 595_000)
            assertEquals("watched to the end starts over", 0, repository.resumePosition(VideoId("a")))

            repository.savePosition(VideoId("a"), 2_000)
            assertEquals("barely started starts over", 0, repository.resumePosition(VideoId("a")))
        }

    @Test
    fun watchingAgainMovesToTopAndKeepsPosition() =
        runTest {
            repository.record(video("a"))
            repository.savePosition(VideoId("a"), 60_000)
            repository.record(video("b"))
            repository.record(video("a"))

            val history = repository.history.first()
            assertEquals(listOf("a", "b"), history.map { it.item.id.value })
            assertEquals(60_000, history.first().positionMs)
            assertEquals(0.1f, repository.progress.first().getValue(VideoId("a")), 0.001f)
        }

    @Test
    fun offlineCopyDoesNotEraseOnlineDetails() =
        runTest {
            repository.record(video("a"))
            repository.record(video("a").copy(channel = null, thumbnails = listOf(Image("file:///x.jpg", null, null))))

            val entry = repository.history.first().single()
            assertEquals("Channel", entry.item.channel?.name)
            assertEquals(
                "https://i.ytimg.com/a.jpg",
                entry.item.thumbnails
                    .single()
                    .url,
            )
        }

    @Test
    fun pausedHistoryRecordsNothing() =
        runTest {
            settings.setHistoryEnabled(false)
            repository.record(video("a"))
            assertTrue(repository.history.first().isEmpty())
        }

    @Test
    fun resumeCanBeTurnedOff() =
        runTest {
            repository.record(video("a"))
            repository.savePosition(VideoId("a"), 120_000)
            settings.setResumePlayback(false)
            assertEquals(0, repository.resumePosition(VideoId("a")))
        }

    private fun video(
        id: String,
        minutes: Long = 10,
    ) = StreamItem(
        id = VideoId(id),
        title = "Video $id",
        channel = ChannelRef(ChannelId("UC1"), "Channel", emptyList(), isVerified = true),
        thumbnails = listOf(Image("https://i.ytimg.com/$id.jpg", 480, 360)),
        type = StreamType.Video,
        duration = Duration.ofMinutes(minutes),
        viewCount = 1,
        uploadedAt = null,
        uploadedAtText = null,
    )
}
