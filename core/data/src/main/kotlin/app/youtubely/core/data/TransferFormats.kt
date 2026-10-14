// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class TransferChannel(
    val id: String,
    val name: String,
    val group: String? = null,
    val notify: Boolean = false,
    val autoDownload: Boolean = false,
)

@Serializable
data class TransferVideo(
    val id: String,
    val title: String,
    val channelId: String? = null,
    val channelName: String? = null,
    val durationSeconds: Long? = null,
)

@Serializable
data class TransferPlaylist(
    val name: String,
    val videos: List<TransferVideo>,
)

/** Everything that can be moved to another device or another app. */
@Serializable
data class TransferBundle(
    val app: String = APP_NAME,
    val version: Int = VERSION,
    val subscriptions: List<TransferChannel> = emptyList(),
    val playlists: List<TransferPlaylist> = emptyList(),
    val watchLater: List<TransferVideo> = emptyList(),
) {
    val isEmpty: Boolean get() = subscriptions.isEmpty() && playlists.isEmpty() && watchLater.isEmpty()

    companion object {
        const val APP_NAME = "YouTubely"
        const val VERSION = 1
    }
}

/**
 * Reads and writes subscriptions in the formats of YouTubely, NewPipe, LibreTube, Google Takeout
 * (`subscriptions.csv`) and OPML. The format of an input is detected from its content.
 */
object TransferFormats {
    private val json =
        Json {
            ignoreUnknownKeys = true
            // The marker that tells YouTubely's own files from other apps' ones is a default value.
            encodeDefaults = true
            explicitNulls = false
            prettyPrint = false
        }

    private val channelIdInUrl = Regex("(UC[A-Za-z0-9_-]{22})")
    private val outline = Regex("<outline\\b[^>]*>", RegexOption.IGNORE_CASE)

    fun encode(bundle: TransferBundle): String = json.encodeToString(TransferBundle.serializer(), bundle)

    /** `null` when [text] is none of the known formats or holds nothing to import. */
    fun parse(text: String): TransferBundle? {
        val trimmed = text.trim().removePrefix("\uFEFF")
        val bundle =
            when {
                trimmed.startsWith("{") -> parseJson(trimmed)
                trimmed.startsWith("<") -> TransferBundle(subscriptions = parseOpml(trimmed))
                else -> TransferBundle(subscriptions = parseCsv(trimmed))
            }
        return bundle?.takeUnless { it.isEmpty }
    }

    private fun parseJson(text: String): TransferBundle? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val own = root?.get("app")?.jsonPrimitive?.contentOrNull == TransferBundle.APP_NAME
        // NewPipe ("url") and LibreTube ("url" or "channelId") list their channels under "subscriptions".
        val channels = root?.get("subscriptions") as? JsonArray
        return when {
            root == null -> null
            own -> runCatching { json.decodeFromString(TransferBundle.serializer(), text) }.getOrNull()
            channels == null -> null
            else -> TransferBundle(subscriptions = channels.mapNotNull { channelOf(it) }.distinctBy { it.id })
        }
    }

    private fun channelOf(element: JsonElement): TransferChannel? {
        val item = element as? JsonObject
        val id =
            listOf("channelId", "id", "url")
                .firstNotNullOfOrNull { key ->
                    item
                        ?.get(key)
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.let(::channelIdOf)
                }
        return id?.let { TransferChannel(it, item?.get("name")?.jsonPrimitive?.contentOrNull ?: it) }
    }

    /** Google Takeout: `Channel ID,Channel URL,Channel title`. */
    private fun parseCsv(text: String): List<TransferChannel> =
        text
            .lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val cells = splitCsv(line)
                val id = cells.firstNotNullOfOrNull(::channelIdOf) ?: return@mapNotNull null
                TransferChannel(id, cells.lastOrNull()?.takeIf { it != id && !it.startsWith("http") } ?: id)
            }.distinctBy { it.id }
            .toList()

    private fun splitCsv(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        for (c in line) {
            when {
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> cells += cell.toString().also { cell.clear() }
                else -> cell.append(c)
            }
        }
        cells += cell.toString()
        return cells.map { it.trim() }
    }

    /** OPML as exported by many readers: `<outline text="…" xmlUrl="…channel_id=UC…"/>`. */
    private fun parseOpml(text: String): List<TransferChannel> =
        outline
            .findAll(text)
            .mapNotNull { match ->
                val tag = match.value
                val id = attribute(tag, "xmlUrl")?.let(::channelIdOf) ?: return@mapNotNull null
                TransferChannel(id, attribute(tag, "text") ?: attribute(tag, "title") ?: id)
            }.distinctBy { it.id }
            .toList()

    private fun attribute(
        tag: String,
        name: String,
    ): String? =
        Regex("\\b$name\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.groupValues
            ?.get(1)
            ?.let(::unescape)

    private fun unescape(value: String): String =
        value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    /** A channel id (`UC…`) from an id or a URL that contains one. */
    private fun channelIdOf(text: String): String? = channelIdInUrl.find(text)?.value
}
