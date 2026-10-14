// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.network

import app.youtubely.core.model.TrafficKind
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/** Receives the number of bytes that went over the network. Called often, from any thread. */
fun interface TrafficSink {
    fun onBytes(
        kind: TrafficKind,
        bytes: Long,
    )
}

/** Marks a request with the kind of traffic it is; hosts alone cannot tell a download from streaming. */
fun Request.Builder.trafficKind(kind: TrafficKind): Request.Builder = tag(TrafficKind::class.java, kind)

/** Counts what is read from response bodies, so the Traffic screen shows what the app really used. */
class TrafficInterceptor(
    private val sink: TrafficSink,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val kind = request.tag(TrafficKind::class.java) ?: classify(request.url.host)
        request.body
            ?.contentLength()
            ?.takeIf { it > 0 }
            ?.let { sink.onBytes(kind, it) }
        val response = chain.proceed(request)
        return response.newBuilder().body(CountingBody(response.body, kind, sink)).build()
    }

    private class CountingBody(
        private val delegate: ResponseBody,
        private val kind: TrafficKind,
        private val sink: TrafficSink,
    ) : ResponseBody() {
        private val counted: BufferedSource by lazy {
            object : ForwardingSource(delegate.source()) {
                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long {
                    val read = super.read(sink, byteCount)
                    if (read > 0) this@CountingBody.sink.onBytes(kind, read)
                    return read
                }
            }.buffer()
        }

        override fun contentType(): MediaType? = delegate.contentType()

        override fun contentLength(): Long = delegate.contentLength()

        override fun source(): BufferedSource = counted
    }

    companion object {
        private val VIDEO_HOSTS = listOf("googlevideo.com")
        private val IMAGE_HOSTS = listOf("ytimg.com", "ggpht.com", "googleusercontent.com")

        fun classify(host: String): TrafficKind =
            when {
                VIDEO_HOSTS.any { host == it || host.endsWith(".$it") } -> TrafficKind.Video
                IMAGE_HOSTS.any { host == it || host.endsWith(".$it") } -> TrafficKind.Images
                else -> TrafficKind.Other
            }
    }
}
