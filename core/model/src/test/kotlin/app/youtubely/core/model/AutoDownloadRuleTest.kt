// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoDownloadRuleTest {
    private fun item(
        title: String = "Weekly update",
        type: StreamType = StreamType.Video,
        minutes: Long? = 12,
    ) = StreamItem(VideoId("v"), title, null, emptyList(), type, minutes?.let(Duration::ofMinutes), null, null, null)

    @Test
    fun `default rule saves regular videos and skips shorts and live`() {
        val rule = AutoDownloadRule()
        assertNull(rule.skipReason(item()))
        assertEquals(SkipReason.Short, rule.skipReason(item(type = StreamType.Short)))
        assertEquals(SkipReason.Live, rule.skipReason(item(type = StreamType.Live)))
        assertEquals(SkipReason.Live, rule.skipReason(item(type = StreamType.Upcoming)))
        assertNull(AutoDownloadRule(skipShorts = false).skipReason(item(type = StreamType.Short)))
    }

    @Test
    fun `duration limits apply only when the length is known`() {
        val rule = AutoDownloadRule(minMinutes = 5, maxMinutes = 30)
        assertEquals(SkipReason.TooShort, rule.skipReason(item(minutes = 2)))
        assertEquals(SkipReason.TooLong, rule.skipReason(item(minutes = 90)))
        assertNull(rule.skipReason(item(minutes = 10)))
        assertNull(rule.skipReason(item(minutes = null)))
    }

    @Test
    fun `words match case-insensitively`() {
        val rule = AutoDownloadRule(includeWords = listOf("Podcast", "episode"), excludeWords = listOf("trailer"))
        assertNull(rule.skipReason(item("The PODCAST #12")))
        assertEquals(SkipReason.MissingWord, rule.skipReason(item("Vlog")))
        assertEquals(SkipReason.ExcludedWord, rule.skipReason(item("Episode trailer")))
    }

    @Test
    fun `words are parsed from lines and commas`() {
        assertEquals(listOf("a b", "c"), AutoDownloadRule.parseWords(" a b \n\nc, C ,"))
        assertNull(AutoDownloadRule.joinWords(emptyList()))
    }

    @Test
    fun `time window wraps past midnight`() {
        val night = TimeWindow(22, 7)
        assertTrue(night.contains(23))
        assertTrue(night.contains(3))
        assertFalse(night.contains(12))
        assertEquals(0, night.minutesUntilOpen(2, 30))
        assertEquals(9 * 60 + 30, night.minutesUntilOpen(12, 30))
        assertTrue(TimeWindow(5, 5).contains(17))
        assertEquals(30, TimeWindow(9, 18).minutesUntilOpen(8, 30))
    }
}
