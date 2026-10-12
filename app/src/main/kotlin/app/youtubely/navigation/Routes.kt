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
data object DownloadsRoute : TopLevelRoute

@Serializable
data object SettingsRoute : TopLevelRoute

/** [sharedKey] identifies the thumbnail that was tapped, for the shared-element transition. */
@Serializable
data class WatchRoute(
    val videoId: String,
    val sharedKey: String,
) : Route

/** A finished download played from disk. */
@Serializable
data class OfflineWatchRoute(
    val downloadId: String,
) : Route
