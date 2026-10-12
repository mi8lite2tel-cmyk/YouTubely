// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Downloads a file in fixed-size range requests and appends to [target], so an interrupted
 * download resumes from the bytes already on disk. Small ranges also keep media servers from
 * throttling one long-running response.
 */
class RangeDownloader(
    private val client: OkHttpClient,
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
) {
    /**
     * @param totalBytes expected size if known (the source reports it); otherwise read from the
     *   first response's Content-Range.
     * @param onProgress bytes on disk so far and the total.
     */
    suspend fun download(
        url: String,
        headers: Map<String, String>,
        target: File,
        totalBytes: Long?,
        onProgress: suspend (done: Long, total: Long) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        var done = if (target.exists()) target.length() else 0L
        var total = totalBytes
        while (total == null || done < total) {
            currentCoroutineContext().ensureActive()
            val chunk = fetchChunk(url, headers, target, done) ?: return
            done += chunk.written
            total = total ?: chunk.total
            onProgress(done, total)
        }
    }

    private class Chunk(
        val written: Long,
        val total: Long,
    )

    /** Appends bytes from [offset] on; `null` when the server reports the file is already complete. */
    private fun fetchChunk(
        url: String,
        headers: Map<String, String>,
        target: File,
        offset: Long,
    ): Chunk? {
        val end = offset + chunkSize - 1
        val request =
            Request
                .Builder()
                .url(url)
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .header("Range", "bytes=$offset-$end")
                .build()
        client.newCall(request).execute().use { response ->
            if (response.code == HTTP_RANGE_NOT_SATISFIABLE) return null
            val total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
            if (response.code != HTTP_PARTIAL_CONTENT || total == null) {
                throw IOException("Unexpected HTTP ${response.code} for range $offset-$end")
            }
            val written = FileOutputStream(target, true).use { out -> response.body.byteStream().copyTo(out) }
            if (written == 0L) throw IOException("Empty range response at $offset")
            return Chunk(written, total)
        }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 4L * 1024 * 1024
        private const val HTTP_PARTIAL_CONTENT = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
