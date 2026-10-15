// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {
    @Test
    fun `region accepts ISO codes only`() {
        assertEquals("DE", Region("DE").code)
        assertFailsWith<IllegalArgumentException> { Region("de") }
        assertFailsWith<IllegalArgumentException> { Region("DEU") }
    }

    @Test
    fun `ids reject blank values`() {
        assertFailsWith<IllegalArgumentException> { VideoId(" ") }
        assertFailsWith<IllegalArgumentException> { ChannelId("") }
        assertFailsWith<IllegalArgumentException> { PlaylistId("") }
    }

    @Test
    fun `bestFor picks the smallest image wide enough`() {
        val images = listOf(Image("l", 1280, 720), Image("s", 120, 90), Image("m", 480, 360))
        assertEquals("m", images.bestFor(300)?.url)
        assertEquals("s", images.bestFor(100)?.url)
    }

    @Test
    fun `bestFor falls back to the largest or an unsized image`() {
        assertEquals("l", listOf(Image("s", 120, 90), Image("l", 1280, 720)).bestFor(4000)?.url)
        assertEquals("u", listOf(Image("u", null, null)).bestFor(100)?.url)
        assertNull(emptyList<Image>().bestFor(100))
    }

    @Test
    fun `duration filter keeps only matching videos`() {
        fun video(minutes: Long?) =
            SearchItem.Stream(
                StreamItem(
                    id = VideoId("id"),
                    title = "t",
                    channel = null,
                    thumbnails = emptyList(),
                    type = StreamType.Video,
                    duration = minutes?.let(Duration::ofMinutes),
                    viewCount = null,
                    uploadedAt = null,
                    uploadedAtText = null,
                ),
            )
        val short = SearchFilter.DurationFilter.Short
        assertTrue(short.matches(video(3)))
        assertFalse(short.matches(video(10)))
        assertFalse(short.matches(video(null)))
        assertTrue(SearchFilter.DurationFilter.Long.matches(video(45)))
        assertTrue(SearchFilter.DurationFilter.Any.matches(video(null)))
    }

    @Test
    fun `a typed sponsorblock server is checked, not silently replaced`() {
        assertEquals("", SponsorBlockSettings.normalizeServer("   "))
        assertEquals("https://sb.example.com", SponsorBlockSettings.normalizeServer(" https://sb.example.com/ "))
        assertNull(SponsorBlockSettings.normalizeServer("sb.example.com"))
        assertNull(SponsorBlockSettings.normalizeServer("http://sb.example.com"))
        assertNull(SponsorBlockSettings.normalizeServer("https://"))
    }
}
