// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryTest {
    @Test
    fun waitingGrowsAndIsCapped() {
        assertEquals(
            listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L),
            (0..5).map(PlaybackRecovery::retryDelayMs),
        )
        assertEquals(2_000L, PlaybackRecovery.retryDelayMs(-3))
    }

    @Test
    fun onlyConnectionProblemsAreWorthRetrying() {
        assertTrue(PlaybackRecovery.isRecoverable(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(PlaybackRecovery.isRecoverable(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertFalse(PlaybackRecovery.isRecoverable(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertFalse(PlaybackRecovery.isRecoverable(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
    }

    @Test
    fun expiredLinksAndStubbornErrorsAskForFreshLinks() {
        assertTrue(PlaybackRecovery.needsFreshLinks(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, attempt = 0))
        assertFalse(
            PlaybackRecovery.needsFreshLinks(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, attempt = 1),
        )
        assertTrue(
            PlaybackRecovery.needsFreshLinks(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, attempt = 2),
        )
    }
}
