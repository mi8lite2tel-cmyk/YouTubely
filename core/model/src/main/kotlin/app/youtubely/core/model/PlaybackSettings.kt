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
    /** Downloads wait for an unmetered network (Wi-Fi). */
    val downloadOnWifiOnly: Boolean = true,
    /** Play sound only: the video track is not downloaded at all, which saves data and battery. */
    val audioOnly: Boolean = false,
    /** How far a double tap on the left or right side of the player seeks. */
    val seekStepSeconds: Int = DEFAULT_SEEK_STEP,
    /** Keep a local watch history; when off, nothing new is recorded. */
    val historyEnabled: Boolean = true,
    /** Reopening a video continues where it was left. */
    val resumePlayback: Boolean = true,
    /** Skip stretches of silence (speeds up talks and podcasts a little). */
    val skipSilence: Boolean = false,
    /** Turn loud videos down to the common level, like YouTube's stable volume. */
    val normalizeVolume: Boolean = true,
    /** When a video ends and nothing is queued, play the first suggested one. */
    val autoplayNext: Boolean = true,
    /** Show subtitles when the video has them. */
    val subtitlesEnabled: Boolean = false,
    /** BCP 47 language of the subtitles to prefer; `null` = the app's language. */
    val subtitleLanguage: String? = null,
    /** Subtitle text size as a multiple of the normal size. */
    val subtitleScale: Float = 1f,
    /** Dark box behind subtitles. */
    val subtitleBackground: Boolean = true,
) {
    companion object {
        const val DEFAULT_METERED_LIMIT = 720
        const val DEFAULT_SEEK_STEP = 10
        val SEEK_STEP_CHOICES = listOf(5, 10, 15, 30)
        val HEIGHT_CHOICES = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)
        val SUBTITLE_SCALES = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    }
}
