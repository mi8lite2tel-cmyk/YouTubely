// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.youtubely.core.data.SubscriptionFeed
import app.youtubely.core.data.SubscriptionRepository
import app.youtubely.core.datastore.SyncSettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Loads the feeds of all subscribed channels, announces new videos of the channels that asked for
 * it and hands the ones of "automatic download" channels to [AutoDownloadWorker].
 */
@HiltWorker
internal class FeedSyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val feed: SubscriptionFeed,
        private val settings: SyncSettingsRepository,
        private val subscriptions: SubscriptionRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            feed.refresh()
            // Every channel failed: probably no network, try again with back-off.
            if (feed.state.first().error != null && runAttemptCount < MAX_ATTEMPTS) return Result.retry()

            val current = settings.settings.first()
            val announce = feed.freshToAnnounce()
            if (current.notificationsEnabled) {
                NewVideoNotifications.show(applicationContext, announce.filter { it.subscription.notify })
            }
            feed.markAnnounced(announce)

            val download = feed.freshToDownload()
            feed.markDownloadHandled(download.filterNot { it.subscription.autoDownload })
            // Also runs without new videos when watched downloads may have to go.
            val cleanUp = subscriptions.details.first().any { it.autoDownload && it.autoRule.deleteWatched }
            if (cleanUp ||
                download.any { it.subscription.autoDownload }
            ) {
                AutoDownloadWorker.enqueue(applicationContext, current)
            }
            return Result.success()
        }

        private companion object {
            const val MAX_ATTEMPTS = 3
        }
    }
