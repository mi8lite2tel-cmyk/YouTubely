// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The single HTTP client of the app: extractor requests, thumbnails and media share its pool and cache. */
object HttpClientFactory {
    private const val CONNECT_TIMEOUT_S = 15L
    private const val READ_TIMEOUT_S = 30L

    fun create(builder: OkHttpClient.Builder.() -> Unit = {}): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply(builder)
            .build()
}
