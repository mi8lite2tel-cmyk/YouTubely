// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

data class PlaybackSettings(
    /** Highest resolution on unmetered networks (Wi-Fi), `null` = no limit. */
    val maxHeightUnmetered: Int? = null,
    /** Highest resolution on metered networks (mobile data), `null` = no limit. */
    val maxHeightMetered: Int? = DEFAULT_METERED_LIMIT,
    /** Keep playing with the screen off or the app in the background. */
    val backgroundPlayback: Boolean = true,
    /** Shrink into a picture-in-picture window when leaving the app during a video. */
    val pictureInPicture: Boolean = true,
) {
    companion object {
        const val DEFAULT_METERED_LIMIT = 720
        val HEIGHT_CHOICES = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)
    }
}
