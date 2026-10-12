// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import java.time.Duration

sealed interface SearchItem {
    data class Stream(
        val item: StreamItem,
    ) : SearchItem

    data class ChannelResult(
        val channel: ChannelRef,
        val subscriberCount: Long?,
        val videoCount: Long?,
        val description: String?,
    ) : SearchItem

    data class PlaylistResult(
        val playlist: Playlist,
    ) : SearchItem
}

/**
 * Search filters. YouTube's public search only lets clients pick the result type, so
 * [duration] is applied to the returned page by the provider.
 */
data class SearchFilter(
    val type: Type = Type.All,
    val duration: DurationFilter = DurationFilter.Any,
) {
    enum class Type { All, Videos, Channels, Playlists }

    /** Same buckets as YouTube: under 4 minutes, 4–20 minutes, over 20 minutes. */
    enum class DurationFilter(
        private val range: ClosedRange<Duration>?,
    ) {
        Any(null),
        Short(Duration.ZERO..SHORT_LIMIT),
        Medium(SHORT_LIMIT..LONG_LIMIT),
        Long(LONG_LIMIT..Duration.ofDays(1)),
        ;

        /** Items without a known duration (channels, playlists, live) are kept only for [Any]. */
        fun matches(item: SearchItem): Boolean {
            val duration = (item as? SearchItem.Stream)?.item?.duration
            return range == null || (duration != null && duration in range)
        }
    }

    private companion object {
        val SHORT_LIMIT: Duration = Duration.ofMinutes(4)
        val LONG_LIMIT: Duration = Duration.ofMinutes(20)
    }
}
