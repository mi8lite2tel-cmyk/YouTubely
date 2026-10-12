// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import app.youtubely.core.model.AudioStream
import app.youtubely.core.model.SegmentIndex
import app.youtubely.core.model.VideoStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.time.Duration
import javax.xml.parsers.DocumentBuilderFactory

class DashManifestBuilderTest {
    private val index = SegmentIndex(0L..739L, 740L..2247L)

    private fun video(
        height: Int,
        mime: String = "video/mp4",
        codecs: String = "avc1.4D401F",
    ) = VideoStream(
        url = "https://rr1.googlevideo.com/videoplayback?itag=$height&c=VISIONOS",
        mimeType = mime,
        codecs = codecs,
        width = height * 16 / 9,
        height = height,
        fps = 30,
        bitrate = null,
        isHdr = false,
        isVideoOnly = true,
        segmentIndex = index,
        contentLength = 10_000_000,
    )

    private fun audio(
        lang: String?,
        original: Boolean,
    ) = AudioStream(
        url = "https://rr1.googlevideo.com/videoplayback?itag=140&lang=$lang",
        mimeType = "audio/mp4",
        codecs = "mp4a.40.2",
        bitrate = 128_000,
        languageTag = lang,
        trackName = null,
        isOriginal = original,
        sampleRate = 44_100,
        channels = 2,
        segmentIndex = index,
    )

    private fun parse(xml: String): Element =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(xml.byteInputStream())
            .documentElement

    @Test
    fun groupsVideoByCodecFamilyAndAudioByLanguage() {
        val xml =
            DashManifestBuilder.build(
                Duration.ofSeconds(100),
                listOf(video(1080), video(720), video(1080, "video/webm", "vp9")),
                listOf(audio("en", original = true), audio("de", original = false)),
            )!!
        val root = parse(xml)
        assertEquals("static", root.getAttribute("type"))
        assertEquals("PT100.000S", root.getAttribute("mediaPresentationDuration"))
        val sets = root.getElementsByTagName("AdaptationSet")
        assertEquals(4, sets.length)
        val reps = root.getElementsByTagName("Representation")
        assertEquals(5, reps.length)
        // Bandwidth estimated from size: 10 MB * 8 / 100 s
        assertEquals("800000", (reps.item(0) as Element).getAttribute("bandwidth"))
        // URLs are escaped and survive parsing unchanged
        val baseUrl = root.getElementsByTagName("BaseURL").item(0).textContent
        assertTrue(baseUrl.contains("&c=VISIONOS"))
        val segmentBase = root.getElementsByTagName("SegmentBase").item(0) as Element
        assertEquals("740-2247", segmentBase.getAttribute("indexRange"))
        assertTrue(xml.contains("""lang="de""""))
        assertTrue(xml.contains("""value="dub""""))
    }

    @Test
    fun needsVideoAudioAndIndexes() {
        assertNull(DashManifestBuilder.build(Duration.ofSeconds(10), emptyList(), listOf(audio("en", true))))
        assertNull(DashManifestBuilder.build(Duration.ofSeconds(10), listOf(video(720)), emptyList()))
        assertNull(
            DashManifestBuilder.build(
                Duration.ofSeconds(10),
                listOf(video(720).copy(segmentIndex = null)),
                listOf(audio("en", true)),
            ),
        )
    }

    @Test
    fun codecFamilies() {
        assertEquals("avc1", DashManifestBuilder.codecFamily("avc1.64002A"))
        assertEquals("vp9", DashManifestBuilder.codecFamily("vp09.00.40.08"))
        assertEquals("vp9", DashManifestBuilder.codecFamily("vp9"))
        assertEquals("av01", DashManifestBuilder.codecFamily("av01.0.08M.08"))
    }
}
