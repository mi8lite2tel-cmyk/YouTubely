// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class FormatTest {
    @Test
    fun `durations are shown as clock times`() {
        assertEquals("0:00", formatDuration(Duration.ZERO))
        assertEquals("4:05", formatDuration(Duration.ofSeconds(245)))
        assertEquals("12:34", formatDuration(Duration.ofSeconds(754)))
        assertEquals("1:02:03", formatDuration(Duration.ofSeconds(3723)))
        assertEquals("25:00:00", formatDuration(Duration.ofHours(25)))
    }
}
