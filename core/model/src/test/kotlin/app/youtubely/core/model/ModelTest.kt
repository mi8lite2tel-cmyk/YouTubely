// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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
}
