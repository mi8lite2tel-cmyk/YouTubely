// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchStatsTest {
    private val now = Instant.parse("2026-10-14T20:00:00Z")
    private val alpha = ChannelRef(ChannelId("a"), "Alpha", emptyList(), false)
    private val beta = ChannelRef(ChannelId("b"), "Beta", emptyList(), false)

    private fun entry(
        id: String,
        channel: ChannelRef?,
        minutesWatched: Long,
        lengthMinutes: Long,
        hoursAgo: Long,
    ) = HistoryEntry(
        StreamItem(
            VideoId(id),
            id,
            channel,
            emptyList(),
            StreamType.Video,
            Duration.ofMinutes(lengthMinutes),
            null,
            null,
            null,
        ),
        positionMs = Duration.ofMinutes(minutesWatched).toMillis(),
        watchedAt = now.minus(Duration.ofHours(hoursAgo)),
    )

    @Test
    fun `counts periods, time capped by length, top channels and days`() {
        val history =
            listOf(
                entry("1", alpha, 10, 20, hoursAgo = 1),
                entry("2", alpha, 50, 30, hoursAgo = 30),
                entry("3", beta, 5, 10, hoursAgo = 24 * 10),
                entry("4", beta, 60, 60, hoursAgo = 24 * 40),
            )
        val stats = WatchStats.from(history, now, ZoneOffset.UTC)
        assertEquals(2, stats.videosWeek)
        assertEquals(3, stats.videosMonth)
        assertEquals(4, stats.videosTotal)
        assertEquals(Duration.ofMinutes(40), stats.timeWeek)
        assertEquals(Duration.ofMinutes(45), stats.timeMonth)
        assertEquals(listOf("Beta", "Alpha"), stats.topChannels.map { it.channel.name })
        assertEquals(Duration.ofMinutes(65), stats.topChannels.first().time)
        assertEquals(WatchStats.DAYS, stats.days.size)
        assertEquals(LocalDate.of(2026, 10, 14) to 1, stats.days.last())
        assertEquals(LocalDate.of(2026, 10, 13) to 1, stats.days[WatchStats.DAYS - 2])
    }

    @Test
    fun `empty history`() {
        val stats = WatchStats.from(emptyList(), now, ZoneOffset.UTC)
        assertEquals(0, stats.videosTotal)
        assertNull(stats.busiestHour)
        assertEquals(Duration.ZERO, stats.timeMonth)
    }
}
