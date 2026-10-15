// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.transfer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedReadTest {
    @Test
    fun `reads a stream that fits`() {
        val data = ByteArray(20_000) { it.toByte() }
        assertArrayEquals(data, data.inputStream().readAtMost(20_000))
    }

    @Test
    fun `an empty stream is empty`() {
        assertArrayEquals(ByteArray(0), ByteArray(0).inputStream().readAtMost(10))
    }

    @Test
    fun `a stream over the limit is refused`() {
        assertNull(ByteArray(20_001).inputStream().readAtMost(20_000))
    }
}
