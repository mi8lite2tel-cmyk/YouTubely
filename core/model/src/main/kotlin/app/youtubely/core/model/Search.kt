// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

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

data class SearchFilter(
    val type: Type = Type.All,
    val duration: DurationFilter = DurationFilter.Any,
    val uploadDate: UploadDate = UploadDate.Any,
    val sort: Sort = Sort.Relevance,
) {
    enum class Type { All, Videos, Channels, Playlists }

    enum class DurationFilter { Any, Short, Medium, Long }

    enum class UploadDate { Any, LastHour, Today, ThisWeek, ThisMonth, ThisYear }

    enum class Sort { Relevance, UploadDate, ViewCount, Rating }
}
