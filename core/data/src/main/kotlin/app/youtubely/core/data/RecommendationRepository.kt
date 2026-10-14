// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.StreamItem
import app.youtubely.core.provider.StreamProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RecommendationState {
    data object Loading : RecommendationState

    /** Neither history nor subscriptions yet: there is nothing to base recommendations on. */
    data object NeedsData : RecommendationState

    data class Ready(
        val items: List<Recommendation>,
    ) : RecommendationState

    data class Failed(
        val error: Throwable,
    ) : RecommendationState
}

/**
 * Builds "For you" on the device. It looks at the latest watched videos (what YouTube lists next
 * to them), at the subscriptions' feed and at channels the user watches often, then ranks all of
 * that with [Recommender]. The result is kept for a while so switching tabs is instant.
 */
@Singleton
class RecommendationRepository
    @Inject
    constructor(
        private val provider: StreamProvider,
        private val history: HistoryRepository,
        private val subscriptions: SubscriptionRepository,
        private val feed: SubscriptionFeed,
    ) {
        private val _state = MutableStateFlow<RecommendationState>(RecommendationState.Loading)
        val state: StateFlow<RecommendationState> = _state.asStateFlow()

        private val refreshing = Mutex()
        private var computedAt: Instant? = null
        private val relatedCache = HashMap<String, List<StreamItem>>()

        /** Whether there is anything to build recommendations from. */
        val hasSignals: Flow<Boolean> =
            combine(history.history, subscriptions.subscriptions) { watched, followed ->
                watched.isNotEmpty() || followed.isNotEmpty()
            }

        suspend fun refreshIfStale() {
            val at = computedAt
            if (at == null || Instant.now().isAfter(at.plusSeconds(STALE_AFTER_SECONDS))) refresh()
        }

        suspend fun refresh() {
            if (refreshing.isLocked) return
            refreshing.withLock {
                val now = Instant.now()
                val watched =
                    history.history.first().map { WatchSignal(it.item, it.watchedAt, it.progress) }
                val followed =
                    subscriptions.subscriptions
                        .first()
                        .map { it.id }
                        .toSet()
                if (watched.isEmpty() && followed.isEmpty()) {
                    _state.value = RecommendationState.NeedsData
                    return
                }
                if (_state.value !is RecommendationState.Ready) _state.value = RecommendationState.Loading
                val candidates = candidates(watched, followed, now)
                if (candidates.isEmpty()) {
                    // Most likely offline: keep what was shown before, if anything.
                    if (_state.value !is RecommendationState.Ready) {
                        _state.value = RecommendationState.Failed(java.io.IOException("No videos to rank"))
                    }
                    return
                }
                _state.value =
                    RecommendationState.Ready(Recommender.rank(candidates, watched, followed, now, MAX_ITEMS))
                computedAt = now
            }
        }

        private suspend fun candidates(
            watched: List<WatchSignal>,
            followed: Set<ChannelId>,
            now: Instant,
        ): List<Candidate> =
            coroutineScope {
                val seeds =
                    watched
                        .sortedByDescending { Recommender.weight(it, now) }
                        .distinctBy { it.item.id }
                        .take(SEEDS)
                val permits = Semaphore(PARALLEL_REQUESTS)
                val related =
                    seeds.map { seed ->
                        async {
                            permits
                                .withPermit {
                                    relatedOf(
                                        seed.item,
                                    )
                                }.map { Candidate(it, CandidateSource.Related(seed.item)) }
                        }
                    }
                val frequent =
                    frequentChannels(watched, followed, now).map { channel ->
                        async {
                            permits
                                .withPermit { quietly { provider.channelFeed(channel) } }
                                .orEmpty()
                                .map { Candidate(it, CandidateSource.FrequentChannel) }
                        }
                    }
                val fromFeed =
                    feed.state
                        .first()
                        .items
                        .map { Candidate(it, CandidateSource.Subscribed) }
                (related + frequent).awaitAll().flatten() + fromFeed
            }

        private suspend fun relatedOf(seed: StreamItem): List<StreamItem> =
            relatedCache[seed.id.value]
                ?: quietly { provider.streamInfo(seed.id).related }?.also { relatedCache[seed.id.value] = it }
                ?: emptyList()

        /** Channels that were watched at least twice but are not followed, most watched first. */
        private fun frequentChannels(
            watched: List<WatchSignal>,
            followed: Set<ChannelId>,
            now: Instant,
        ): List<ChannelId> =
            watched
                .mapNotNull { signal ->
                    signal.item.channel
                        ?.id
                        ?.let { it to Recommender.weight(signal, now) }
                }.filter { it.first !in followed }
                .groupBy({ it.first }, { it.second })
                .filter { it.value.size >= MIN_WATCHES }
                .entries
                .sortedByDescending { it.value.sum() }
                .take(FREQUENT_CHANNELS)
                .map { it.key }

        private suspend fun <T> quietly(block: suspend () -> T): T? =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") _: Exception,
            ) {
                null
            }

        private companion object {
            const val SEEDS = 5
            const val FREQUENT_CHANNELS = 3
            const val MIN_WATCHES = 2
            const val PARALLEL_REQUESTS = 3
            const val MAX_ITEMS = 60
            const val STALE_AFTER_SECONDS = 30 * 60L
        }
    }
