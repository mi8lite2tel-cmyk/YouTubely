// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import java.net.URI
import java.net.URLDecoder

/** Something a YouTube link points at. */
sealed interface YouTubeLink {
    /** [playlist] is set when the link also names the playlist the video belongs to. */
    data class Video(
        val id: VideoId,
        val playlist: PlaylistId? = null,
    ) : YouTubeLink

    data class Playlist(
        val id: PlaylistId,
    ) : YouTubeLink

    data class Channel(
        val id: ChannelId,
    ) : YouTubeLink

    companion object {
        private val HOSTS = setOf("youtube.com", "youtu.be", "youtube-nocookie.com")
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        private val CHANNEL_ID = Regex("UC[A-Za-z0-9_-]{22}")
        private val VIDEO_PATHS = setOf("shorts", "live", "embed", "v")

        /**
         * Understands watch, short, live and embed links, `youtu.be` links, playlists and
         * `/channel/UC…` pages, also with `www.`, `m.` or `music.` in front. Anything else
         * (handles, search pages, other sites) gives `null`.
         */
        fun parse(text: String): YouTubeLink? {
            val uri = runCatching { URI(text.trim()) }.getOrNull()
            val host =
                uri
                    ?.host
                    ?.lowercase()
                    ?.removePrefix("www.")
                    ?.removePrefix("m.")
                    ?.removePrefix("music.")
            return if (uri == null || host == null || host !in HOSTS) null else parse(uri, host)
        }

        private fun parse(
            uri: URI,
            host: String,
        ): YouTubeLink? {
            val segments =
                uri.path
                    .orEmpty()
                    .split('/')
                    .filter { it.isNotEmpty() }
            val query = queryOf(uri.rawQuery)
            val playlist = query["list"]?.takeIf { it.isNotBlank() }?.let(::PlaylistId)
            return when {
                host == "youtu.be" -> {
                    videoLink(segments.firstOrNull(), playlist)
                }

                segments.firstOrNull() == "watch" -> {
                    // A watch link that names only a playlist opens the playlist.
                    videoLink(query["v"], playlist) ?: playlist?.let(YouTubeLink::Playlist)
                }

                segments.firstOrNull() in VIDEO_PATHS -> {
                    videoLink(segments.getOrNull(1), playlist)
                }

                segments.firstOrNull() == "playlist" -> {
                    playlist?.let(YouTubeLink::Playlist)
                }

                segments.firstOrNull() == "channel" -> {
                    segments.getOrNull(1)?.takeIf(CHANNEL_ID::matches)?.let { Channel(ChannelId(it)) }
                }

                else -> {
                    null
                }
            }
        }

        private fun videoLink(
            id: String?,
            playlist: PlaylistId?,
        ): YouTubeLink? = id?.takeIf(VIDEO_ID::matches)?.let { Video(VideoId(it), playlist) }

        private fun queryOf(raw: String?): Map<String, String> =
            raw
                .orEmpty()
                .split('&')
                .filter { it.contains('=') }
                .associate {
                    URLDecoder.decode(it.substringBefore('='), "UTF-8") to
                        URLDecoder.decode(it.substringAfter('='), "UTF-8")
                }

        /** The first link found in free text such as a shared message. */
        fun find(text: String): YouTubeLink? =
            text.split(Regex("\\s+")).firstNotNullOfOrNull { parse(it.trim(*WRAPPING_CHARS)) }

        /** Brackets, quotes and punctuation that a message puts around a link. */
        private val WRAPPING_CHARS = charArrayOf('(', ')', '[', ']', '<', '>', '"', '\'', '.', ',', ';', ':', '!', '?')
    }
}
