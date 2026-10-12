// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.provider.newpipe

import app.youtubely.core.model.Region
import app.youtubely.core.model.SearchFilter
import app.youtubely.core.model.SearchItem
import app.youtubely.core.model.VideoId
import app.youtubely.core.network.HttpClientFactory
import app.youtubely.core.network.NewPipeDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Talks to the real YouTube. Skipped unless run with -Plive=true, because CI must not depend
 * on YouTube being reachable or on its current page layout.
 */
class LiveNewPipeStreamProviderTest {
    private val live = System.getProperty("youtubely.live").toBoolean()
    private val provider by lazy {
        NewPipeStreamProvider(NewPipeDownloader(HttpClientFactory.create()), Dispatchers.IO, Locale.US)
    }

    private fun liveTest(block: suspend () -> Unit) {
        if (live) runBlocking { block() }
    }

    @Test
    fun search() =
        liveTest {
            val first = provider.search("lofi hip hop", SearchFilter(type = SearchFilter.Type.Videos), null)
            println("search: ${first.items.size} items, next=${first.nextPage != null}")
            first.items.take(3).forEach { println("  $it") }
            assertTrue(first.items.any { it is SearchItem.Stream })
            val second = provider.search("lofi hip hop", SearchFilter(type = SearchFilter.Type.Videos), first.nextPage)
            println("search page 2: ${second.items.size} items")
            assertTrue(second.items.isNotEmpty())
        }

    @Test
    fun suggestions() =
        liveTest {
            val suggestions = provider.suggestions("materi")
            println("suggestions: $suggestions")
            assertTrue(suggestions.isNotEmpty())
        }

    @Test
    fun streamInfo() =
        liveTest {
            val info = provider.streamInfo(VideoId("aqz-KE-bpKQ"))
            println("title=${info.item.title} channel=${info.item.channel?.name} duration=${info.item.duration}")
            println("dash=${info.dashManifestUrl} hls=${info.hlsManifestUrl}")
            info.videoStreams.forEach {
                println("  video ${it.width}x${it.height}@${it.fps} ${it.mimeType} ${it.codecs} only=${it.isVideoOnly}")
                println("    index=${it.segmentIndex} length=${it.contentLength} ua=${it.httpHeaders["User-Agent"]}")
            }
            info.audioStreams.forEach { println("  audio ${it.mimeType} ${it.codecs} ${it.bitrate} ${it.languageTag}") }
            println("related=${info.related.size} chapters=${info.chapters.size} subtitles=${info.subtitles.size}")
            assertTrue(info.videoStreams.any { it.isVideoOnly && it.segmentIndex != null })
            assertTrue(info.videoStreams.isNotEmpty() || info.dashManifestUrl != null)
            assertTrue(info.audioStreams.isNotEmpty())
        }

    @Test
    fun explore() =
        liveTest {
            val sections = provider.explore(Region("US"))
            sections.forEach { println("explore ${it.kind}: ${it.items.size} items, e.g. ${it.items.first().title}") }
            assertTrue(sections.isNotEmpty())
        }
}
