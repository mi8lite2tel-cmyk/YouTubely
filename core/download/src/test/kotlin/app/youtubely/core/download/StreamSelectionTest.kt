// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import app.youtubely.core.model.AudioStream
import app.youtubely.core.model.DownloadFormat
import app.youtubely.core.model.StreamInfo
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.StreamType
import app.youtubely.core.model.VideoId
import app.youtubely.core.model.VideoStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamSelectionTest {
    private fun video(
        height: Int,
        mime: String,
        codecs: String,
        bitrate: Int = height * 1000,
    ) = VideoStream(
        url = "v$height$codecs",
        mimeType = mime,
        codecs = codecs,
        width = height * 16 / 9,
        height = height,
        fps = 30,
        bitrate = bitrate,
        isHdr = false,
        isVideoOnly = true,
    )

    private fun audio(
        mime: String,
        codecs: String,
        bitrate: Int,
        original: Boolean = true,
    ) = AudioStream(
        url = "a$codecs$bitrate",
        mimeType = mime,
        codecs = codecs,
        bitrate = bitrate,
        languageTag = null,
        trackName = null,
        isOriginal = original,
    )

    private val info =
        StreamInfo(
            item = StreamItem(VideoId("x"), "t", null, emptyList(), StreamType.Video, null, null, null, null),
            description = "",
            likeCount = null,
            videoStreams =
                listOf(
                    video(2160, "video/webm", "vp9"),
                    video(1080, "video/mp4", "avc1.640028"),
                    video(1080, "video/mp4", "av01.0.08M.08", bitrate = 9_999_999),
                    video(720, "video/mp4", "avc1.4D401F"),
                    video(1080, "video/webm", "vp9"),
                ),
            audioStreams =
                listOf(
                    audio("audio/webm", "opus", 160_000),
                    audio("audio/mp4", "mp4a.40.2", 128_000),
                    audio("audio/mp4", "mp4a.40.5", 48_000),
                    audio("audio/mp4", "mp4a.40.2", 192_000, original = false),
                ),
            dashManifestUrl = null,
            hlsManifestUrl = null,
            subtitles = emptyList(),
            chapters = emptyList(),
            related = emptyList(),
            loudnessDb = null,
            publishedAt = null,
        )

    @Test
    fun offersOnlyMp4HeightsAndAudio() {
        assertEquals(
            listOf(DownloadFormat.Video(1080), DownloadFormat.Video(720), DownloadFormat.Audio),
            StreamSelection.formats(info),
        )
    }

    @Test
    fun prefersH264AtSameHeightAndOriginalAac() {
        val selected = StreamSelection.select(info, DownloadFormat.Video(1080))!!
        assertEquals("avc1.640028", selected.video!!.codecs)
        assertEquals("mp4a.40.2", selected.audio.codecs)
        assertEquals(128_000, selected.audio.bitrate)
    }

    @Test
    fun fallsBackToNextLowerHeight() {
        assertEquals(720, StreamSelection.select(info, DownloadFormat.Video(900))!!.video!!.height)
        assertNull(StreamSelection.select(info, DownloadFormat.Video(360)))
    }

    @Test
    fun audioOnlyHasNoVideo() {
        assertNull(StreamSelection.select(info, DownloadFormat.Audio)!!.video)
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("Ep_ 1_ What_s new_ (2026)", DownloadWorker.safeFileName("Ep: 1/ What's new? (2026)"))
        assertEquals("video", DownloadWorker.safeFileName("///"))
    }

    @Test
    fun skipsStreamsTheDeviceCannotDecode() {
        val noAv1: (VideoStream) -> Boolean = { it.codecs?.startsWith("av01") != true }
        val avcOnly: (VideoStream) -> Boolean = { it.codecs?.startsWith("avc1") == true }
        val limited = info.copy(videoStreams = info.videoStreams + video(2160, "video/mp4", "av01.0.12M.08"))
        assertEquals(1080, (StreamSelection.formats(limited, noAv1).first() as DownloadFormat.Video).height)
        assertEquals(2160, (StreamSelection.formats(limited).first() as DownloadFormat.Video).height)
        assertEquals(
            "v1080avc1.640028",
            StreamSelection.select(limited, DownloadFormat.Video(2160), avcOnly)!!.video!!.url,
        )
        assertNull(StreamSelection.select(limited, DownloadFormat.Video(2160)) { false })
    }
}
