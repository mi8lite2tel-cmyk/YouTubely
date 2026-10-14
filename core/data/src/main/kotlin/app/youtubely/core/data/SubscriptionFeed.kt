// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.database.FeedDao
import app.youtubely.core.database.FeedItemEntity
import app.youtubely.core.database.SubscriptionDao
import app.youtubely.core.database.SubscriptionEntity
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.FreshVideo
import app.youtubely.core.model.StreamItem
import app.youtubely.core.provider.StreamProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
 * New videos from subscribed channels, newest first, kept in the local database so the feed is
 * there offline and between launches. A refresh uses each channel's public RSS feed: one light
 * request per channel, no account and no recommendation tracking.
 */
@Singleton
class SubscriptionFeed
    @Inject
    constructor(
        private val provider: StreamProvider,
        private val subscriptions: SubscriptionRepository,
        private val subscriptionDao: SubscriptionDao,
        private val dao: FeedDao,
    ) {
        private data class Status(
            val loading: Boolean = false,
            val failed: List<ChannelRef> = emptyList(),
            val updatedAt: Instant? = null,
            val error: Throwable? = null,
        )

        private val status = MutableStateFlow(Status())
        private val refreshing = Mutex()

        val state: Flow<FeedState> =
            combine(dao.observeLatest(MAX_ITEMS), status) { rows, status ->
                FeedState(
                    items = rows.map { it.video.toItem() },
                    loading = status.loading,
                    failed = status.failed,
                    updatedAt = status.updatedAt,
                    error = status.error,
                )
            }

        /** Videos found after [since] (epoch millis). */
        fun newCountSince(since: Long): Flow<Int> = dao.observeCountSince(since)

        /** Reloads unless a load finished less than [maxAgeMillis] ago. */
        suspend fun refreshIfStale(maxAgeMillis: Long = STALE_AFTER_MS) {
            val updated = status.value.updatedAt
            if (updated == null || Instant.now().toEpochMilli() - updated.toEpochMilli() > maxAgeMillis) refresh()
        }

        suspend fun refresh() {
            if (refreshing.isLocked) return
            refreshing.withLock {
                status.update { it.copy(loading = true) }
                val channels = subscriptionDao.getAll()
                val permits = Semaphore(PARALLEL_REQUESTS)
                val failed =
                    coroutineScope {
                        channels
                            .map { channel -> async { channel to permits.withPermit { load(channel) } } }
                            .awaitAll()
                    }.filter { it.second.isFailure }
                dao.prune(MAX_STORED)
                status.value =
                    Status(
                        failed = failed.map { it.first.toChannel() },
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

        /** Videos found by refreshes that background sync has not looked at yet. */
        suspend fun fresh(): List<FreshVideo> {
            val bySubscription = subscriptionDao.getAll().associateBy { it.channelId }
            return dao.pending().mapNotNull { row ->
                val subscription = bySubscription[row.video.channelId] ?: return@mapNotNull null
                FreshVideo(row.video.toItem(), subscription.toSubscription())
            }
        }

        suspend fun markHandled(videos: List<FreshVideo>) {
            if (videos.isNotEmpty()) dao.clearPending(videos.map { it.item.id.value })
        }

        private suspend fun load(channel: SubscriptionEntity): Result<Unit> =
            try {
                // The RSS feed names the channel but carries no avatar; reuse the stored one.
                val items = provider.channelFeed(channelId(channel)).map { it.copy(channel = channel.toChannel()) }
                store(channel, items)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Result.failure(e)
            }

        private suspend fun store(
            channel: SubscriptionEntity,
            items: List<StreamItem>,
        ) {
            val now = System.currentTimeMillis()
            // The first load only fills the feed; its videos are not "new" and trigger nothing.
            val seeding = channel.lastRefreshedAt == null
            val recent = now - FRESH_WINDOW_MS
            dao.insertAll(
                items.map { item ->
                    val snapshot = item.toSnapshot()
                    FeedItemEntity(
                        video = snapshot,
                        firstSeenAt = now,
                        pending = !seeding && (snapshot.uploadedAt ?: now) >= recent,
                    )
                },
            )
            subscriptionDao.markRefreshed(channel.channelId, now)
        }

        private fun channelId(channel: SubscriptionEntity) =
            app.youtubely.core.model
                .ChannelId(channel.channelId)

        private companion object {
            const val PARALLEL_REQUESTS = 6
            const val MAX_ITEMS = 300
            const val MAX_STORED = 600
            const val STALE_AFTER_MS = 15 * 60 * 1_000L

            /** A video older than this is not announced even when it was just discovered. */
            const val FRESH_WINDOW_MS = 3 * 24 * 60 * 60 * 1_000L
        }
    }
