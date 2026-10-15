// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.library

import app.youtubely.core.model.AutoDownloadRule
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleLengthTest {
    @Test
    fun `a minimum above the maximum clears the maximum`() {
        val rule = AutoDownloadRule(minMinutes = 0, maxMinutes = 10).withMin(20)
        assertEquals(20, rule.minMinutes)
        assertEquals(0, rule.maxMinutes)
    }

    @Test
    fun `a maximum below the minimum clears the minimum`() {
        val rule = AutoDownloadRule(minMinutes = 20, maxMinutes = 0).withMax(10)
        assertEquals(0, rule.minMinutes)
        assertEquals(10, rule.maxMinutes)
    }

    @Test
    fun `a consistent pair is kept`() {
        val rule = AutoDownloadRule(minMinutes = 5, maxMinutes = 60).withMin(10)
        assertEquals(10, rule.minMinutes)
        assertEquals(60, rule.maxMinutes)
    }
}
