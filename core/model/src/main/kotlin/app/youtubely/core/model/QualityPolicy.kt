// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

/** When the app saves mobile data while playing: never, automatically when it matters, or always. */
enum class DataSaverMode { Off, Auto, Always }

/** Which video codec family to prefer when a video offers several. */
enum class CodecPreference {
    /** Efficient codecs while saving data, the player's own choice otherwise. */
    Auto,

    /** VP9 / AV1: fewer bytes for the same picture, needs a decoder in the device. */
    Efficient,

    /** H.264: plays everywhere and is easiest on the battery, but takes more traffic. */
    Compatible,
}

/** Why the automatic quality ended up where it did; shown to the user next to "Auto". */
enum class QualityReason {
    /** The user picked a resolution for this video. */
    Pinned,
    AudioOnly,

    /** The monthly mobile-data allowance is used up. */
    MonthlyLimit,
    DataSaver,
    PowerSave,
    MeteredLimit,
    UnmeteredLimit,

    /** Nothing holds the quality back; only the connection decides. */
    Unlimited,
}

/** Everything about the current situation that decides how sharp the picture may be. */
data class QualityInputs(
    val settings: PlaybackSettings,
    val metered: Boolean,
    /** Android's own Data Saver is switched on for this app. */
    val systemDataSaver: Boolean = false,
    /** Android's Battery Saver is on. */
    val powerSave: Boolean = false,
    /** Share of the monthly mobile allowance already used, `null` when no limit is set. */
    val monthlyUsedFraction: Float? = null,
    /** Resolution the user picked for the current video, `null` = automatic. */
    val pinnedHeight: Int? = null,
)

/** Result of [QualityPolicy.decide]. [maxHeight] and [maxFps] of `null` mean "no cap". */
data class QualityCap(
    val maxHeight: Int?,
    val maxFps: Int?,
    val preferEfficientCodec: Boolean,
    val reason: QualityReason,
)

/**
 * Chooses the ceiling for automatic quality. The player's own bandwidth estimate then picks the
 * best rendition below it, so the ceiling only has to say what is *allowed*, never what is
 * *possible*. Pure and deterministic, which keeps every rule below covered by plain unit tests.
 */
object QualityPolicy {
    const val SAVER_FPS = 30
    const val LOWEST_HEIGHT = 144
    const val POWER_SAVE_HEIGHT = 480
    const val EXHAUSTED_HEIGHT = 360

    /** Auto data saver switches on once this share of the monthly allowance is used. */
    const val SAVER_THRESHOLD = 0.8f

    fun decide(inputs: QualityInputs): QualityCap {
        val settings = inputs.settings
        val saver = isSaving(inputs)
        val efficient = settings.codec == CodecPreference.Efficient || (settings.codec == CodecPreference.Auto && saver)
        val fps = listOfNotNull(settings.maxFps, SAVER_FPS.takeIf { saver }).minOrNull()
        return when {
            settings.audioOnly -> {
                QualityCap(LOWEST_HEIGHT, fps, efficient, QualityReason.AudioOnly)
            }

            inputs.pinnedHeight != null -> {
                QualityCap(inputs.pinnedHeight, null, efficient, QualityReason.Pinned)
            }

            else -> {
                val (height, reason) = heightCeiling(inputs, saver)
                QualityCap(height, fps, efficient, reason)
            }
        }
    }

    /** Whether data saving is in effect right now. */
    fun isSaving(inputs: QualityInputs): Boolean {
        val settings = inputs.settings
        val tight =
            inputs.systemDataSaver || (inputs.monthlyUsedFraction ?: 0f) >= SAVER_THRESHOLD
        return when (settings.dataSaver) {
            DataSaverMode.Off -> false
            DataSaverMode.Always -> true
            DataSaverMode.Auto -> inputs.metered && tight
        }
    }

    private fun heightCeiling(
        inputs: QualityInputs,
        saver: Boolean,
    ): Pair<Int?, QualityReason> {
        val settings = inputs.settings
        val network = if (inputs.metered) settings.maxHeightMetered else settings.maxHeightUnmetered
        val networkReason = if (inputs.metered) QualityReason.MeteredLimit else QualityReason.UnmeteredLimit
        val exhausted =
            settings.dataSaver != DataSaverMode.Off && inputs.metered && (inputs.monthlyUsedFraction ?: 0f) >= 1f
        // Ordered by priority: on equal heights the first reason wins.
        val candidates =
            listOfNotNull(
                (EXHAUSTED_HEIGHT to QualityReason.MonthlyLimit).takeIf { exhausted },
                (settings.dataSaverMaxHeight to QualityReason.DataSaver).takeIf { saver },
                (POWER_SAVE_HEIGHT to QualityReason.PowerSave).takeIf { settings.limitOnPowerSave && inputs.powerSave },
                network?.let { it to networkReason },
            )
        return candidates.minByOrNull { it.first } ?: (null to QualityReason.Unlimited)
    }
}
