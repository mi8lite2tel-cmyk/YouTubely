// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RangeDownloaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val content = ByteArray(10_000) { (it % 251).toByte() }
    private val server = MockWebServer()
    private val requestedRanges = mutableListOf<String>()

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.headers["Range"]!!.removePrefix("bytes=")
                    requestedRanges += range
                    val start = range.substringBefore('-').toInt()
                    val end = minOf(range.substringAfter('-').toInt(), content.size - 1)
                    return MockResponse
                        .Builder()
                        .code(206)
                        .addHeader("Content-Range", "bytes $start-$end/${content.size}")
                        .body(okio.Buffer().write(content, start, end - start + 1))
                        .build()
                }
            }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun downloadsInChunksWithHeaders() =
        runTest {
            val target = folder.newFile("video.part").apply { delete() }
            val progress = mutableListOf<Long>()
            RangeDownloader(OkHttpClient(), chunkSize = 4_000)
                .download(server.url("/v").toString(), mapOf("User-Agent" to "ua"), target, null) { done, _ ->
                    progress += done
                }
            assertArrayEquals(content, target.readBytes())
            assertEquals(listOf("0-3999", "4000-7999", "8000-11999"), requestedRanges)
            assertEquals(listOf(4_000L, 8_000L, 10_000L), progress)
        }

    @Test
    fun resumesFromExistingBytes() =
        runTest {
            val target = folder.newFile("audio.part").apply { writeBytes(content.copyOfRange(0, 6_000)) }
            RangeDownloader(OkHttpClient(), chunkSize = 4_000)
                .download(server.url("/a").toString(), emptyMap(), target, content.size.toLong()) { _, _ -> }
            assertArrayEquals(content, target.readBytes())
            assertEquals(listOf("6000-9999"), requestedRanges)
        }

    @Test
    fun acceptsAServerThatIgnoresRanges() =
        runTest {
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        MockResponse
                            .Builder()
                            .code(200)
                            .body(okio.Buffer().write(content))
                            .build()
                }
            val target = folder.newFile("whole.part").apply { delete() }
            RangeDownloader(OkHttpClient(), chunkSize = 4_000)
                .download(server.url("/w").toString(), emptyMap(), target, null) { _, _ -> }
            assertArrayEquals(content, target.readBytes())
        }
}
