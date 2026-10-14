// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferFormatsTest {
    private val a = "UC" + "a".repeat(22)
    private val b = "UC" + "b".repeat(22)

    @Test
    fun ownFormatRoundTrips() {
        val bundle =
            TransferBundle(
                subscriptions = listOf(TransferChannel(a, "Ä “quoted”", group = "Music", notify = true)),
                playlists = listOf(TransferPlaylist("Road", listOf(TransferVideo("dQw4w9WgXcQ", "Song", a, "Ä", 212)))),
                watchLater = listOf(TransferVideo("abcdefghijk", "Later")),
            )
        assertEquals(bundle, TransferFormats.parse(TransferFormats.encode(bundle)))
    }

    @Test
    fun newPipeAndLibreTubeJson() {
        val newPipe = """{"app_version":"0.27","subscriptions":[{"service_id":0,"url":"https://www.youtube.com/channel/$a","name":"One"},
            {"service_id":1,"url":"https://soundcloud.com/x","name":"Other service"}]}"""
        assertEquals(listOf(TransferChannel(a, "One")), TransferFormats.parse(newPipe)!!.subscriptions)
        val libreTube = """{"subscriptions":[{"channelId":"$b","name":"Two"}]}"""
        assertEquals(listOf(TransferChannel(b, "Two")), TransferFormats.parse(libreTube)!!.subscriptions)
    }

    @Test
    fun takeoutCsv() {
        val csv =
            "Channel ID,Channel URL,Channel title\n" +
                "$a,http://www.youtube.com/channel/$a,\"Name, with comma\"\n" +
                "$b,http://www.youtube.com/channel/$b,Two\n"
        assertEquals(
            listOf(TransferChannel(a, "Name, with comma"), TransferChannel(b, "Two")),
            TransferFormats.parse(csv)!!.subscriptions,
        )
    }

    @Test
    fun opml() {
        val opml =
            """<?xml version="1.0"?><opml version="1.1"><body><outline text="YouTube Subscriptions" title="YouTube Subscriptions">
            <outline text="Tom &amp; Jerry" title="Tom &amp; Jerry" type="rss" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=$a"/>
            </outline></body></opml>"""
        assertEquals(listOf(TransferChannel(a, "Tom & Jerry")), TransferFormats.parse(opml)!!.subscriptions)
    }

    @Test
    fun unknownOrEmptyInputIsRejected() {
        assertNull(TransferFormats.parse("hello"))
        assertNull(TransferFormats.parse("{}"))
        assertNull(TransferFormats.parse("""{"subscriptions":[]}"""))
        assertNull(TransferFormats.parse("not json {"))
    }

    @Test
    fun qrFramesRoundTripInAnyOrderWithRepeats() {
        val random = java.util.Random(42)
        val alphabet = ('a'..'z') + ('A'..'Z') + ('0'..'9')

        fun randomText(length: Int) = String(CharArray(length) { alphabet[random.nextInt(alphabet.size)] })
        val channels = (0 until 60).map { TransferChannel("UC" + randomText(22), randomText(16)) }
        val text = TransferFormats.encode(TransferBundle(subscriptions = channels))
        val frames = QrTransfer.frames(text, session = "abc123", frameChars = 200)
        assertTrue(frames.size > 3)
        assertTrue(frames.all { it.length < 260 })

        val receiver = QrTransfer.Receiver()
        assertNull(receiver.text())
        (frames.reversed() + frames).forEach { receiver.accept(it) }
        assertTrue(receiver.isComplete)
        assertEquals(frames.size, receiver.received)
        assertEquals(text, receiver.text())
    }

    @Test
    fun receiverIgnoresForeignAndBrokenFrames() {
        val receiver = QrTransfer.Receiver()
        assertFalse(receiver.accept("https://example.com"))
        assertFalse(receiver.accept("YT1|s|5|2|data"))
        val first = QrTransfer.frames("one", session = "aaaaaa", frameChars = 1_000)
        val other = QrTransfer.frames("two", session = "bbbbbb", frameChars = 1_000)
        assertTrue(receiver.accept(first.single()))
        assertFalse(receiver.accept(other.single()))
        assertEquals("one", receiver.text())
        assertNotNull(receiver.text())
    }
}
