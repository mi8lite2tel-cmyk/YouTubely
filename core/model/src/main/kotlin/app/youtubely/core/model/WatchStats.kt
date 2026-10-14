// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A channel with how much of it was watched. */
data class ChannelStat(
    val channel: ChannelRef,
    val videos: Int,
    val time: Duration,
)

/**
 * What the local history says about watching habits. Times are estimates: the history keeps how
 * far each video got, not every minute spent, so re-watching counts once.
 */
data class WatchStats(
    val videosWeek: Int,
    val videosMonth: Int,
    val videosTotal: Int,
    val timeWeek: Duration,
    val timeMonth: Duration,
    /** Most watched channels of the last 90 days, by time. */
    val topChannels: List<ChannelStat>,
    /** Videos opened on each of the last [DAYS] days, oldest first. */
    val days: List<Pair<LocalDate, Int>>,
    /** Hour of the day (0–23) with the most videos opened, `null` without history. */
    val busiestHour: Int?,
) {
    companion object {
        const val DAYS = 14
        private const val TOP = 5
        private const val WEEK = 7L
        private const val MONTH = 30L
        private const val QUARTER = 90L

        fun from(
            history: List<HistoryEntry>,
            now: Instant,
            zone: ZoneId = ZoneId.systemDefault(),
        ): WatchStats {
            fun since(days: Long) = history.filter { !it.watchedAt.isBefore(now.minus(Duration.ofDays(days))) }
            val week = since(WEEK)
            val month = since(MONTH)
            val today = now.atZone(zone).toLocalDate()
            val perDay = history.groupingBy { it.watchedAt.atZone(zone).toLocalDate() }.eachCount()
            return WatchStats(
                videosWeek = week.size,
                videosMonth = month.size,
                videosTotal = history.size,
                timeWeek = week.watchedTime(),
                timeMonth = month.watchedTime(),
                topChannels =
                    since(QUARTER)
                        .filter { it.item.channel != null }
                        .groupBy { it.item.channel!!.id }
                        .map { (_, entries) ->
                            ChannelStat(entries.first().item.channel!!, entries.size, entries.watchedTime())
                        }.sortedWith(compareByDescending<ChannelStat> { it.time }.thenByDescending { it.videos })
                        .take(TOP),
                days =
                    (DAYS - 1 downTo 0).map { back ->
                        today.minusDays(back.toLong()).let { it to (perDay[it] ?: 0) }
                    },
                busiestHour =
                    history
                        .groupingBy { it.watchedAt.atZone(zone).hour }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key,
            )
        }

        /** How far each video got, never more than its length. */
        private fun List<HistoryEntry>.watchedTime(): Duration =
            Duration.ofMillis(
                sumOf { entry ->
                    val length = entry.item.duration?.toMillis() ?: entry.positionMs
                    entry.positionMs.coerceIn(0, length)
                },
            )
    }
}
