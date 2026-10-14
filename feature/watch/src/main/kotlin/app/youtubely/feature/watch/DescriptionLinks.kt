// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.watch

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

private val Timecode = Regex("(?<![\\d:])(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})(?![\\d:])")
private val WebLink = Regex("https?://[^\\s<>\"']+")
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
private const val MILLIS = 1_000L
private const val MAX_MINUTES_OR_SECONDS = 59

/** The milliseconds a timecode like `1:02:03` or `12:34` stands for, or `null` when it is not one. */
internal fun timecodeMillis(text: String): Long? {
    val match = Timecode.matchEntire(text) ?: return null
    val hours = match.groupValues[1].toIntOrNull() ?: 0
    val minutes = match.groupValues[2].toInt()
    val seconds = match.groupValues[3].toInt()
    if (seconds > MAX_MINUTES_OR_SECONDS || (hours > 0 && minutes > MAX_MINUTES_OR_SECONDS)) return null
    return (hours * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE + seconds) * MILLIS
}

/**
 * The description with its timecodes turned into links that seek the video and its web addresses
 * into links that open in the browser. [onSeek] gets the position in milliseconds.
 */
internal fun linkify(
    text: String,
    linkColor: androidx.compose.ui.graphics.Color,
    onSeek: (Long) -> Unit,
): AnnotatedString {
    val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    // Web links first; timecodes inside a web address are left alone.
    val webRanges = WebLink.findAll(text).map { it.range }.toList()
    return buildAnnotatedString {
        append(text)
        webRanges.forEach { range ->
            addLink(LinkAnnotation.Url(text.substring(range), style), range.first, range.last + 1)
        }
        Timecode.findAll(text).forEach { match ->
            val millis = timecodeMillis(match.value) ?: return@forEach
            if (webRanges.none { match.range.first in it }) {
                addLink(
                    LinkAnnotation.Clickable(match.value, style) { onSeek(millis) },
                    match.range.first,
                    match.range.last + 1,
                )
            }
        }
    }
}
