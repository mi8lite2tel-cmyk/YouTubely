// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import app.youtubely.core.model.DownloadState

/** What the app knows about one download when it starts: the record and what is on disk. */
data class DownloadFacts(
    val id: String,
    val state: DownloadState,
    /** The finished file is where the record says it is. */
    val fileExists: Boolean,
)

/** What a download folder holds. */
data class FolderFacts(
    val name: String,
    /** A finished media file (mp4, m4a) is inside. */
    val hasMedia: Boolean,
)

/** What to do to bring records, work and files back in line after the app was killed. */
data class ReconcilePlan(
    /** Interrupted downloads that have no work scheduled: start them again. */
    val restart: List<String>,
    /** Finished downloads whose file was deleted from outside the app: forget them. */
    val forget: List<String>,
    /** Leftover folders of removed downloads without any finished media: safe to delete. */
    val deleteFolders: List<String>,
)

/**
 * Downloads are records in a file, work in WorkManager and folders on disk; any of the three can
 * outlive the others when the app is killed or storage is cleared. This decides what to fix. Pure,
 * so every case has a test. A folder with a finished media file is never deleted automatically.
 */
object DownloadReconciler {
    fun plan(
        downloads: List<DownloadFacts>,
        activeWork: Set<String>,
        folders: List<FolderFacts>,
    ): ReconcilePlan {
        val interrupted = setOf(DownloadState.Queued, DownloadState.Downloading, DownloadState.Merging)
        val known = downloads.mapTo(HashSet()) { it.id }
        return ReconcilePlan(
            restart = downloads.filter { it.state in interrupted && it.id !in activeWork }.map { it.id },
            forget = downloads.filter { it.state == DownloadState.Completed && !it.fileExists }.map { it.id },
            deleteFolders = folders.filter { it.name !in known && !it.hasMedia }.map { it.name },
        )
    }
}
