// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.storage.StorageManager
import androidx.core.content.getSystemService
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
import app.youtubely.core.model.StorageUsage
import app.youtubely.core.model.StreamInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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

        /**
         * Queued downloads exist but wait because they may only use Wi-Fi and the device is on
         * mobile data. The UI offers [downloadNow] for that case.
         */
        val waitingForWifi: Flow<Boolean> =
            combine(
                settings.settings.map { it.downloadOnWifiOnly },
                unmeteredNetwork(),
                store.downloads.map { all -> all.any { it.state == DownloadState.Queued } },
            ) { wifiOnly, unmetered, anyQueued -> wifiOnly && !unmetered && anyQueued }
                .distinctUntilChanged()

        fun download(id: String): Flow<Download?> = store.downloads.map { all -> all.firstOrNull { it.id == id } }

        /** Bytes used by all downloads (including unfinished parts) and free on their volume. */
        suspend fun storage(): StorageUsage =
            withContext(Dispatchers.IO) {
                val root = DownloadWorker.downloadsRoot(context).apply { mkdirs() }
                StorageUsage(
                    usedBytes = root.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                    freeBytes = allocatableBytes(root),
                )
            }

        /** Free space including cache the system would clear for us; plain free space if that is unknown. */
        private fun allocatableBytes(dir: File): Long {
            val storage = context.getSystemService<StorageManager>() ?: return dir.usableSpace
            return try {
                storage.getAllocatableBytes(storage.getUuidForPath(dir))
            } catch (_: IOException) {
                dir.usableSpace
            }
        }

        /** What can be saved without re-encoding and played back on this device (see [StreamSelection]). */
        fun formats(info: StreamInfo): List<DownloadFormat> = StreamSelection.formats(info, DeviceDecoders::canPlay)

        /** @param unmeteredOnly wait for Wi-Fi whatever the setting says (automatic downloads). */
        suspend fun start(
            info: StreamInfo,
            format: DownloadFormat,
            unmeteredOnly: Boolean = false,
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
            enqueue(id, forceUnmetered = unmeteredOnly)
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

        /** Lets the downloads that wait for Wi-Fi use mobile data this once. */
        suspend fun downloadNow() {
            store.downloads
                .first()
                .filter { it.state == DownloadState.Queued }
                .forEach { enqueue(it.id, allowMetered = true) }
        }

        /** Removes the record and every file of the download, finished or not. */
        suspend fun delete(id: String) {
            workManager.cancelUniqueWork(workName(id))
            store.remove(id)
            File(DownloadWorker.downloadsRoot(context), id).deleteRecursively()
        }

        private fun unmeteredNetwork(): Flow<Boolean> =
            callbackFlow {
                val connectivity = context.getSystemService<ConnectivityManager>()
                if (connectivity == null) {
                    trySend(true)
                    awaitClose()
                    return@callbackFlow
                }
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
                        }

                        // Offline is not a reason to blame Wi-Fi-only mode.
                        override fun onLost(network: Network) {
                            trySend(true)
                        }
                    }
                connectivity.registerDefaultNetworkCallback(callback)
                awaitClose { connectivity.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()

        private suspend fun enqueue(
            id: String,
            allowMetered: Boolean = false,
            forceUnmetered: Boolean = false,
        ) {
            val wifiOnly = forceUnmetered || (settings.settings.first().downloadOnWifiOnly && !allowMetered)
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
