// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.dash.manifest.Representation
import app.youtubely.core.model.AudioStream
import app.youtubely.core.model.SegmentIndex
import app.youtubely.core.model.VideoStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

/** Feeds the generated manifest to Media3's own DASH parser, exactly as the player does. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Media3ParsesManifestTest {
    @Test
    fun media3ReadsAllRepresentations() {
        val index = SegmentIndex(0L..739L, 740L..2247L)
        val videos =
            listOf(1080, 720, 360).map { h ->
                VideoStream(
                    url = "https://rr1.googlevideo.com/videoplayback?itag=$h&c=VISIONOS&sig=a%3Db",
                    mimeType = "video/mp4",
                    codecs = "avc1.4D401F",
                    width = h * 16 / 9,
                    height = h,
                    fps = 30,
                    bitrate = h * 1000,
                    isHdr = false,
                    isVideoOnly = true,
                    segmentIndex = index,
                )
            }
        val audio =
            AudioStream(
                url = "https://rr1.googlevideo.com/videoplayback?itag=140",
                mimeType = "audio/mp4",
                codecs = "mp4a.40.2",
                bitrate = 128_000,
                languageTag = "en",
                trackName = null,
                isOriginal = true,
                sampleRate = 44_100,
                channels = 2,
                segmentIndex = index,
            )
        val xml = DashManifestBuilder.build(Duration.ofMillis(634_566), videos, listOf(audio))!!

        val manifest = DashManifestParser().parse(Uri.parse("https://youtubely.invalid/m.mpd"), xml.byteInputStream())

        assertEquals(634_566L, manifest.durationMs)
        val sets = manifest.getPeriod(0).adaptationSets
        val video = sets.single { it.type == C.TRACK_TYPE_VIDEO }
        val audioSet = sets.single { it.type == C.TRACK_TYPE_AUDIO }
        assertEquals(listOf(1080, 720, 360), video.representations.map { it.format.height })
        assertEquals(
            "en",
            audioSet.representations
                .single()
                .format.language,
        )
        val representation = video.representations.first()
        assertEquals(videos.first().url, representation.baseUrls.single().url)
        assertTrue(representation is Representation.SingleSegmentRepresentation)
        assertEquals(0L, representation.initializationUri!!.start)
        assertEquals(740L, representation.indexUri!!.start)
        assertEquals(1508L, representation.indexUri!!.length)
    }
}
