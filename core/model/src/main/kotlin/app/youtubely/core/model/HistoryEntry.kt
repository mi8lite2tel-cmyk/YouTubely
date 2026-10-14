// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import java.time.Duration
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

/**
 * Videos worth continuing: started (past the first seconds), not finished, watched within
 * [maxAge] of [now]; newest first, at most [limit]. Live streams have no position to return to.
 */
fun List<HistoryEntry>.unfinished(
    now: Instant,
    maxAge: Duration = Duration.ofDays(CONTINUE_DAYS),
    limit: Int = CONTINUE_LIMIT,
): List<HistoryEntry> =
    filter { entry ->
        val length = entry.item.duration?.toMillis() ?: return@filter false
        entry.positionMs >= CONTINUE_MIN_MS &&
            entry.positionMs < length - CONTINUE_END_MS &&
            entry.positionMs < length * CONTINUE_DONE_SHARE &&
            !entry.watchedAt.isBefore(now.minus(maxAge))
    }.sortedByDescending { it.watchedAt }
        .take(limit)

private const val CONTINUE_DAYS = 30L
private const val CONTINUE_LIMIT = 12
private const val CONTINUE_MIN_MS = 30_000L
private const val CONTINUE_END_MS = 30_000L
private const val CONTINUE_DONE_SHARE = 0.95
