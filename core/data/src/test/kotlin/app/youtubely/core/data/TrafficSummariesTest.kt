// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.database.TrafficDay
import app.youtubely.core.database.TrafficSum
import app.youtubely.core.model.TrafficKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TrafficSummariesTest {
    private val today = LocalDate.of(2026, 10, 15)

    @Test
    fun `splits the month into mobile, wifi and kinds`() {
        val summary =
            TrafficSummaries.build(
                sums =
                    listOf(
                        TrafficSum(metered = true, kind = "Video", bytes = 100),
                        TrafficSum(metered = false, kind = "Video", bytes = 400),
                        TrafficSum(metered = false, kind = "Download", bytes = 50),
                        TrafficSum(metered = true, kind = "Unknown-kind-from-a-newer-version", bytes = 7),
                    ),
                days = emptyList(),
                today = today,
            )
        assertEquals(107L, summary.monthMobile)
        assertEquals(450L, summary.monthWifi)
        assertEquals(557L, summary.monthTotal)
        assertEquals(500L, summary.monthByKind[TrafficKind.Video])
        assertEquals(50L, summary.monthByKind[TrafficKind.Download])
        // A kind this version does not know is counted as "other" instead of crashing.
        assertEquals(7L, summary.monthByKind[TrafficKind.Other])
    }

    @Test
    fun `shows the last days oldest first, including quiet ones`() {
        val summary =
            TrafficSummaries.build(
                sums = emptyList(),
                days =
                    listOf(
                        TrafficDay(today.toEpochDay(), metered = true, bytes = 10),
                        TrafficDay(today.toEpochDay(), metered = false, bytes = 20),
                        TrafficDay(today.minusDays(1).toEpochDay(), metered = false, bytes = 5),
                        // Older than the chart: not shown.
                        TrafficDay(today.minusDays(40).toEpochDay(), metered = true, bytes = 999),
                    ),
                today = today,
            )
        assertEquals(TrafficSummaries.DAYS_SHOWN, summary.days.size)
        assertEquals(today, summary.days.last().date)
        assertEquals(today.minusDays(TrafficSummaries.DAYS_SHOWN - 1L), summary.days.first().date)
        assertEquals(10L, summary.days.last().mobile)
        assertEquals(20L, summary.days.last().wifi)
        assertEquals(5L, summary.days[summary.days.lastIndex - 1].total)
        assertEquals(0L, summary.days.first().total)
    }
}
