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
import app.youtubely.core.data.HistoryRepository
import app.youtubely.core.data.SubscriptionFeed
import app.youtubely.core.data.SubscriptionRepository
import app.youtubely.core.datastore.SyncSettingsRepository
import app.youtubely.core.download.DownloadManager
import app.youtubely.core.model.FreshVideo
import app.youtubely.core.model.Subscription
import app.youtubely.core.model.SyncSettings
import app.youtubely.core.model.VideoId
import app.youtubely.core.provider.ProviderError
import app.youtubely.core.provider.StreamProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Saves new videos of channels with automatic download for offline viewing, on Wi-Fi (and while
 * charging when asked to), inside the chosen time window, within the storage limit and the
 * per-run limit. Each channel's rule decides what is wanted (quality, length, words, Shorts, live)
 * and how many to keep; watched videos go away when the rule says so.
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
        private val subscriptionRepository: SubscriptionRepository,
        private val history: HistoryRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val current = settings.settings.first()
            val wait = minutesUntilWindow(current)
            if (wait > 0) {
                enqueue(applicationContext, current, delayMinutes = wait)
                return Result.success()
            }
            val subscriptions = subscriptionRepository.details.first().filter { it.autoDownload }
            val progress = history.progress.first()
            subscriptions.forEach { cleanUp(it, current, progress) }

            val videos = feed.freshToDownload().filter { it.subscription.autoDownload }
            val run = saveAll(videos, current, progress)
            videos.map { it.subscription }.distinctBy { it.channel.id }.forEach { cleanUp(it, current, progress) }
            return when {
                run.retry && runAttemptCount < MAX_ATTEMPTS -> Result.retry()

                // The rest of a burst of uploads waits for the next run instead of filling the phone at once.
                run.full -> Result.success().also { enqueue(applicationContext, current, NEXT_RUN_MINUTES) }

                else -> Result.success()
            }
        }

        private enum class Outcome { Saved, Skipped, Retry }

        /** How a run went: some saves should be tried again, or the per-run limit stopped it. */
        private class RunResult(
            val retry: Boolean,
            val full: Boolean,
        )

        private suspend fun saveAll(
            videos: List<FreshVideo>,
            current: SyncSettings,
            progress: Map<VideoId, Float>,
        ): RunResult {
            var saved = 0
            var retry = false
            for (video in videos) {
                val stop =
                    AutoDownloadPolicy.runFull(saved, current.autoDownloadPerRun) ||
                        AutoDownloadPolicy.limitReached(downloads.storage().usedBytes, current.autoDownloadLimitGb)
                if (stop) return RunResult(retry, full = AutoDownloadPolicy.runFull(saved, current.autoDownloadPerRun))
                when (save(video, current, progress)) {
                    Outcome.Saved -> saved++
                    Outcome.Skipped -> Unit
                    Outcome.Retry -> retry = true
                }
            }
            return RunResult(retry, full = false)
        }

        /** Saves [video] when the channel's rule wants it; a video it does not want is handled as well. */
        private suspend fun save(
            video: FreshVideo,
            current: SyncSettings,
            progress: Map<VideoId, Float>,
        ): Outcome {
            val rule = video.subscription.autoRule
            val watched = current.autoDownloadSkipWatched && video.item.id in progress
            val outcome =
                if (rule.skipReason(video.item) != null || watched) Outcome.Skipped else fetch(video, current)
            if (outcome != Outcome.Retry) feed.markDownloadHandled(listOf(video))
            return outcome
        }

        private suspend fun fetch(
            video: FreshVideo,
            current: SyncSettings,
        ): Outcome =
            try {
                val info = provider.streamInfo(video.item.id)
                // The feed may not know the length or type yet; the full info does.
                if (video.subscription.autoRule.skipReason(info.item) != null) return Outcome.Skipped
                val height = AutoDownloadPolicy.maxHeight(video.subscription.autoRule, current)
                val format = AutoDownloadPolicy.choose(downloads.formats(info), height)
                if (format == null) {
                    Outcome.Skipped
                } else {
                    // A download the user started by hand stays theirs: it must not be cleaned up later.
                    val manual = downloads.isActive(info.item.id, format)
                    val download = downloads.start(info, format, unmeteredOnly = true)
                    if (!manual) log.record(video.item.id, video.subscription.channel.id, download.id)
                    Outcome.Saved
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                Outcome.Retry
            } catch (e: ProviderError) {
                // Throttled or broken for now: try again later. Removed, private, paid, age-restricted
                // or blocked videos will never work, so they are not tried again.
                if (e.isTemporary()) Outcome.Retry else Outcome.Skipped
            } catch (
                @Suppress("TooGenericExceptionCaught") _: Exception,
            ) {
                Outcome.Skipped
            }

        private fun ProviderError.isTemporary() =
            this is ProviderError.Network ||
                this is ProviderError.RateLimited ||
                this is ProviderError.ExtractorOutdated ||
                this is ProviderError.Unknown

        /** Removes watched downloads (when the rule says so) and those beyond the number to keep. */
        private suspend fun cleanUp(
            subscription: Subscription,
            current: SyncSettings,
            progress: Map<VideoId, Float>,
        ) {
            val channel = subscription.channel.id
            val rule = subscription.autoRule
            val all = log.forChannel(channel)
            val watched = if (rule.deleteWatched) AutoDownloadPolicy.watched(all, progress) else emptyList()
            val expired = AutoDownloadPolicy.expired(all - watched.toSet(), AutoDownloadPolicy.keepLast(rule, current))
            (watched + expired).forEach {
                downloads.delete(it.downloadId)
                log.forget(it.videoId)
            }
        }

        private fun minutesUntilWindow(current: SyncSettings): Int {
            val window = current.autoDownloadWindow ?: return 0
            val now = LocalTime.now()
            return window.minutesUntilOpen(now.hour, now.minute)
        }

        companion object {
            private const val NAME = "auto-download"
            private const val MAX_ATTEMPTS = 3
            private const val NEXT_RUN_MINUTES = 60

            fun enqueue(
                context: Context,
                settings: SyncSettings,
                delayMinutes: Int = 0,
            ) {
                val now = LocalTime.now()
                val wait = maxOf(delayMinutes, settings.autoDownloadWindow?.minutesUntilOpen(now.hour, now.minute) ?: 0)
                val request =
                    OneTimeWorkRequestBuilder<AutoDownloadWorker>()
                        .setInitialDelay(wait.toLong(), TimeUnit.MINUTES)
                        .setConstraints(
                            Constraints
                                .Builder()
                                .setRequiredNetworkType(NetworkType.UNMETERED)
                                .setRequiresCharging(settings.autoDownloadOnlyCharging)
                                .setRequiresStorageNotLow(true)
                                .setRequiresBatteryNotLow(true)
                                .build(),
                        ).build()
                // A delayed run queues behind a running one instead of cancelling it half-way.
                val policy = if (wait > 0) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
                WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, request)
            }
        }
    }
