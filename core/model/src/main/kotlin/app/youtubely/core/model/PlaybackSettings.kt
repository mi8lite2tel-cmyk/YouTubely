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
    /** Saves mobile data while playing: lower ceiling, 30 fps, efficient codecs, shorter buffer. */
    val dataSaver: DataSaverMode = DataSaverMode.Auto,
    /** Highest resolution while the data saver is active. */
    val dataSaverMaxHeight: Int = DEFAULT_SAVER_HEIGHT,
    /** Highest frame rate, `null` = no limit. */
    val maxFps: Int? = null,
    val codec: CodecPreference = CodecPreference.Auto,
    /** Keep the picture no sharper than the player window can show (small window, PiP). */
    val fitToPlayerSize: Boolean = true,
    /** Cap at 480p while Android's Battery Saver is on. */
    val limitOnPowerSave: Boolean = true,
    /** Reopen a channel's videos with the quality and speed last used there. */
    val rememberPerChannel: Boolean = true,
    /** Mobile data allowance per month in megabytes; 0 = none. Drives the data saver and the Traffic screen. */
    val monthlyMobileLimitMb: Int = 0,
    /** Parallel connections per download file. */
    val downloadConnections: Int = DEFAULT_CONNECTIONS,
) {
    companion object {
        const val DEFAULT_METERED_LIMIT = 720
        const val DEFAULT_SEEK_STEP = 10
        const val DEFAULT_SAVER_HEIGHT = 480
        const val DEFAULT_CONNECTIONS = 4
        val SAVER_HEIGHT_CHOICES = listOf(720, 480, 360, 240)
        val FPS_CHOICES = listOf(30, 60)
        val CONNECTION_CHOICES = listOf(1, 2, 4, 6, 8)
        val MONTHLY_LIMIT_CHOICES_MB = listOf(0, 1024, 3072, 5120, 10240, 20480)
        val SEEK_STEP_CHOICES = listOf(5, 10, 15, 30)
        val HEIGHT_CHOICES = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)
        val SUBTITLE_SCALES = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    }
}
