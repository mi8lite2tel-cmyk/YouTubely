// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.youtubely.core.datastore.PlaybackSettingsRepository
import app.youtubely.core.model.Download
import app.youtubely.core.model.DownloadFormat
import app.youtubely.core.model.DownloadState
import app.youtubely.core.model.StreamInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Public entry point for downloads: list, start, pause, resume, delete. */
@Singleton
class DownloadManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: DownloadStore,
        private val settings: PlaybackSettingsRepository,
    ) {
        private val workManager get() = WorkManager.getInstance(context)

        val downloads: Flow<List<Download>> = store.downloads

        fun download(id: String): Flow<Download?> = store.downloads.map { all -> all.firstOrNull { it.id == id } }

        /** What can be saved for this video without re-encoding (see [StreamSelection]). */
        fun formats(info: StreamInfo): List<DownloadFormat> = StreamSelection.formats(info)

        suspend fun start(
            info: StreamInfo,
            format: DownloadFormat,
        ): Download {
            val id = Download.idFor(info.item.id, format)
            val existing = store.get(id)
            if (existing != null && existing.state != DownloadState.Failed) {
                if (existing.state == DownloadState.Paused) resume(id)
                return existing
            }
            val download =
                Download(
                    id = id,
                    videoId = info.item.id,
                    title = info.item.title,
                    channelName = info.item.channel?.name,
                    duration = info.item.duration,
                    format = format,
                    state = DownloadState.Queued,
                    progress = 0f,
                    totalBytes = null,
                    filePath = null,
                    thumbnailPath = null,
                    createdAtMillis = System.currentTimeMillis(),
                )
            store.upsert(download)
            enqueue(id)
            return download
        }

        suspend fun pause(id: String) {
            store.update(id) { it.copy(state = DownloadState.Paused) }
            workManager.cancelUniqueWork(workName(id))
        }

        suspend fun resume(id: String) {
            store.update(id) { it.copy(state = DownloadState.Queued, error = null) }
            enqueue(id)
        }

        /** Removes the record and every file of the download, finished or not. */
        suspend fun delete(id: String) {
            workManager.cancelUniqueWork(workName(id))
            store.remove(id)
            File(DownloadWorker.downloadsRoot(context), id).deleteRecursively()
        }

        private suspend fun enqueue(id: String) {
            val wifiOnly = settings.settings.first().downloadOnWifiOnly
            val request =
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                            .setRequiresStorageNotLow(true)
                            .build(),
                    ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(TAG)
                    .build()
            workManager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
        }

        private fun workName(id: String) = "download-$id"

        private companion object {
            const val TAG = "download"
            const val BACKOFF_SECONDS = 30L
        }
    }
