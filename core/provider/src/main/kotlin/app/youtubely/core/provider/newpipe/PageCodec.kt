// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.provider.newpipe

import app.youtubely.core.model.Page
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64
import org.schabi.newpipe.extractor.Page as NpPage

/** Encodes NewPipe's continuation [NpPage] into the app's opaque [Page] token and back. */
internal object PageCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Token(
        val url: String? = null,
        val id: String? = null,
        val ids: List<String>? = null,
        val cookies: Map<String, String>? = null,
        val body: String? = null,
    )

    fun encode(page: NpPage?): Page? {
        if (!NpPage.isValid(page)) return null
        val token =
            Token(
                url = page!!.url,
                id = page.id,
                ids = page.ids,
                cookies = page.cookies,
                body = page.body?.let(Base64.getEncoder()::encodeToString),
            )
        return Page(json.encodeToString(Token.serializer(), token))
    }

    fun decode(page: Page): NpPage {
        val token = json.decodeFromString(Token.serializer(), page.token)
        return NpPage(
            token.url,
            token.id,
            token.ids,
            token.cookies,
            token.body?.let(Base64.getDecoder()::decode),
        )
    }
}
