// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import android.content.Context
import android.content.res.Resources
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import app.youtubely.core.data.NetworkMonitor
import app.youtubely.core.data.NetworkState
import app.youtubely.core.data.TrafficMeter
import app.youtubely.core.model.CodecPreference
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.QualityCap
import app.youtubely.core.model.QualityInputs
import app.youtubely.core.model.QualityPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/** Size of the area the video is shown in, in pixels. */
data class Viewport(
    val width: Int,
    val height: Int,
)

/** What the player has to know besides the settings to choose its quality. */
data class QualitySituation(
    val network: NetworkState = NetworkState(),
    val monthlyUsedFraction: Float? = null,
    val viewport: Viewport? = null,
)

/**
 * Turns [QualityPolicy]'s ceiling and the live situation (connection, power mode, mobile data
 * used this month, size of the player window) into ExoPlayer track-selection parameters.
 */
@OptIn(UnstableApi::class)
@Singleton
class QualityController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        network: NetworkMonitor,
        traffic: TrafficMeter,
    ) {
        private val viewport = MutableStateFlow<Viewport?>(null)

        /** Changes whenever the connection, the power mode, the allowance or the window size does. */
        val situation: Flow<QualitySituation> =
            combine(network.state, traffic.monthlyUsedFraction, settledViewport()) { state, used, size ->
                QualitySituation(state, used, size)
            }

        /** The window size without the flood of values while it animates (full screen, picture-in-picture). */
        @OptIn(FlowPreview::class)
        private fun settledViewport(): Flow<Viewport?> =
            viewport.debounce(VIEWPORT_DEBOUNCE_MS).onStart { emit(viewport.value) }

        private val _cap = MutableStateFlow<QualityCap?>(null)

        /** The ceiling in effect for the video that plays now; `null` before the first one. */
        val cap: StateFlow<QualityCap?> = _cap.asStateFlow()

        /** Called when the player view changes size (inline, full screen, picture-in-picture). */
        fun setViewport(size: Viewport?) {
            viewport.value = size
        }

        /** Writes the video constraints into [parameters]; returns the ceiling that was applied. */
        @Suppress("SpreadOperator") // Media3 takes the codecs as a vararg; there are at most three.
        fun configure(
            parameters: TrackSelectionParameters.Builder,
            settings: PlaybackSettings,
            situation: QualitySituation,
            pinnedHeight: Int?,
        ): QualityCap {
            val cap =
                QualityPolicy.decide(
                    QualityInputs(
                        settings = settings,
                        metered = situation.network.metered,
                        systemDataSaver = situation.network.systemDataSaver,
                        powerSave = situation.network.powerSave,
                        monthlyUsedFraction = situation.monthlyUsedFraction,
                        pinnedHeight = pinnedHeight,
                    ),
                )
            parameters.setMaxVideoFrameRate(cap.maxFps ?: Int.MAX_VALUE)
            parameters.setPreferredVideoMimeTypes(*preferredCodecs(settings, cap))
            val height = cap.maxHeight
            when {
                settings.audioOnly -> {
                    parameters
                        .setMinVideoSize(
                            0,
                            0,
                        ).setMaxVideoSize(Int.MAX_VALUE, QualityPolicy.LOWEST_HEIGHT)
                }

                pinnedHeight != null -> {
                    parameters
                        .setMinVideoSize(
                            0,
                            pinnedHeight,
                        ).setMaxVideoSize(Int.MAX_VALUE, pinnedHeight)
                }

                else -> {
                    parameters.setMinVideoSize(0, 0).setMaxVideoSize(Int.MAX_VALUE, height ?: Int.MAX_VALUE)
                }
            }
            applyViewport(parameters, settings, situation.viewport, pinnedHeight != null)
            _cap.value = cap
            return cap
        }

        /**
         * The viewport makes the player prefer renditions that fit the window. A margin keeps a
         * 1080-pixel-wide phone from dropping to a soft 480p when 720p would still look sharp.
         */
        private fun applyViewport(
            parameters: TrackSelectionParameters.Builder,
            settings: PlaybackSettings,
            size: Viewport?,
            pinned: Boolean,
        ) {
            if (!settings.fitToPlayerSize || pinned || size == null || size.width <= 0 || size.height <= 0) {
                parameters.setViewportSizeToPhysicalDisplaySize(context, true)
                return
            }
            val display = Resources.getSystem().displayMetrics
            val longSide = max(display.widthPixels, display.heightPixels)
            val shortSide = min(display.widthPixels, display.heightPixels)
            parameters.setViewportSize(
                min((size.width * VIEWPORT_MARGIN).toInt(), longSide),
                min((size.height * VIEWPORT_MARGIN).toInt(), shortSide),
                true,
            )
        }

        private fun preferredCodecs(
            settings: PlaybackSettings,
            cap: QualityCap,
        ): Array<String> =
            when {
                settings.audioOnly -> emptyArray()
                cap.preferEfficientCodec -> arrayOf(MimeTypes.VIDEO_VP9, MimeTypes.VIDEO_AV1, MimeTypes.VIDEO_H264)
                settings.codec == CodecPreference.Compatible -> arrayOf(MimeTypes.VIDEO_H264)
                else -> emptyArray()
            }

        private companion object {
            const val VIEWPORT_MARGIN = 1.5f
            const val VIEWPORT_DEBOUNCE_MS = 400L
        }
    }
