// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.watch

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DescriptionLinksTest {
    @Test
    fun timecodes() {
        assertEquals(754_000L, timecodeMillis("12:34"))
        assertEquals(3_723_000L, timecodeMillis("1:02:03"))
        assertEquals(5_000L, timecodeMillis("0:05"))
        assertNull(timecodeMillis("12:75"))
        assertNull(timecodeMillis("1:75:00"))
        assertNull(timecodeMillis("12"))
    }

    @Test
    fun findsTimecodesAndWebLinksOnly() {
        val text = "Intro 0:00\n1:02:03 Part two\nSee https://example.com/watch?t=1:23 and 7:77 nonsense"
        val result = linkify(text, Color.Blue) {}
        val links = result.getLinkAnnotations(0, text.length)
        // 0:00, 1:02:03 and the web address; the 1:23 inside the address and 7:77 do not count.
        assertEquals(3, links.size)
    }
}
