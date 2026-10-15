// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.youtubely.core.model.VideoId
import app.youtubely.core.provider.ProviderError
import app.youtubely.core.provider.StreamProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Queues many videos (a whole playlist) for download one after another. Each video's own
 * download then runs as usual; this worker only looks up the streams, which is light.
 */
@HiltWorker
internal class BatchDownloadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val provider: StreamProvider,
        private val downloads: DownloadManager,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val ids = inputData.getNullableStringArray(KEY_IDS).orEmpty().filterNotNull()
            val height = inputData.getInt(KEY_HEIGHT, AUDIO_ONLY).takeIf { it != AUDIO_ONLY }
            var again = false
            for (id in ids) {
                try {
                    val info = provider.streamInfo(VideoId(id))
                    StreamSelection.best(downloads.formats(info), height)?.let { downloads.start(info, it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ProviderError) {
                    // A removed or private video must not stop the rest of the playlist; a throttled
                    // one is tried again (videos already queued are recognised and left alone).
                    again = again || e is ProviderError.Network || e is ProviderError.RateLimited
                } catch (
                    @Suppress("TooGenericExceptionCaught") _: Exception,
                ) {
                    // Nothing sensible to do for this one video.
                }
            }
            return if (again && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }

        companion object {
            private const val KEY_IDS = "ids"
            private const val KEY_HEIGHT = "height"
            private const val AUDIO_ONLY = 0
            private const val MAX_ATTEMPTS = 3

            /** Work data is limited to 10 KB; this many ids stay well below it. */
            const val MAX_VIDEOS = 300

            fun enqueue(
                context: Context,
                ids: List<VideoId>,
                maxHeight: Int?,
            ) {
                val request =
                    OneTimeWorkRequestBuilder<BatchDownloadWorker>()
                        .setInputData(
                            workDataOf(
                                KEY_IDS to ids.take(MAX_VIDEOS).map { it.value }.toTypedArray(),
                                KEY_HEIGHT to (maxHeight ?: AUDIO_ONLY),
                            ),
                        ).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                WorkManager
                    .getInstance(
                        context,
                    ).enqueueUniqueWork("batch-download", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            }
        }
    }
