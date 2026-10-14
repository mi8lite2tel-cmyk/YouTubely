// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.sync

import app.youtubely.core.data.AutoDownload
import app.youtubely.core.model.AutoDownloadRule
import app.youtubely.core.model.DownloadFormat
import app.youtubely.core.model.SyncSettings
import app.youtubely.core.model.VideoId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDownloadPolicyTest {
    private val formats =
        listOf(1080, 720, 360).map(DownloadFormat::Video) + DownloadFormat.Audio

    @Test
    fun picksTheBestPictureWithinTheLimit() {
        assertEquals(DownloadFormat.Video(720), AutoDownloadPolicy.choose(formats, 720))
        assertEquals(DownloadFormat.Video(720), AutoDownloadPolicy.choose(formats, 900))
    }

    @Test
    fun fallsBackToTheLowestPictureWhenAllAreTooBig() {
        assertEquals(DownloadFormat.Video(360), AutoDownloadPolicy.choose(formats, 240))
    }

    @Test
    fun noPictureLimitMeansAudioOnly() {
        assertEquals(DownloadFormat.Audio, AutoDownloadPolicy.choose(formats, null))
    }

    @Test
    fun audioIsTheLastResort() {
        assertEquals(DownloadFormat.Audio, AutoDownloadPolicy.choose(listOf(DownloadFormat.Audio), 720))
        assertNull(AutoDownloadPolicy.choose(emptyList(), 720))
    }

    @Test
    fun keepsTheNewestAndExpiresTheRest() {
        val all = listOf("a", "b", "c", "d").map { AutoDownload(VideoId(it), "dl-$it") }
        assertEquals(listOf("c", "d"), AutoDownloadPolicy.expired(all, 2).map { it.videoId.value })
        assertEquals(3, AutoDownloadPolicy.expired(all, 0).size)
    }

    @Test
    fun storageLimit() {
        val gb = 1024L * 1024 * 1024
        assertFalse(AutoDownloadPolicy.limitReached(100 * gb, 0))
        assertFalse(AutoDownloadPolicy.limitReached(4 * gb, 5))
        assertTrue(AutoDownloadPolicy.limitReached(5 * gb, 5))
    }

    @Test
    fun channelRuleOverridesGlobalQualityAndKeep() {
        val global = SyncSettings(autoDownloadMaxHeight = 720, autoDownloadKeepLast = 3)
        assertEquals(720, AutoDownloadPolicy.maxHeight(AutoDownloadRule(), global))
        assertEquals(1080, AutoDownloadPolicy.maxHeight(AutoDownloadRule(maxHeight = 1080), global))
        assertNull(AutoDownloadPolicy.maxHeight(AutoDownloadRule(maxHeight = 1080, audioOnly = true), global))
        assertEquals(3, AutoDownloadPolicy.keepLast(AutoDownloadRule(), global))
        assertEquals(10, AutoDownloadPolicy.keepLast(AutoDownloadRule(keepLast = 10), global))
    }

    @Test
    fun watchedDownloadsAreThoseNearlyFinished() {
        val downloads =
            listOf(AutoDownload(VideoId("a"), "1"), AutoDownload(VideoId("b"), "2"), AutoDownload(VideoId("c"), "3"))
        val progress = mapOf(VideoId("a") to 0.95f, VideoId("b") to 0.5f)
        assertEquals(listOf("1"), AutoDownloadPolicy.watched(downloads, progress).map { it.downloadId })
    }

    @Test
    fun perRunLimitStopsTheRun() {
        assertFalse(AutoDownloadPolicy.runFull(saved = 4, perRun = 5))
        assertTrue(AutoDownloadPolicy.runFull(saved = 5, perRun = 5))
        assertFalse(AutoDownloadPolicy.runFull(saved = 100, perRun = 0))
    }
}
