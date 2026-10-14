// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Moves text to another phone through a series of QR codes shown one after another. The text is
 * compressed and cut into frames `YT1|session|index|total|data`; the receiver collects frames in
 * any order (a frame may be seen several times) until all of them are there.
 */
object QrTransfer {
    private const val PREFIX = "YT1"
    private const val SEPARATOR = '|'
    private const val DEFAULT_FRAME_CHARS = 500

    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun frames(
        text: String,
        session: String = newSession(),
        frameChars: Int = DEFAULT_FRAME_CHARS,
    ): List<String> {
        val data = encoder.encodeToString(gzip(text.toByteArray(Charsets.UTF_8)))
        val chunks = data.chunked(frameChars.coerceAtLeast(1)).ifEmpty { listOf("") }
        return chunks.mapIndexed { index, chunk ->
            listOf(PREFIX, session, index, chunks.size, chunk).joinToString(SEPARATOR.toString())
        }
    }

    private fun newSession(): String =
        java.util.UUID
            .randomUUID()
            .toString()
            .take(SESSION_LENGTH)

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    private const val SESSION_LENGTH = 6
    private const val FIELD_COUNT = 5
    private const val INDEX_FIELD = 2
    private const val TOTAL_FIELD = 3
    private const val DATA_FIELD = 4

    /** Collects the frames of one transfer. Frames of another transfer are ignored once one has started. */
    class Receiver {
        private var session: String? = null
        private var total = 0
        private val parts = HashMap<Int, String>()

        val received: Int get() = parts.size
        val expected: Int get() = total
        val isComplete: Boolean get() = total > 0 && parts.size == total

        /** Whether [frame] belongs to a YouTubely transfer and was accepted. */
        fun accept(frame: String): Boolean {
            val frameInfo = Frame.parse(frame)
            val sameTransfer = session == null || session == frameInfo?.session
            if (frameInfo == null || !sameTransfer) return false
            session = frameInfo.session
            total = frameInfo.count
            parts[frameInfo.index] = frameInfo.data
            return true
        }

        private class Frame(
            val session: String,
            val index: Int,
            val count: Int,
            val data: String,
        ) {
            companion object {
                fun parse(frame: String): Frame? {
                    val fields = frame.split(SEPARATOR, limit = FIELD_COUNT)
                    val index = fields.getOrNull(INDEX_FIELD)?.toIntOrNull()
                    val count = fields.getOrNull(TOTAL_FIELD)?.toIntOrNull()
                    val valid =
                        fields.size == FIELD_COUNT && fields[0] == PREFIX &&
                            index != null && count != null && count > 0 && index in 0 until count
                    return if (valid) Frame(fields[1], index!!, count!!, fields[DATA_FIELD]) else null
                }
            }
        }

        /** The transferred text once [isComplete], `null` before that or when the data is damaged. */
        fun text(): String? {
            if (!isComplete) return null
            val data = (0 until total).joinToString("") { parts.getValue(it) }
            return runCatching { String(gunzip(decoder.decode(data)), Charsets.UTF_8) }.getOrNull()
        }
    }
}
