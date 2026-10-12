// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

/** Public, non-personalized charts shown on the home screen. */
enum class ExploreKind {
    Trending,
    Music,
    Gaming,
    Live,
    Podcasts,
    MoviesAndShows,
}

data class ExploreSection(
    val kind: ExploreKind,
    val items: List<StreamItem>,
)
