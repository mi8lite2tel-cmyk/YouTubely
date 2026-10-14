// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.sponsorblock

import app.youtubely.core.model.SponsorBlockSettings
import app.youtubely.core.model.SponsorSegment
import app.youtubely.core.model.VideoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks a SponsorBlock server which stretches of a video to skip. Only the first four characters
 * of the SHA-256 of the video id are sent (the server answers for every video sharing them), so
 * the server cannot tell which video is being watched.
 */
@Singleton
class SponsorBlockClient
    @Inject
    constructor(
        private val client: OkHttpClient,
    ) {
        suspend fun segments(
            videoId: VideoId,
            settings: SponsorBlockSettings,
        ): List<SponsorSegment> =
            withContext(Dispatchers.IO) {
                val url =
                    settings.serverUrl
                        .trimEnd('/')
                        .plus("/api/skipSegments/${hashPrefix(videoId.value)}")
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("categories", jsonList(settings.categories.sorted()))
                        .addQueryParameter("actionTypes", jsonList(listOf("skip")))
                        .build()
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    when {
                        // Nothing is marked for any video with this prefix.
                        response.code == HTTP_NOT_FOUND -> emptyList()

                        !response.isSuccessful -> throw IOException("SponsorBlock HTTP ${response.code}")

                        else -> parse(response.body.string(), videoId.value, settings.categories)
                    }
                }
            }

        internal companion object {
            private const val HTTP_NOT_FOUND = 404
            private const val PREFIX_LENGTH = 4
            private const val MILLIS = 1_000

            fun hashPrefix(videoId: String): String =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(videoId.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                    .take(PREFIX_LENGTH)

            private fun jsonList(values: List<String>) = values.joinToString(",", "[", "]") { "\"$it\"" }

            /** The segments of [videoId] from an answer that covers all videos with the same hash prefix. */
            fun parse(
                body: String,
                videoId: String,
                categories: Set<String>,
            ): List<SponsorSegment> {
                val root = runCatching { Json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return emptyList()
                return root
                    .map { it.jsonObject }
                    .filter { it["videoID"]?.jsonPrimitive?.contentOrNull == videoId }
                    .flatMap { entry ->
                        (entry["segments"] as? JsonArray).orEmpty().mapNotNull { segmentOf(it as? JsonObject) }
                    }.filter { it.category in categories && it.endMs > it.startMs }
                    .sortedBy { it.startMs }
            }

            private fun segmentOf(item: JsonObject?): SponsorSegment? {
                item ?: return null
                val range = item["segment"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: return null
                val category = item["category"]?.jsonPrimitive?.contentOrNull
                val skips = item["actionType"]?.jsonPrimitive?.contentOrNull in setOf(null, "skip")
                return if (range.size == 2 && category != null && skips) {
                    SponsorSegment(category, (range[0] * MILLIS).toLong(), (range[1] * MILLIS).toLong())
                } else {
                    null
                }
            }
        }
    }
