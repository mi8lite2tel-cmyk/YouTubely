// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import java.time.Duration
import java.time.Instant

enum class StreamType {
    Video,
    Short,
    Live,
    Upcoming,
}

/** Minimal channel reference shown next to a video or comment. */
data class ChannelRef(
    val id: ChannelId,
    val name: String,
    val avatars: List<Image>,
    val isVerified: Boolean,
)

/** A video as it appears in lists (feeds, search, related, playlists). */
data class StreamItem(
    val id: VideoId,
    val title: String,
    val channel: ChannelRef?,
    val thumbnails: List<Image>,
    val type: StreamType,
    /** `null` for live streams and when the source does not report it. */
    val duration: Duration?,
    val viewCount: Long?,
    /** Exact upload time when the source provides one. */
    val uploadedAt: Instant?,
    /** Relative text such as "3 days ago" when only an approximate date is known. */
    val uploadedAtText: String?,
)
