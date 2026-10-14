// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers how fast the connection was the last times, separately for Wi-Fi and mobile data.
 * The player starts its next video from this value instead of a pessimistic guess, so the first
 * seconds already have the right quality. Read synchronously because the player needs it at once.
 */
@Singleton
class BandwidthStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("bandwidth", Context.MODE_PRIVATE)

        /** Bits per second, or `null` when nothing was measured yet. */
        fun estimate(metered: Boolean): Long? = prefs.getLong(key(metered), 0L).takeIf { it > 0 }

        fun record(
            metered: Boolean,
            bitsPerSecond: Long,
        ) {
            if (bitsPerSecond < MIN_SAMPLE_BPS) return
            prefs.edit { putLong(key(metered), blend(estimate(metered), bitsPerSecond)) }
        }

        private fun key(metered: Boolean) = if (metered) "metered" else "unmetered"

        companion object {
            /** Below this a measurement is noise (a stalled transfer), not a connection speed. */
            const val MIN_SAMPLE_BPS = 50_000L
            private const val KEEP = 0.7
            private const val TAKE = 1 - KEEP

            /** Moving average: a single odd measurement must not erase what earlier ones showed. */
            fun blend(
                old: Long?,
                sample: Long,
            ): Long = if (old == null) sample else (old * KEEP + sample * TAKE).toLong()
        }
    }
