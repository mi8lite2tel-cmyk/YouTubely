// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

enum class ChannelTab {
    Videos,
    Shorts,
    Live,
    Playlists,
}

data class Channel(
    val id: ChannelId,
    val name: String,
    val handle: String?,
    val description: String,
    val avatars: List<Image>,
    val banners: List<Image>,
    val subscriberCount: Long?,
    val isVerified: Boolean,
    val tabs: List<ChannelTab>,
)

data class ChannelPage(
    val channel: Channel,
    val tab: ChannelTab,
    val items: Paged<SearchItem>,
)
