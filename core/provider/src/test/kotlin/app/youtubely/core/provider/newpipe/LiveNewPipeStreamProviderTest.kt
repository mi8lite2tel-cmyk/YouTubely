// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.provider.newpipe

import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelTab
import app.youtubely.core.model.PlaylistId
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

    @Test
    fun channel() =
        liveTest {
            // Blender: has videos, shorts, live and playlists tabs.
            val id = ChannelId("UCSMOQeBJ2RAnuFungnQOxLg")
            val page = provider.channel(id, ChannelTab.Videos, null)
            println("channel ${page.channel.name} tabs=${page.channel.tabs} subs=${page.channel.subscriberCount}")
            println("  videos=${page.items.items.size} next=${page.items.nextPage != null}")
            assertTrue(page.items.items.isNotEmpty())
            val more = provider.channel(id, ChannelTab.Videos, page.items.nextPage)
            println("  page 2: ${more.items.items.size}")
            assertTrue(more.items.items.isNotEmpty())
            val playlists = provider.channel(id, ChannelTab.Playlists, null)
            println("  playlists=${playlists.items.items.size}")
            val shorts = provider.channel(id, ChannelTab.Shorts, null)
            println("  shorts=${shorts.items.items.size}")
        }

    @Test
    fun playlist() =
        liveTest {
            val page = provider.playlist(PlaylistId("PLa1F2ddGya_-UvuAqHAksYnB0qL9yWDO6"), null)
            println("playlist ${page.playlist.name} by ${page.playlist.uploader?.name}: ${page.items.items.size} items")
            assertTrue(page.items.items.isNotEmpty())
        }

    @Test
    fun channelFeed() =
        liveTest {
            val items = provider.channelFeed(ChannelId("UCSMOQeBJ2RAnuFungnQOxLg"))
            println("feed: ${items.size} items, newest ${items.firstOrNull()?.title} at ${items.firstOrNull()?.uploadedAt}")
            assertTrue(items.isNotEmpty())
        }
}
