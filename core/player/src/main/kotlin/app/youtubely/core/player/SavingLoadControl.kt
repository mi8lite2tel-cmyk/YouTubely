// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import androidx.annotation.OptIn
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator

/**
 * The stock load control with a switchable ceiling on how far ahead the player buffers. A short
 * buffer wastes less traffic when the viewer closes the video or jumps ahead, so the data saver
 * turns it on; otherwise the delegate decides alone.
 */
@OptIn(UnstableApi::class)
internal class SavingLoadControl(
    private val delegate: LoadControl,
) : LoadControl {
    /** Seconds to buffer ahead at most, `null` = whatever the delegate wants. */
    @Volatile
    var maxBufferMs: Long? = null

    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) = delegate.onTracksSelected(parameters, trackGroups, trackSelections)

    override fun onTracksSelected(
        playerId: PlayerId,
        timeline: Timeline,
        mediaPeriodId: MediaPeriodId,
        renderers: Array<out Renderer>,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) = delegate.onTracksSelected(playerId, timeline, mediaPeriodId, renderers, trackGroups, trackSelections)

    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)

    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)

    override fun getAllocator(playerId: PlayerId): Allocator = delegate.getAllocator(playerId)

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = delegate.getBackBufferDurationUs(playerId)

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean =
        delegate.retainBackBufferFromKeyframe(playerId)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        val cap = maxBufferMs
        if (cap != null && parameters.bufferedDurationUs >= cap * MICROS_PER_MILLI) return false
        return delegate.shouldContinueLoading(parameters)
    }

    override fun shouldContinuePreloading(
        playerId: PlayerId,
        timeline: Timeline,
        mediaPeriodId: MediaPeriodId,
        bufferedDurationUs: Long,
    ): Boolean = delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean =
        delegate.shouldStartPlayback(parameters)

    private companion object {
        const val MICROS_PER_MILLI = 1_000L
    }
}
