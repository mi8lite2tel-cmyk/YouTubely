// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import app.youtubely.core.model.AudioStream
import app.youtubely.core.model.SegmentIndex
import app.youtubely.core.model.VideoStream
import java.time.Duration
import java.util.Locale

/**
 * Builds a static, on-demand DASH manifest from separate video-only and audio streams that
 * carry [SegmentIndex] ranges. One manifest holds every quality, so the player adapts quality
 * to the network by itself and loads media in small range requests (fast seeking, no single
 * multi-hundred-megabyte download that servers throttle).
 *
 * Video is grouped into one adaptation set per container and codec family (H.264 and AV1 in
 * MP4, VP9 in WebM), audio into one set per language and container.
 */
object DashManifestBuilder {
    private const val BITS_PER_BYTE = 8
    private const val CHANNEL_SCHEME = "urn:mpeg:dash:23003:3:audio_channel_configuration:2011"

    fun build(
        duration: Duration,
        videos: List<VideoStream>,
        audios: List<AudioStream>,
    ): String? {
        val playableVideos = videos.filter { it.isVideoOnly && it.segmentIndex != null }
        val playableAudios = audios.filter { it.segmentIndex != null }
        if (playableVideos.isEmpty() || playableAudios.isEmpty() || duration.isZero) return null

        val seconds = duration.toMillis() / MILLIS_PER_SECOND
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" """)
            append("""profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" type="static" """)
            appendLine("""minBufferTime="PT1.5S" mediaPresentationDuration="PT${seconds.format()}S">""")
            appendLine("""<Period id="0" start="PT0S">""")
            var setId = 0
            playableVideos
                .groupBy { it.mimeType to codecFamily(it.codecs) }
                .forEach { (key, streams) ->
                    appendLine(
                        """<AdaptationSet id="${setId++}" contentType="video" mimeType="${key.first.xml()}" """ +
                            """subsegmentAlignment="true" subsegmentStartsWithSAP="1">""",
                    )
                    streams.sortedByDescending { it.height }.forEachIndexed { index, video ->
                        append("""<Representation id="v$setId-$index" """)
                        video.codecs?.let { append("""codecs="${it.xml()}" """) }
                        append("""bandwidth="${bandwidth(video.bitrate, video.contentLength, seconds)}" """)
                        appendLine("""width="${video.width}" height="${video.height}" frameRate="${video.fps}">""")
                        appendMedia(video.url, video.segmentIndex!!)
                        appendLine("</Representation>")
                    }
                    appendLine("</AdaptationSet>")
                }
            playableAudios
                .groupBy { Triple(it.languageTag, it.mimeType, it.isOriginal) }
                .forEach { (key, streams) ->
                    val (language, mimeType, original) = key
                    append("""<AdaptationSet id="${setId++}" contentType="audio" mimeType="${mimeType.xml()}" """)
                    language?.let { append("""lang="${it.xml()}" """) }
                    appendLine(""">""")
                    val role = if (original) "main" else "dub"
                    appendLine("""<Role schemeIdUri="urn:mpeg:dash:role:2011" value="$role"/>""")
                    streams.forEachIndexed { index, audio ->
                        append("""<Representation id="a$setId-$index" """)
                        audio.codecs?.let { append("""codecs="${it.xml()}" """) }
                        audio.sampleRate?.let { append("""audioSamplingRate="$it" """) }
                        appendLine("""bandwidth="${bandwidth(audio.bitrate, audio.contentLength, seconds)}">""")
                        audio.channels?.let {
                            appendLine("""<AudioChannelConfiguration schemeIdUri="$CHANNEL_SCHEME" value="$it"/>""")
                        }
                        appendMedia(audio.url, audio.segmentIndex!!)
                        appendLine("</Representation>")
                    }
                    appendLine("</AdaptationSet>")
                }
            appendLine("</Period>")
            appendLine("</MPD>")
        }
    }

    private const val MILLIS_PER_SECOND = 1000.0
    private const val FALLBACK_BANDWIDTH = 1_000_000

    private fun StringBuilder.appendMedia(
        url: String,
        index: SegmentIndex,
    ) {
        appendLine("<BaseURL>${url.xml()}</BaseURL>")
        appendLine("""<SegmentBase indexRange="${index.indexRange.first}-${index.indexRange.last}">""")
        appendLine("""<Initialization range="${index.initRange.first}-${index.initRange.last}"/>""")
        appendLine("</SegmentBase>")
    }

    /** DASH requires a bandwidth; estimate it from the file size when the source has no bitrate. */
    private fun bandwidth(
        bitrate: Int?,
        contentLength: Long?,
        seconds: Double,
    ): Int =
        bitrate
            ?: contentLength?.let { (it * BITS_PER_BYTE / seconds).toInt() }
            ?: FALLBACK_BANDWIDTH

    /** `avc1.64002A` -> `avc1`, `vp09.00.40.08`/`vp9` -> `vp9`, `av01.0.08M.08` -> `av01`. */
    internal fun codecFamily(codecs: String?): String {
        val prefix = codecs?.substringBefore('.')?.lowercase(Locale.ROOT).orEmpty()
        return if (prefix == "vp09") "vp9" else prefix
    }

    private fun Double.format() = String.format(Locale.ROOT, "%.3f", this)

    private fun String.xml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
