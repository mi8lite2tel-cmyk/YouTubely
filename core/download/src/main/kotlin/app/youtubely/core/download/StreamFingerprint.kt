// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

/**
 * Identifies the streams a half-finished download was loading. Links expire and the extractor is
 * asked again on every attempt; if it now picks another stream (other codec, size or bitrate),
 * appending to the old half would glue two different files together. A changed fingerprint
 * means: start the parts from zero.
 */
object StreamFingerprint {
    fun of(selected: SelectedStreams): String =
        listOf(
            selected.video?.let { "v:${it.mimeType}|${it.codecs}|${it.height}|${it.bitrate}|${it.contentLength}" }
                ?: "v:-",
            "a:${selected.audio.mimeType}|${selected.audio.codecs}|${selected.audio.bitrate}|${selected.audio.contentLength}",
        ).joinToString("\n")

    /** `true` when the parts on disk belong to the streams about to be loaded (or there are none yet). */
    fun matches(
        stored: String?,
        current: String,
    ): Boolean = stored == null || stored == current
}
