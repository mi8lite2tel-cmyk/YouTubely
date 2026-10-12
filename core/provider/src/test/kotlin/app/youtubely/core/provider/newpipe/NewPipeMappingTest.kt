// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.provider.newpipe

import app.youtubely.core.provider.ProviderError
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.Description
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.schabi.newpipe.extractor.Page as NpPage

class NewPipeMappingTest {
    @Test
    fun `page token round-trips every field`() {
        val page = NpPage("https://example.com/next", "id1", listOf("a", "b"), mapOf("c" to "d"), byteArrayOf(1, 2, 3))
        val decoded = PageCodec.decode(PageCodec.encode(page)!!)
        assertEquals(page.url, decoded.url)
        assertEquals(page.id, decoded.id)
        assertEquals(page.ids, decoded.ids)
        assertEquals(page.cookies, decoded.cookies)
        assertContentEquals(page.body, decoded.body)
    }

    @Test
    fun `missing next page encodes to null`() {
        assertNull(PageCodec.encode(null))
    }

    @Test
    fun `exceptions map to the most specific provider error`() {
        assertIs<ProviderError.RateLimited>(ReCaptchaException("captcha", "url").toProviderError())
        assertIs<ProviderError.GeoBlocked>(GeographicRestrictionException("geo").toProviderError())
        assertIs<ProviderError.AgeRestricted>(AgeRestrictedContentException("age").toProviderError())
        assertIs<ProviderError.Paid>(PaidContentException("paid").toProviderError())
        assertIs<ProviderError.Removed>(ContentNotAvailableException("gone").toProviderError())
        assertIs<ProviderError.ExtractorOutdated>(ParsingException("layout changed").toProviderError())
        assertIs<ProviderError.Network>(IOException("offline").toProviderError())
        assertIs<ProviderError.Unknown>(IllegalStateException("?").toProviderError())
    }

    @Test
    fun `html description becomes plain text with line breaks`() {
        val html = Description("Line <b>one</b><br>Line two<br/><a href=\"https://x.y\">link</a>", Description.HTML)
        assertEquals("Line one\nLine two\nlink", html.toPlainText())
        assertEquals("plain", Description("plain", Description.PLAIN_TEXT).toPlainText())
    }

    @Test
    fun `media headers match the signing client`() {
        assertEquals(
            mapOf("User-Agent" to "x").keys,
            mediaHeaders("https://rr1.googlevideo.com/videoplayback?id=1&c=ANDROID").keys,
        )
        val web = mediaHeaders("https://rr1.googlevideo.com/videoplayback?id=1&c=WEB")
        assertEquals("https://www.youtube.com", web["Origin"])
    }
}
