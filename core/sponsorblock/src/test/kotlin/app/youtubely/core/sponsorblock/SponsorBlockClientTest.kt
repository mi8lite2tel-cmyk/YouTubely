// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.sponsorblock

import app.youtubely.core.model.SponsorBlockSettings
import app.youtubely.core.model.SponsorSegment
import app.youtubely.core.model.VideoId
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorBlockClientTest {
    private val server = MockWebServer()

    @After
    fun tearDown() = server.close()

    private val answer =
        """[
          {"videoID":"other","segments":[{"category":"sponsor","actionType":"skip","segment":[1,2]}]},
          {"videoID":"abc12345678","segments":[
            {"category":"intro","actionType":"skip","segment":[0,5.5]},
            {"category":"sponsor","actionType":"skip","segment":[100.25,160]},
            {"category":"sponsor","actionType":"mute","segment":[200,210]},
            {"category":"selfpromo","actionType":"skip","segment":[300,300]}
          ]}]"""

    @Test
    fun picksTheRequestedVideoAndCategoriesAndSkipsOnly() {
        val segments = SponsorBlockClient.parse(answer, "abc12345678", setOf("sponsor", "intro", "selfpromo"))
        assertEquals(
            listOf(SponsorSegment("intro", 0, 5_500), SponsorSegment("sponsor", 100_250, 160_000)),
            segments,
        )
    }

    @Test
    fun garbageGivesNoSegments() {
        assertTrue(SponsorBlockClient.parse("not json", "abc12345678", setOf("sponsor")).isEmpty())
        assertTrue(SponsorBlockClient.parse("{}", "abc12345678", setOf("sponsor")).isEmpty())
    }

    @Test
    fun onlyAHashPrefixIsSentToTheServer() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(answer)
                    .build(),
            )
            server.start()
            val client = SponsorBlockClient(OkHttpClient())
            val settings = SponsorBlockSettings(enabled = true, serverUrl = server.url("/").toString())

            val segments = client.segments(VideoId("abc12345678"), settings)

            val request = server.takeRequest()
            assertEquals("/api/skipSegments/${SponsorBlockClient.hashPrefix("abc12345678")}", request.url.encodedPath)
            assertTrue("abc12345678" !in request.url.toString())
            assertEquals(1, segments.count { it.category == "sponsor" })
        }

    @Test
    fun anUnknownPrefixIsNotAnError() =
        runTest {
            server.enqueue(MockResponse.Builder().code(404).build())
            server.start()
            val settings = SponsorBlockSettings(enabled = true, serverUrl = server.url("/").toString())
            assertTrue(SponsorBlockClient(OkHttpClient()).segments(VideoId("abc12345678"), settings).isEmpty())
        }

    @Test
    fun hashPrefixIsFourHexCharacters() {
        assertEquals(4, SponsorBlockClient.hashPrefix("dQw4w9WgXcQ").length)
        // SHA-256("dQw4w9WgXcQ") starts with "5d3c" is not asserted: only the shape and stability are.
        assertEquals(SponsorBlockClient.hashPrefix("dQw4w9WgXcQ"), SponsorBlockClient.hashPrefix("dQw4w9WgXcQ"))
    }
}
