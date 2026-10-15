// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YouTubeLinkTest {
    private val id = "dQw4w9WgXcQ"

    @Test
    fun watchLinksAndTheirVariants() {
        val expected = YouTubeLink.Video(VideoId(id))
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?v=$id&t=42s",
            "https://music.youtube.com/watch?v=$id",
            "https://youtu.be/$id?si=abc",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/live/$id",
            "https://www.youtube.com/embed/$id",
        ).forEach { assertEquals(expected, YouTubeLink.parse(it), it) }
    }

    @Test
    fun aVideoInAPlaylistKeepsThePlaylist() {
        assertEquals(
            YouTubeLink.Video(VideoId(id), PlaylistId("PL123")),
            YouTubeLink.parse("https://www.youtube.com/watch?v=$id&list=PL123"),
        )
    }

    @Test
    fun playlistsAndChannels() {
        assertEquals(
            YouTubeLink.Playlist(PlaylistId("PL123")),
            YouTubeLink.parse("https://www.youtube.com/playlist?list=PL123"),
        )
        val channel = "UC" + "a".repeat(22)
        assertEquals(
            YouTubeLink.Channel(ChannelId(channel)),
            YouTubeLink.parse("https://www.youtube.com/channel/$channel"),
        )
    }

    @Test
    fun otherThingsAreNotLinks() {
        assertNull(YouTubeLink.parse("https://example.com/watch?v=$id"))
        assertNull(YouTubeLink.parse("https://www.youtube.com/@someone"))
        assertNull(YouTubeLink.parse("https://www.youtube.com/watch?v=short"))
        assertNull(YouTubeLink.parse("not a url"))
    }

    @Test
    fun findsALinkInSharedText() {
        assertEquals(
            YouTubeLink.Video(VideoId(id)),
            YouTubeLink.find("Look at this https://youtu.be/$id it is great"),
        )
        assertNull(YouTubeLink.find("nothing here"))
    }

    @Test
    fun findsALinkWrappedInPunctuation() {
        val video = YouTubeLink.Video(VideoId(id))
        assertEquals(video, YouTubeLink.find("see https://youtu.be/$id."))
        assertEquals(video, YouTubeLink.find("(https://youtu.be/$id)"))
        assertEquals(video, YouTubeLink.find("<https://youtu.be/$id>, thanks!"))
    }

    @Test
    fun aWatchLinkWithOnlyAPlaylistOpensThePlaylist() {
        assertEquals(
            YouTubeLink.Playlist(PlaylistId("PL123")),
            YouTubeLink.parse("https://www.youtube.com/watch?list=PL123"),
        )
    }
}
