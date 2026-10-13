// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.StreamItem
import app.youtubely.core.provider.StreamProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class FeedState(
    val items: List<StreamItem> = emptyList(),
    val loading: Boolean = false,
    /** Channels whose feed could not be loaded on the last refresh. */
    val failed: List<ChannelRef> = emptyList(),
    val updatedAt: Instant? = null,
    /** Every channel failed: most likely no network. */
    val error: Throwable? = null,
)

/**
 * New videos from subscribed channels, newest first. Uses each channel's public RSS feed: one
 * light request per channel, no account and no recommendation tracking.
 */
@Singleton
class SubscriptionFeed
    @Inject
    constructor(
        private val provider: StreamProvider,
        private val subscriptions: SubscriptionRepository,
    ) {
        private val _state = MutableStateFlow(FeedState())
        val state: StateFlow<FeedState> = _state.asStateFlow()

        private val refreshing = Mutex()

        /** Reloads unless a load finished less than [maxAgeMillis] ago. */
        suspend fun refreshIfStale(maxAgeMillis: Long = STALE_AFTER_MS) {
            val updated = _state.value.updatedAt
            if (updated == null || Instant.now().toEpochMilli() - updated.toEpochMilli() > maxAgeMillis) refresh()
        }

        suspend fun refresh() {
            if (refreshing.isLocked) return
            refreshing.withLock {
                _state.update { it.copy(loading = true) }
                val channels = subscriptions.subscriptions.first()
                val permits = Semaphore(PARALLEL_REQUESTS)
                val results =
                    coroutineScope {
                        channels
                            .map { channel ->
                                async { channel to permits.withPermit { load(channel) } }
                            }.awaitAll()
                    }
                val failed = results.filter { it.second.isFailure }
                _state.value =
                    FeedState(
                        items =
                            results
                                .flatMap { it.second.getOrDefault(emptyList()) }
                                .distinctBy { it.id }
                                .sortedByDescending { it.uploadedAt }
                                .take(MAX_ITEMS),
                        failed = failed.map { it.first },
                        updatedAt = Instant.now(),
                        error =
                            if (channels.isNotEmpty() && failed.size == channels.size) {
                                failed.first().second.exceptionOrNull()
                            } else {
                                null
                            },
                    )
            }
        }

        private suspend fun load(channel: ChannelRef): Result<List<StreamItem>> =
            try {
                // The RSS feed names the channel but carries no avatar; reuse the stored one.
                Result.success(provider.channelFeed(channel.id).map { it.copy(channel = channel) })
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Result.failure(e)
            }

        private companion object {
            const val PARALLEL_REQUESTS = 6
            const val MAX_ITEMS = 300
            const val STALE_AFTER_MS = 15 * 60 * 1_000L
        }
    }
