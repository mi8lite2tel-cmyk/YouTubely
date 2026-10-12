// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NewPipeDownloaderTest {
    private val server = MockWebServer()
    private val downloader = NewPipeDownloader(HttpClientFactory.create())

    @BeforeTest
    fun setUp() = server.start()

    @AfterTest
    fun tearDown() = server.close()

    @Test
    fun `get returns body, code and headers`() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .addHeader("X-Test", "1")
                .body("hello")
                .build(),
        )
        val response = downloader.get(server.url("/a").toString())
        assertEquals(200, response.responseCode())
        assertEquals("hello", response.responseBody())
        assertEquals(listOf("1"), response.responseHeaders()["x-test"])
    }

    @Test
    fun `post sends body and custom headers override the default user agent`() {
        server.enqueue(MockResponse.Builder().body("ok").build())
        val request =
            Request
                .newBuilder()
                .post(server.url("/p").toString(), "payload".toByteArray())
                .setHeader("User-Agent", "custom")
                .setHeader("Content-Type", "application/json")
                .build()
        downloader.execute(request)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("payload", recorded.body?.utf8())
        assertEquals("custom", recorded.headers["User-Agent"])
    }

    @Test
    fun `http 429 becomes ReCaptchaException`() {
        server.enqueue(MockResponse.Builder().code(429).build())
        assertFailsWith<ReCaptchaException> { downloader.get(server.url("/limited").toString()) }
    }
}
