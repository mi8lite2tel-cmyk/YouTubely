// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import app.youtubely.core.model.TrafficKind
import app.youtubely.core.network.trafficKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Downloads a file in fixed-size range requests, so an interrupted download resumes from the
 * bytes already on disk. Small ranges also keep media servers from throttling one long-running
 * response. With more than one [connections], several ranges load at once into their place in
 * the file; a small ledger next to it (`.chunks`) records the finished ones for resuming.
 */
class RangeDownloader(
    private val client: OkHttpClient,
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
    private val connections: Int = 1,
) {
    /**
     * @param totalBytes expected size if known (the source reports it); otherwise read from the
     *   first response's Content-Range.
     * @param onProgress bytes on disk so far and the total.
     */
    @Suppress("ReturnCount") // Early exits: nothing left to fetch, or the parallel path did the work.
    suspend fun download(
        url: String,
        headers: Map<String, String>,
        target: File,
        totalBytes: Long?,
        onProgress: suspend (done: Long, total: Long) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        val ledger = File(target.path + LEDGER_SUFFIX)
        // With a ledger the file already has its full length (see ParallelRanges.finished).
        var total = totalBytes ?: target.length().takeIf { ledger.exists() && it > 0 }
        if (connections > 1 || ledger.exists()) {
            val fresh = !ledger.exists() && (!target.exists() || target.length() == 0L)
            if (fresh && total == null) {
                // The first range tells the size (or the server sends everything at once).
                val first = fetchChunk(url, headers, target, 0) ?: return
                total = first.total
                onProgress(first.written, first.total)
                if (first.written >= first.total) return
            }
            if (total != null) {
                ParallelRanges(target, ledger, total).run(url, headers, onProgress)
                return
            }
        }
        sequential(url, headers, target, total, onProgress)
    }

    private suspend fun sequential(
        url: String,
        headers: Map<String, String>,
        target: File,
        totalBytes: Long?,
        onProgress: suspend (done: Long, total: Long) -> Unit,
    ) {
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

    /** One download split into numbered ranges that several connections fetch in parallel. */
    private inner class ParallelRanges(
        private val target: File,
        private val ledger: File,
        private val total: Long,
    ) {
        private val count = ((total + chunkSize - 1) / chunkSize).toInt()

        private fun length(index: Int): Long = minOf(chunkSize, total - index * chunkSize)

        /** Ranges already on disk: from the ledger, or the contiguous start of an older sequential download. */
        private fun finished(): Set<Int> {
            if (ledger.exists()) return ledger.readLines().mapNotNull { it.toIntOrNull() }.toSet()
            val prefix = if (target.exists()) minOf(target.length(), total) else 0L
            val done = (0 until count).filter { it * chunkSize + length(it) <= prefix }.toSet()
            // Full length first, ledger second: a ledger on disk always means a file of the final size.
            RandomAccessFile(target, "rw").use { it.setLength(total) }
            ledger.writeText(done.joinToString("") { "$it\n" })
            return done
        }

        suspend fun run(
            url: String,
            headers: Map<String, String>,
            onProgress: suspend (done: Long, total: Long) -> Unit,
        ) {
            val done = finished()
            val pending = (0 until count).filterNot { it in done }
            val doneBytes = AtomicLong(done.sumOf(::length))
            val next = AtomicInteger(0)
            val report = Mutex()
            onProgress(doneBytes.get(), total)
            withContext(Dispatchers.IO) {
                coroutineScope {
                    repeat(minOf(connections, pending.size)) {
                        launch {
                            RandomAccessFile(target, "rw").use { file ->
                                while (true) {
                                    ensureActive()
                                    val index = pending.getOrNull(next.getAndIncrement()) ?: break
                                    fetchInto(url, headers, file, index)
                                    synchronized(ledger) { ledger.appendText("$index\n") }
                                    doneBytes.addAndGet(length(index))
                                    // Read inside the lock so progress never goes backwards.
                                    report.withLock { onProgress(doneBytes.get(), total) }
                                }
                            }
                        }
                    }
                }
            }
            ledger.delete()
        }

        private fun fetchInto(
            url: String,
            headers: Map<String, String>,
            file: RandomAccessFile,
            index: Int,
        ) {
            val start = index * chunkSize
            val end = start + length(index) - 1
            val request =
                Request
                    .Builder()
                    .url(url)
                    .apply { headers.forEach { (name, value) -> header(name, value) } }
                    .header("Range", "bytes=$start-$end")
                    .trafficKind(TrafficKind.Download)
                    .build()
            client.newCall(request).execute().use { response ->
                if (response.code != HTTP_PARTIAL_CONTENT) {
                    throw IOException("Unexpected HTTP ${response.code} for range $start-$end")
                }
                val written = response.body.byteStream().use { copyAt(it, file, start) }
                if (written != end - start + 1) throw IOException("Short range $start-$end: got $written bytes")
            }
        }
    }

    /** Writes [input] into [file] from [offset] on; returns the number of bytes. */
    private fun copyAt(
        input: java.io.InputStream,
        file: RandomAccessFile,
        offset: Long,
    ): Long {
        val buffer = ByteArray(COPY_BUFFER)
        var position = offset
        var read = input.read(buffer)
        while (read >= 0) {
            synchronized(file) {
                file.seek(position)
                file.write(buffer, 0, read)
            }
            position += read
            read = input.read(buffer)
        }
        return position - offset
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
                .trafficKind(TrafficKind.Download)
                .build()
        return client.newCall(request).execute().use { response ->
            when {
                response.code == HTTP_RANGE_NOT_SATISFIABLE -> null
                response.code == HTTP_OK && offset == 0L -> wholeFile(response, target)
                else -> appendRange(response, target, offset, end)
            }
        }
    }

    /** The server ignored the Range header and sent the whole file in one response. */
    private fun wholeFile(
        response: Response,
        target: File,
    ): Chunk {
        val written = FileOutputStream(target, false).use { out -> response.body.byteStream().copyTo(out) }
        if (written == 0L) throw IOException("Empty response")
        return Chunk(written, written)
    }

    private fun appendRange(
        response: Response,
        target: File,
        offset: Long,
        end: Long,
    ): Chunk {
        val total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
        if (response.code != HTTP_PARTIAL_CONTENT || total == null) {
            throw IOException("Unexpected HTTP ${response.code} for range $offset-$end")
        }
        val written = FileOutputStream(target, true).use { out -> response.body.byteStream().copyTo(out) }
        if (written == 0L) throw IOException("Empty range response at $offset")
        return Chunk(written, total)
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 4L * 1024 * 1024
        private const val LEDGER_SUFFIX = ".chunks"
        private const val COPY_BUFFER = 64 * 1024
        private const val HTTP_OK = 200
        private const val HTTP_PARTIAL_CONTENT = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
