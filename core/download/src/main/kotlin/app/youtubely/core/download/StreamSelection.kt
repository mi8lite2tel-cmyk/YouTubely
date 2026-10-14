// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import app.youtubely.core.model.AudioStream
import app.youtubely.core.model.DownloadFormat
import app.youtubely.core.model.StreamInfo
import app.youtubely.core.model.VideoStream

/** The streams to fetch for one download. [video] is `null` for audio-only downloads. */
data class SelectedStreams(
    val video: VideoStream?,
    val audio: AudioStream,
)

/**
 * Picks streams that can be combined into an MP4 **without re-encoding**: H.264 or AV1 video in
 * MP4 plus AAC audio. VP9/Opus WebM streams would need a slow transcode on the phone.
 */
object StreamSelection {
    private val MP4_VIDEO_CODECS = listOf("avc1", "av01")

    private fun VideoStream.isMuxable(): Boolean =
        isVideoOnly && mimeType == "video/mp4" && MP4_VIDEO_CODECS.any { codecs?.startsWith(it) == true }

    private fun AudioStream.isAac(): Boolean = mimeType == "audio/mp4" && codecs?.startsWith("mp4a") == true

    /** Resolutions offered for download, highest first, plus audio only when available. */
    fun formats(
        info: StreamInfo,
        canDecode: (VideoStream) -> Boolean = { true },
    ): List<DownloadFormat> {
        if (bestAudio(info) == null) return emptyList()
        val heights =
            info.videoStreams
                .filter { it.isMuxable() && canDecode(it) }
                .map { it.height }
                .distinct()
                .sortedDescending()
        return heights.map(DownloadFormat::Video) + DownloadFormat.Audio
    }

    /** @param canDecode streams the device cannot play are never chosen, whatever resolution was asked for. */
    fun select(
        info: StreamInfo,
        format: DownloadFormat,
        canDecode: (VideoStream) -> Boolean = { true },
    ): SelectedStreams? {
        val audio = bestAudio(info) ?: return null
        return when (format) {
            DownloadFormat.Audio -> {
                SelectedStreams(video = null, audio = audio)
            }

            is DownloadFormat.Video -> {
                info.videoStreams
                    .filter { it.isMuxable() && canDecode(it) && it.height <= format.height }
                    // Exact height first, then the closest lower one; H.264 plays everywhere, so it
                    // wins over AV1 at the same height; then the higher bitrate.
                    .sortedWith(
                        compareByDescending<VideoStream> { it.height }
                            .thenByDescending { it.codecs?.startsWith("avc1") == true }
                            .thenByDescending { it.bitrate ?: 0 },
                    ).firstOrNull()
                    ?.let { SelectedStreams(it, audio) }
            }
        }
    }

    /** Original-language AAC with the highest bitrate. */
    private fun bestAudio(info: StreamInfo): AudioStream? =
        info.audioStreams
            .filter { it.isAac() }
            .sortedWith(compareByDescending<AudioStream> { it.isOriginal }.thenByDescending { it.bitrate ?: 0 })
            .firstOrNull()
}
