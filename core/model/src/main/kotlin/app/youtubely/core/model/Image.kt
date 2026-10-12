// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

/** One rendition of an image (thumbnail, avatar, banner). Dimensions are unknown for some sources. */
data class Image(
    val url: String,
    val width: Int?,
    val height: Int?,
)

/** Picks the smallest image that is at least [minWidth] wide, or the largest one available. */
fun List<Image>.bestFor(minWidth: Int): Image? {
    val sized = filter { it.width != null }.sortedBy { it.width }
    return sized.firstOrNull { it.width!! >= minWidth } ?: sized.lastOrNull() ?: firstOrNull()
}
