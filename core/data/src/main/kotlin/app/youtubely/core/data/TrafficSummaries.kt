// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.database.TrafficDay
import app.youtubely.core.database.TrafficSum
import app.youtubely.core.model.DayTraffic
import app.youtubely.core.model.TrafficKind
import app.youtubely.core.model.TrafficSummary
import java.time.LocalDate

/** Turns the database's sums into what the Traffic screen shows. Pure, so the day and month maths is tested. */
internal object TrafficSummaries {
    /** Number of days the bar chart covers. */
    const val DAYS_SHOWN = 14

    fun build(
        sums: List<TrafficSum>,
        days: List<TrafficDay>,
        today: LocalDate,
    ): TrafficSummary {
        val byDay = days.groupBy { it.day }
        val shown =
            (DAYS_SHOWN - 1 downTo 0).map { back ->
                val date = today.minusDays(back.toLong())
                val rows = byDay[date.toEpochDay()].orEmpty()
                DayTraffic(
                    date = date,
                    mobile = rows.filter { it.metered }.sumOf { it.bytes },
                    wifi = rows.filterNot { it.metered }.sumOf { it.bytes },
                )
            }
        return TrafficSummary(
            monthMobile = sums.filter { it.metered }.sumOf { it.bytes },
            monthWifi = sums.filterNot { it.metered }.sumOf { it.bytes },
            monthByKind =
                sums
                    .groupBy { runCatching { TrafficKind.valueOf(it.kind) }.getOrDefault(TrafficKind.Other) }
                    .mapValues { (_, rows) -> rows.sumOf { it.bytes } },
            days = shown,
        )
    }
}
