// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import app.youtubely.core.model.StreamInfo
import app.youtubely.core.model.StreamType

/** How a video is handed to the player, chosen by [PlaybackSource.from]. */
sealed interface PlaybackSource {
    /** Request headers per media URL (client-specific User-Agent and so on). */
    val headers: Map<String, Map<String, String>>

    /** Manifest generated on the device from separate video and audio streams. */
    data class GeneratedDash(
        val manifest: String,
        override val headers: Map<String, Map<String, String>>,
    ) : PlaybackSource

    data class DashUrl(
        val url: String,
    ) : PlaybackSource {
        override val headers: Map<String, Map<String, String>> get() = emptyMap()
    }

    data class Hls(
        val url: String,
    ) : PlaybackSource {
        override val headers: Map<String, Map<String, String>> get() = emptyMap()
    }

    /** A single file with both video and audio (low quality fallback). */
    data class Progressive(
        val url: String,
        override val headers: Map<String, Map<String, String>>,
    ) : PlaybackSource

    companion object {
        /**
         * Live streams prefer HLS (it supports seeking back in the DVR window); videos prefer a
         * generated DASH manifest, then a server manifest, then the best muxed file.
         */
        fun from(info: StreamInfo): PlaybackSource? {
            val isLive = info.item.type == StreamType.Live
            val generated =
                info.item.duration?.let {
                    DashManifestBuilder.build(it, info.videoStreams, info.audioStreams)
                }
            val muxed = info.videoStreams.filterNot { it.isVideoOnly }.maxByOrNull { it.height }
            return when {
                isLive && info.hlsManifestUrl != null -> Hls(info.hlsManifestUrl!!)
                isLive && info.dashManifestUrl != null -> DashUrl(info.dashManifestUrl!!)
                generated != null -> GeneratedDash(generated, info.mediaHeaders())
                info.dashManifestUrl != null -> DashUrl(info.dashManifestUrl!!)
                info.hlsManifestUrl != null -> Hls(info.hlsManifestUrl!!)
                muxed != null -> Progressive(muxed.url, mapOf(muxed.url to muxed.httpHeaders))
                else -> null
            }
        }

        private fun StreamInfo.mediaHeaders(): Map<String, Map<String, String>> =
            videoStreams.associate { it.url to it.httpHeaders } + audioStreams.associate { it.url to it.httpHeaders }
    }
}
