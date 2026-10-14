// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey

/** Destinations shown in the navigation bar / rail; each keeps its own back stack. */
@Serializable
sealed interface TopLevelRoute : Route

@Serializable
data object HomeRoute : TopLevelRoute

@Serializable
data object SearchRoute : TopLevelRoute

@Serializable
data object LibraryRoute : TopLevelRoute

@Serializable
data object DownloadsRoute : TopLevelRoute

@Serializable
data object SettingsRoute : TopLevelRoute

/** [sharedKey] identifies the thumbnail that was tapped, for the shared-element transition. */
@Serializable
data class WatchRoute(
    val videoId: String,
    val sharedKey: String,
    /** Set when the video was started from a playlist, which then plays on in order. */
    val playlistId: String? = null,
) : Route

@Serializable
data class ChannelRoute(
    val channelId: String,
) : Route

@Serializable
data class PlaylistRoute(
    val playlistId: String,
) : Route

/** A finished download played from disk. */
@Serializable
data class OfflineWatchRoute(
    val downloadId: String,
) : Route

/** A playlist made on this device. */
@Serializable
data class LocalPlaylistRoute(
    val playlistId: Long,
) : Route

/** Import, export and QR transfer of subscriptions and playlists. */
@Serializable
data object TransferRoute : Route

/** Watching statistics from the local history. */
@Serializable
data object StatsRoute : Route

/** Search over history, watch later, downloads and local playlists. */
@Serializable
data object LibrarySearchRoute : Route
