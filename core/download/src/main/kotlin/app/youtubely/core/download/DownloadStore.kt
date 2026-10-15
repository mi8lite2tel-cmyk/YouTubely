// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import app.youtubely.core.model.Download
import app.youtubely.core.model.DownloadFormat
import app.youtubely.core.model.DownloadState
import app.youtubely.core.model.VideoId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration

/** All downloads, newest first, persisted as JSON in a typed DataStore. */
class DownloadStore(
    private val store: DataStore<List<DownloadRecord>>,
) {
    val downloads: Flow<List<Download>> =
        store.data.map { records ->
            records.sortedByDescending { it.createdAtMillis }.map(DownloadRecord::toDownload)
        }

    suspend fun get(id: String): Download? =
        store.data
            .first()
            .firstOrNull { it.id == id }
            ?.toDownload()

    suspend fun upsert(download: Download) {
        store.updateData { records -> records.filterNot { it.id == download.id } + DownloadRecord.from(download) }
    }

    suspend fun update(
        id: String,
        transform: (Download) -> Download,
    ) {
        store.updateData { records ->
            records.map { if (it.id == id) DownloadRecord.from(transform(it.toDownload())) else it }
        }
    }

    suspend fun remove(id: String) {
        store.updateData { records -> records.filterNot { it.id == id } }
    }
}

@Serializable
data class DownloadRecord(
    val id: String,
    val videoId: String,
    val title: String,
    val channelName: String? = null,
    val durationSeconds: Long? = null,
    /** 0 = audio only. */
    val height: Int,
    val state: String,
    val progress: Float = 0f,
    val totalBytes: Long? = null,
    val filePath: String? = null,
    val thumbnailPath: String? = null,
    val createdAtMillis: Long,
    val error: String? = null,
) {
    fun toDownload() =
        Download(
            id = id,
            videoId = VideoId(videoId),
            title = title,
            channelName = channelName,
            duration = durationSeconds?.let(Duration::ofSeconds),
            format = if (height == 0) DownloadFormat.Audio else DownloadFormat.Video(height),
            state = DownloadState.entries.firstOrNull { it.name == state } ?: DownloadState.Failed,
            progress = progress,
            totalBytes = totalBytes,
            filePath = filePath,
            thumbnailPath = thumbnailPath,
            createdAtMillis = createdAtMillis,
            error = error,
        )

    companion object {
        fun from(download: Download) =
            DownloadRecord(
                id = download.id,
                videoId = download.videoId.value,
                title = download.title,
                channelName = download.channelName,
                durationSeconds = download.duration?.seconds,
                height = (download.format as? DownloadFormat.Video)?.height ?: 0,
                state = download.state.name,
                progress = download.progress,
                totalBytes = download.totalBytes,
                filePath = download.filePath,
                thumbnailPath = download.thumbnailPath,
                createdAtMillis = download.createdAtMillis,
                error = download.error,
            )
    }
}

internal object DownloadRecordsSerializer : Serializer<List<DownloadRecord>> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: List<DownloadRecord> = emptyList()

    override suspend fun readFrom(input: InputStream): List<DownloadRecord> =
        try {
            json.decodeFromString<List<DownloadRecord>>(input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable download records", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Unreadable download records", e)
        }

    override suspend fun writeTo(
        t: List<DownloadRecord>,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}
