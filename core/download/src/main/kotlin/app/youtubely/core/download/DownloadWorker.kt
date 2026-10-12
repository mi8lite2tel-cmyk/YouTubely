// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.youtubely.core.model.Download
import app.youtubely.core.model.DownloadState
import app.youtubely.core.model.bestFor
import app.youtubely.core.provider.ProviderError
import app.youtubely.core.provider.StreamProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Downloads one [Download]: fresh stream URLs from the provider, video and audio in resumable
 * range requests, then a lossless merge into MP4 (audio-only downloads are kept as M4A).
 * Runs as a foreground worker with a progress notification.
 */
@HiltWorker
internal class DownloadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val store: DownloadStore,
        private val provider: StreamProvider,
        private val client: OkHttpClient,
    ) : CoroutineWorker(context, params) {
        private val id = requireNotNull(inputData.getString(KEY_ID))
        private val notificationId = id.hashCode()

        override suspend fun doWork(): Result {
            val download = store.get(id) ?: return Result.success()
            DownloadNotifications.ensureChannel(applicationContext)
            return try {
                run(download)
                Result.success()
            } catch (e: CancellationException) {
                // A user pause already set Paused, a delete removed the record; otherwise the system
                // stopped us (e.g. Wi-Fi lost) and WorkManager resumes the download later.
                store.update(id) { if (it.state == DownloadState.Paused) it else it.copy(state = DownloadState.Queued) }
                throw e
            } catch (e: IOException) {
                if (runAttemptCount < MAX_ATTEMPTS) {
                    store.update(id) { it.copy(state = DownloadState.Queued) }
                    Result.retry()
                } else {
                    fail(e)
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                fail(e)
            }
        }

        private suspend fun fail(e: Exception): Result {
            store.update(id) { it.copy(state = DownloadState.Failed, error = e.message) }
            return Result.failure()
        }

        private suspend fun run(download: Download) {
            setState(download, DownloadState.Downloading, download.progress)
            val info = provider.streamInfo(download.videoId)
            val streams = StreamSelection.select(info, download.format) ?: throw ProviderError.ExtractorOutdated()
            val dir = File(downloadsRoot(applicationContext), download.id).apply { mkdirs() }

            val thumbnail = File(dir, "thumbnail.jpg")
            if (!thumbnail.exists()) {
                info.item.thumbnails
                    .bestFor(THUMBNAIL_WIDTH)
                    ?.url
                    ?.let { runCatching { fetch(it, thumbnail) } }
            }

            val videoPart = File(dir, "video.part")
            val audioPart = File(dir, "audio.part")
            val total = downloadStreams(download, streams, videoPart, audioPart)

            val name = safeFileName(info.item.title)
            val output =
                if (streams.video != null) {
                    setState(download, DownloadState.Merging, 1f, total)
                    File(dir, "$name.mp4").also { MediaMerger(applicationContext).merge(videoPart, audioPart, it) }
                } else {
                    File(dir, "$name.m4a").also { audioPart.renameTo(it) }
                }
            videoPart.delete()
            audioPart.delete()

            store.update(id) {
                it.copy(
                    state = DownloadState.Completed,
                    progress = 1f,
                    totalBytes = output.length(),
                    filePath = output.absolutePath,
                    thumbnailPath = thumbnail.takeIf(File::exists)?.absolutePath,
                    error = null,
                )
            }
        }

        /** Fetches video (if any) then audio, reporting combined progress in whole percents. Returns the total size. */
        private suspend fun downloadStreams(
            download: Download,
            streams: SelectedStreams,
            videoPart: File,
            audioPart: File,
        ): Long? {
            val total = ((streams.video?.contentLength ?: 0L) + (streams.audio.contentLength ?: 0L)).takeIf { it > 0 }
            var videoDone = if (videoPart.exists()) videoPart.length() else 0L
            var audioDone = if (audioPart.exists()) audioPart.length() else 0L
            var lastPercent = -1

            suspend fun report() {
                val progress = total?.let { (videoDone + audioDone).toFloat() / it } ?: 0f
                val percent = (progress * PERCENT).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    setState(download, DownloadState.Downloading, progress, total)
                }
            }

            val downloader = RangeDownloader(client)
            streams.video?.let { video ->
                downloader.download(video.url, video.httpHeaders, videoPart, video.contentLength) { done, _ ->
                    videoDone = done
                    report()
                }
            }
            val audio = streams.audio
            downloader.download(audio.url, audio.httpHeaders, audioPart, audio.contentLength) { done, _ ->
                audioDone = done
                report()
            }
            return total
        }

        private suspend fun setState(
            download: Download,
            state: DownloadState,
            progress: Float,
            total: Long? = null,
        ) {
            store.update(id) { it.copy(state = state, progress = progress, totalBytes = total ?: it.totalBytes) }
            setForeground(foregroundInfo(download.title, progress, merging = state == DownloadState.Merging))
        }

        private fun foregroundInfo(
            title: String,
            progress: Float,
            merging: Boolean,
        ): ForegroundInfo {
            val notification = DownloadNotifications.progress(applicationContext, title, progress, merging)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(notificationId, notification)
            }
        }

        override suspend fun getForegroundInfo(): ForegroundInfo =
            foregroundInfo(store.get(id)?.title.orEmpty(), 0f, merging = false)

        private fun fetch(
            url: String,
            target: File,
        ) {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) target.outputStream().use { response.body.byteStream().copyTo(it) }
            }
        }

        companion object {
            const val KEY_ID = "download_id"
            private const val MAX_ATTEMPTS = 3
            private const val PERCENT = 100
            private const val THUMBNAIL_WIDTH = 720
            private const val MAX_NAME_LENGTH = 80

            fun downloadsRoot(context: Context): File =
                File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")

            /** Keeps letters, digits and a few separators so the name works on every file system. */
            internal fun safeFileName(title: String): String =
                title
                    .replace(Regex("[^\\p{L}\\p{N} ._()-]"), "_")
                    .trim()
                    .take(MAX_NAME_LENGTH)
                    .takeIf { name -> name.any(Char::isLetterOrDigit) }
                    ?: "video"
        }
    }
