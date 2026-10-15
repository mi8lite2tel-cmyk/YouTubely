// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import app.youtubely.core.model.AudioStream
import app.youtubely.core.model.DownloadState
import app.youtubely.core.model.VideoStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadReconcilerTest {
    private fun facts(
        id: String,
        state: DownloadState,
        fileExists: Boolean = true,
    ) = DownloadFacts(id, state, fileExists)

    @Test
    fun interruptedDownloadsWithoutWorkAreRestarted() {
        val plan =
            DownloadReconciler.plan(
                downloads =
                    listOf(
                        facts("a", DownloadState.Downloading),
                        facts("b", DownloadState.Merging),
                        facts("c", DownloadState.Queued),
                        facts("d", DownloadState.Downloading),
                        facts("e", DownloadState.Paused),
                        facts("f", DownloadState.Failed),
                    ),
                activeWork = setOf("d"),
                folders = emptyList(),
            )
        assertEquals(listOf("a", "b", "c"), plan.restart)
    }

    @Test
    fun finishedDownloadWhoseFileIsGoneIsForgotten() {
        val plan =
            DownloadReconciler.plan(
                downloads =
                    listOf(
                        facts("ok", DownloadState.Completed),
                        facts("gone", DownloadState.Completed, fileExists = false),
                        facts("working", DownloadState.Downloading, fileExists = false),
                    ),
                activeWork = setOf("working"),
                folders = emptyList(),
            )
        assertEquals(listOf("gone"), plan.forget)
        assertTrue(plan.restart.isEmpty())
    }

    @Test
    fun onlyLeftoverFoldersWithoutMediaAreDeleted() {
        val plan =
            DownloadReconciler.plan(
                downloads = listOf(facts("known", DownloadState.Completed)),
                activeWork = emptySet(),
                folders =
                    listOf(
                        FolderFacts("known", hasMedia = true),
                        FolderFacts("junk", hasMedia = false),
                        FolderFacts("precious", hasMedia = true),
                    ),
            )
        assertEquals(listOf("junk"), plan.deleteFolders)
    }

    private fun video(
        height: Int,
        codecs: String,
        size: Long,
    ) = VideoStream("u", "video/mp4", codecs, height * 16 / 9, height, 30, 1000, false, true, contentLength = size)

    private val audio = AudioStream("a", "audio/mp4", "mp4a.40.2", 128_000, null, null, true, contentLength = 5L)

    @Test
    fun fingerprintChangesWhenTheStreamDoes() {
        val first = StreamFingerprint.of(SelectedStreams(video(720, "avc1", 100), audio))
        assertEquals(first, StreamFingerprint.of(SelectedStreams(video(720, "avc1", 100), audio)))
        assertFalse(
            StreamFingerprint.matches(first, StreamFingerprint.of(SelectedStreams(video(720, "avc1", 101), audio))),
        )
        assertFalse(
            StreamFingerprint.matches(first, StreamFingerprint.of(SelectedStreams(video(1080, "avc1", 100), audio))),
        )
        assertFalse(StreamFingerprint.matches(first, StreamFingerprint.of(SelectedStreams(null, audio))))
    }

    @Test
    fun noStoredFingerprintMeansNothingToConflictWith() {
        assertTrue(StreamFingerprint.matches(null, "anything"))
    }
}
