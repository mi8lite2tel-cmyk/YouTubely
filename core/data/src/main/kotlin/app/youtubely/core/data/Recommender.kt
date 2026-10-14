// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.VideoId
import java.time.Duration
import java.time.Instant
import kotlin.math.pow

/** A video the user watched, with how far and when. */
data class WatchSignal(
    val item: StreamItem,
    val watchedAt: Instant,
    /** Watched share 0..1, `null` when the length is unknown. */
    val progress: Float?,
)

/** Where a candidate for the recommendations came from. */
sealed interface CandidateSource {
    /** Listed next to [seed], a video the user watched. */
    data class Related(
        val seed: StreamItem,
    ) : CandidateSource

    /** A recent video of a subscribed channel. */
    data object Subscribed : CandidateSource

    /** A recent video of a channel the user watches a lot without following it. */
    data object FrequentChannel : CandidateSource
}

data class Candidate(
    val item: StreamItem,
    val source: CandidateSource,
)

/** Why a video was recommended, shown under it. */
sealed interface Reason {
    data class Watched(
        val title: String,
    ) : Reason

    data object Subscription : Reason

    data object FrequentChannel : Reason
}

data class Recommendation(
    val item: StreamItem,
    val reason: Reason,
    val score: Double,
)

/**
 * Ranks videos for "For you" from what is on this device only: the watch history (recent and
 * finished videos count more), the subscriptions and the videos YouTube lists next to what was
 * watched. Nothing is sent anywhere and nothing is learned about the user by anyone else.
 */
object Recommender {
    private const val HISTORY_HALF_LIFE_DAYS = 14.0
    private const val FRESH_HALF_LIFE_DAYS = 10.0
    private const val UNKNOWN_PROGRESS = 0.3f
    private const val SUBSCRIBED_AFFINITY = 1.0
    private const val RELATED_CAP = 2.0
    private const val AFFINITY_WEIGHT = 1.5
    private const val RELATED_WEIGHT = 1.2
    private const val FRESH_WEIGHT = 0.9
    private const val SUBSCRIBED_BONUS = 0.2
    private const val REPEAT_PENALTY = 0.65

    /** How much a watched video says about the user's taste: recent and watched-to-the-end counts most. */
    fun weight(
        signal: WatchSignal,
        now: Instant,
    ): Double {
        val ageDays = Duration.between(signal.watchedAt, now).toHours().coerceAtLeast(0) / HOURS_PER_DAY
        val finished = 0.5 + (signal.progress ?: UNKNOWN_PROGRESS).coerceIn(0f, 1f)
        return 0.5.pow(ageDays / HISTORY_HALF_LIFE_DAYS) * finished
    }

    fun rank(
        candidates: List<Candidate>,
        history: List<WatchSignal>,
        subscribed: Set<ChannelId>,
        now: Instant,
        limit: Int,
    ): List<Recommendation> {
        val watched = history.mapTo(HashSet()) { it.item.id }
        val affinity = affinityOf(history, subscribed, now)
        val strongest = affinity.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val weights = history.associate { it.item.id to weight(it, now) }

        val scored =
            candidates
                .filterNot { it.item.id in watched }
                .groupBy { it.item.id }
                .map { (_, group) -> score(group, affinity, strongest, weights, subscribed, now) }
                .sortedByDescending { it.score }
        return diversify(scored, limit)
    }

    private fun affinityOf(
        history: List<WatchSignal>,
        subscribed: Set<ChannelId>,
        now: Instant,
    ): Map<ChannelId, Double> {
        val result = HashMap<ChannelId, Double>()
        history.forEach { signal ->
            val channel = signal.item.channel?.id ?: return@forEach
            result.merge(channel, weight(signal, now), Double::plus)
        }
        subscribed.forEach { result.merge(it, SUBSCRIBED_AFFINITY, Double::plus) }
        return result
    }

    private fun score(
        group: List<Candidate>,
        affinity: Map<ChannelId, Double>,
        strongest: Double,
        weights: Map<VideoId, Double>,
        subscribed: Set<ChannelId>,
        now: Instant,
    ): Recommendation {
        val item = group.first().item
        val channel = item.channel?.id
        val related = group.mapNotNull { it.source as? CandidateSource.Related }
        val relatedSum = related.sumOf { weights[it.seed.id] ?: 0.0 }
        val seed = related.maxByOrNull { weights[it.seed.id] ?: 0.0 }?.seed
        val affinityShare = ((channel?.let { affinity[it] } ?: 0.0) / strongest).coerceIn(0.0, 1.0)
        val relatedShare = (relatedSum / RELATED_CAP).coerceIn(0.0, 1.0)
        val fresh =
            item.uploadedAt?.let {
                0.5.pow(Duration.between(it, now).toHours().coerceAtLeast(0) / HOURS_PER_DAY / FRESH_HALF_LIFE_DAYS)
            } ?: 0.0
        val isSubscribed = channel != null && channel in subscribed
        val score =
            AFFINITY_WEIGHT * affinityShare +
                RELATED_WEIGHT * relatedShare +
                FRESH_WEIGHT * fresh +
                (if (isSubscribed) SUBSCRIBED_BONUS else 0.0)
        val reason =
            when {
                seed != null && RELATED_WEIGHT * relatedShare >= AFFINITY_WEIGHT * affinityShare -> {
                    Reason.Watched(
                        seed.title,
                    )
                }

                isSubscribed -> {
                    Reason.Subscription
                }

                seed != null -> {
                    Reason.Watched(seed.title)
                }

                else -> {
                    Reason.FrequentChannel
                }
            }
        return Recommendation(item, reason, score)
    }

    /** Picks the best by score, but every extra video of a channel already chosen counts for less. */
    private fun diversify(
        sorted: List<Recommendation>,
        limit: Int,
    ): List<Recommendation> {
        val pool = sorted.toMutableList()
        val chosen = mutableListOf<Recommendation>()
        val perChannel = HashMap<ChannelId?, Int>()
        while (chosen.size < limit && pool.isNotEmpty()) {
            val best =
                pool.maxBy { it.score * REPEAT_PENALTY.pow(perChannel[it.item.channel?.id] ?: 0) }
            pool.remove(best)
            chosen += best
            perChannel.merge(best.item.channel?.id, 1, Int::plus)
        }
        return chosen
    }

    private const val HOURS_PER_DAY = 24.0
}
