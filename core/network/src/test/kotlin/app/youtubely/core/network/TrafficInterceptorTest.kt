// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.network

import app.youtubely.core.model.TrafficKind
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.test.Test
import kotlin.test.assertEquals

class TrafficInterceptorTest {
    @Test
    fun `hosts are classified`() {
        assertEquals(TrafficKind.Video, TrafficInterceptor.classify("rr3---sn-abc.googlevideo.com"))
        assertEquals(TrafficKind.Images, TrafficInterceptor.classify("i.ytimg.com"))
        assertEquals(TrafficKind.Images, TrafficInterceptor.classify("yt3.ggpht.com"))
        assertEquals(TrafficKind.Other, TrafficInterceptor.classify("www.youtube.com"))
        assertEquals(TrafficKind.Other, TrafficInterceptor.classify("notgooglevideo.com"))
    }

    @Test
    fun `counts the bytes of a response body by kind`() {
        val counted = mutableMapOf<TrafficKind, Long>()
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(TrafficInterceptor { kind, bytes -> counted.merge(kind, bytes, Long::plus) })
                .build()
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().body("x".repeat(BODY_SIZE)).build())
            server.start()
            val request =
                Request
                    .Builder()
                    .url(server.url("/"))
                    .trafficKind(TrafficKind.Download)
                    .build()
            client.newCall(request).execute().use { it.body.string() }
        }
        assertEquals(BODY_SIZE.toLong(), counted[TrafficKind.Download])
    }

    private companion object {
        const val BODY_SIZE = 20_000
    }
}
