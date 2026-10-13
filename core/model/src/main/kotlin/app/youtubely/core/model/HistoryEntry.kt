// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import java.time.Instant

/** A video in the local watch history with the position playback stopped at. */
data class HistoryEntry(
    val item: StreamItem,
    val positionMs: Long,
    val watchedAt: Instant,
) {
    /** Watched share 0..1, or `null` when the length is unknown (live streams). */
    val progress: Float?
        get() =
            item.duration
                ?.toMillis()
                ?.takeIf { it > 0 }
                ?.let { (positionMs.toFloat() / it).coerceIn(0f, 1f) }
}
