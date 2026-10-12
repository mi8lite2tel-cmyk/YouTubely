// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

/** What to save: a video at a given resolution (merged with audio) or audio only. */
sealed interface DownloadFormat {
    data class Video(
        val height: Int,
    ) : DownloadFormat

    data object Audio : DownloadFormat
}

enum class DownloadState {
    Queued,
    Downloading,

    /** Video and audio are being combined into one file. */
    Merging,
    Paused,
    Completed,
    Failed,
}

/** A saved (or in-progress) video for offline viewing. */
data class Download(
    val id: String,
    val videoId: VideoId,
    val title: String,
    val channelName: String?,
    val duration: java.time.Duration?,
    val format: DownloadFormat,
    val state: DownloadState,
    /** 0..1 while downloading. */
    val progress: Float,
    val totalBytes: Long?,
    /** Final media file once [state] is [DownloadState.Completed]. */
    val filePath: String?,
    val thumbnailPath: String?,
    val createdAtMillis: Long,
    val error: String? = null,
) {
    companion object {
        fun idFor(
            videoId: VideoId,
            format: DownloadFormat,
        ): String =
            when (format) {
                is DownloadFormat.Video -> "${videoId.value}-${format.height}p"
                DownloadFormat.Audio -> "${videoId.value}-audio"
            }
    }
}
