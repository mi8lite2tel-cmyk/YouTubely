// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

data class Playlist(
    val id: PlaylistId,
    val name: String,
    val uploader: ChannelRef?,
    val thumbnails: List<Image>,
    val streamCount: Long?,
)

data class PlaylistPage(
    val playlist: Playlist,
    val items: Paged<StreamItem>,
)
