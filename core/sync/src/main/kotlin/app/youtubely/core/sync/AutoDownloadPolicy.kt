// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.sync

import app.youtubely.core.data.AutoDownload
import app.youtubely.core.model.DownloadFormat

/** Decisions of the automatic download that do not touch the system, so they can be tested. */
object AutoDownloadPolicy {
    /**
     * The best [DownloadFormat.Video] not above [maxHeight] (the lowest one when all are higher),
     * or audio when [maxHeight] is `null` or the video has no suitable picture.
     */
    fun choose(
        formats: List<DownloadFormat>,
        maxHeight: Int?,
    ): DownloadFormat? {
        val audio = formats.firstOrNull { it == DownloadFormat.Audio }
        val videos = formats.filterIsInstance<DownloadFormat.Video>()
        val video =
            maxHeight?.let {
                videos.filter { format -> format.height <= it }.maxByOrNull { format -> format.height }
                    ?: videos.minByOrNull { format -> format.height }
            }
        return video ?: audio
    }

    /** Automatic downloads of a channel beyond the newest [keep], given newest first. */
    fun expired(
        downloads: List<AutoDownload>,
        keep: Int,
    ): List<AutoDownload> = downloads.drop(keep.coerceAtLeast(1))

    fun limitReached(
        usedBytes: Long,
        limitGb: Int,
    ): Boolean = limitGb > 0 && usedBytes >= limitGb * BYTES_PER_GB

    private const val BYTES_PER_GB = 1024L * 1024 * 1024
}
