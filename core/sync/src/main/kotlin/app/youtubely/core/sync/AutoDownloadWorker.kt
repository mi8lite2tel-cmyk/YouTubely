// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.youtubely.core.data.AutoDownloadLog
import app.youtubely.core.data.SubscriptionFeed
import app.youtubely.core.datastore.SyncSettingsRepository
import app.youtubely.core.download.DownloadManager
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.FreshVideo
import app.youtubely.core.model.SyncSettings
import app.youtubely.core.provider.ProviderError
import app.youtubely.core.provider.StreamProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * Saves new videos of channels with automatic download for offline viewing, on Wi-Fi (and while
 * charging when asked to), within the storage limit, keeping only each channel's latest few.
 */
@HiltWorker
internal class AutoDownloadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val feed: SubscriptionFeed,
        private val provider: StreamProvider,
        private val downloads: DownloadManager,
        private val log: AutoDownloadLog,
        private val settings: SyncSettingsRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val current = settings.settings.first()
            val videos = feed.freshToDownload().filter { it.subscription.autoDownload }
            var retry = false
            for (video in videos) {
                if (AutoDownloadPolicy.limitReached(downloads.storage().usedBytes, current.autoDownloadLimitGb)) break
                when (save(video, current)) {
                    Outcome.Saved, Outcome.Skipped -> feed.markDownloadHandled(listOf(video))
                    Outcome.Retry -> retry = true
                }
            }
            videos.map { it.subscription.channel.id }.distinct().forEach { trim(it, current) }
            return if (retry && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }

        private enum class Outcome { Saved, Skipped, Retry }

        private suspend fun save(
            video: FreshVideo,
            current: SyncSettings,
        ): Outcome =
            try {
                val info = provider.streamInfo(video.item.id)
                val format = AutoDownloadPolicy.choose(downloads.formats(info), current.autoDownloadMaxHeight)
                if (format == null) {
                    Outcome.Skipped
                } else {
                    val download = downloads.start(info, format, unmeteredOnly = true)
                    log.record(video.item.id, video.subscription.channel.id, download.id)
                    Outcome.Saved
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                Outcome.Retry
            } catch (e: ProviderError.Network) {
                Outcome.Retry.also { e.cause }
            } catch (
                @Suppress("TooGenericExceptionCaught") _: Exception,
            ) {
                // Removed, private, paid or age-restricted: nothing to save, do not try again.
                Outcome.Skipped
            }

        /** Removes the automatic downloads of [channel] beyond the number to keep. */
        private suspend fun trim(
            channel: ChannelId,
            current: SyncSettings,
        ) {
            AutoDownloadPolicy.expired(log.forChannel(channel), current.autoDownloadKeepLast).forEach {
                downloads.delete(it.downloadId)
                log.forget(it.videoId)
            }
        }

        companion object {
            private const val NAME = "auto-download"
            private const val MAX_ATTEMPTS = 3

            fun enqueue(
                context: Context,
                settings: SyncSettings,
            ) {
                val request =
                    OneTimeWorkRequestBuilder<AutoDownloadWorker>()
                        .setConstraints(
                            Constraints
                                .Builder()
                                .setRequiredNetworkType(NetworkType.UNMETERED)
                                .setRequiresCharging(settings.autoDownloadOnlyCharging)
                                .setRequiresStorageNotLow(true)
                                .build(),
                        ).build()
                WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
            }
        }
    }
