// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import androidx.media3.common.PlaybackException

/** What to do when playback stops with an error: try again, and how soon. */
object PlaybackRecovery {
    /** After this many failed tries in a row the player gives up and shows the error. */
    const val MAX_ATTEMPTS = 5
    private const val FIRST_DELAY_MS = 2_000L
    private const val MAX_DELAY_MS = 30_000L
    private const val MAX_DOUBLINGS = 4

    /** Waiting grows 2 s, 4 s, 8 s, 16 s, 30 s: quick for a blip, gentle for a long outage. */
    fun retryDelayMs(attempt: Int): Long =
        (FIRST_DELAY_MS shl attempt.coerceIn(0, MAX_DOUBLINGS)).coerceAtMost(MAX_DELAY_MS)

    /** Errors that a later try can fix: the connection, a server hiccup, or links that expired. */
    fun isRecoverable(errorCode: Int): Boolean =
        when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            -> true

            else -> false
        }

    /** A paused live stream fell out of its recording window: jump to the live edge and go on. */
    fun needsLiveEdge(errorCode: Int): Boolean = errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW

    /** Expired or refused links need fresh ones from the extractor, not just another attempt. */
    fun needsFreshLinks(
        errorCode: Int,
        attempt: Int,
    ): Boolean = errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS || attempt >= 2
}
