// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.StreamType
import app.youtubely.core.model.VideoId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RecommenderTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z")

    @Test
    fun watchedVideosAreNeverRecommended() {
        val seen = video("seen", "A")
        val result =
            Recommender.rank(
                listOf(
                    Candidate(seen, CandidateSource.Subscribed),
                    Candidate(video("new", "A"), CandidateSource.Subscribed),
                ),
                history = listOf(signal(seen, hoursAgo = 1)),
                subscribed = setOf(channel("A")),
                now = now,
                limit = 10,
            )
        assertEquals(listOf("new"), result.map { it.item.id.value })
    }

    @Test
    fun aFreshVideoOfAFollowedChannelBeatsAnOldOne() {
        val result =
            Recommender.rank(
                listOf(
                    Candidate(video("old", "A", uploadedHoursAgo = 24 * 60), CandidateSource.Subscribed),
                    Candidate(video("fresh", "A", uploadedHoursAgo = 3), CandidateSource.Subscribed),
                ),
                history = emptyList(),
                subscribed = setOf(channel("A")),
                now = now,
                limit = 10,
            )
        assertEquals(listOf("fresh", "old"), result.map { it.item.id.value })
        assertEquals(Reason.Subscription, result.first().reason)
    }

    @Test
    fun relatedVideosOfARecentFinishedVideoOutrankThoseOfAnOldOne() {
        val recent = signal(video("recent", "A"), hoursAgo = 2, progress = 1f)
        val old = signal(video("old", "B"), hoursAgo = 24 * 80, progress = 0.2f)
        val result =
            Recommender.rank(
                listOf(
                    Candidate(video("fromOld", "C"), CandidateSource.Related(old.item)),
                    Candidate(video("fromRecent", "D"), CandidateSource.Related(recent.item)),
                ),
                history = listOf(recent, old),
                subscribed = emptySet(),
                now = now,
                limit = 10,
            )
        assertEquals(
            "fromRecent",
            result
                .first()
                .item.id.value,
        )
        assertEquals(Reason.Watched("recent"), result.first().reason)
    }

    @Test
    fun aVideoListedNextToSeveralWatchedOnesRanksHigher() {
        val a = signal(video("a", "A"), hoursAgo = 5, progress = 1f)
        val b = signal(video("b", "B"), hoursAgo = 6, progress = 1f)
        val result =
            Recommender.rank(
                listOf(
                    Candidate(video("single", "X"), CandidateSource.Related(a.item)),
                    Candidate(video("both", "Y"), CandidateSource.Related(a.item)),
                    Candidate(video("both", "Y"), CandidateSource.Related(b.item)),
                ),
                history = listOf(a, b),
                subscribed = emptySet(),
                now = now,
                limit = 10,
            )
        assertEquals(listOf("both", "single"), result.map { it.item.id.value })
    }

    @Test
    fun noChannelFloodsTheList() {
        val candidates =
            (1..8).map { Candidate(video("a$it", "A", uploadedHoursAgo = it.toLong()), CandidateSource.Subscribed) } +
                Candidate(video("b1", "B", uploadedHoursAgo = 20), CandidateSource.Subscribed)
        val result =
            Recommender.rank(candidates, emptyList(), setOf(channel("A"), channel("B")), now, limit = 4)
        assertEquals(4, result.size)
        assertTrue("b1" in result.map { it.item.id.value })
        assertFalse(result.all { it.item.channel?.id == channel("A") })
    }

    @Test
    fun weightFadesWithTimeAndGrowsWithProgress() {
        val item = video("v", "A")
        val fresh = Recommender.weight(signal(item, hoursAgo = 1, progress = 1f), now)
        val stale = Recommender.weight(signal(item, hoursAgo = 24 * 60, progress = 1f), now)
        val barelyStarted = Recommender.weight(signal(item, hoursAgo = 1, progress = 0f), now)
        assertTrue(fresh > stale)
        assertTrue(fresh > barelyStarted)
    }

    private fun channel(id: String) = ChannelId("UC$id")

    private fun video(
        id: String,
        channel: String,
        uploadedHoursAgo: Long? = null,
    ) = StreamItem(
        id = VideoId(id),
        title = id,
        channel = ChannelRef(channel(channel), channel, emptyList(), false),
        thumbnails = emptyList(),
        type = StreamType.Video,
        duration = Duration.ofMinutes(10),
        viewCount = null,
        uploadedAt = uploadedHoursAgo?.let { now.minus(Duration.ofHours(it)) },
        uploadedAtText = null,
    )

    private fun signal(
        item: StreamItem,
        hoursAgo: Long,
        progress: Float? = 1f,
    ) = WatchSignal(item, now.minus(Duration.ofHours(hoursAgo)), progress)
}
