// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import app.youtubely.core.model.SponsorSegment
import app.youtubely.core.model.VideoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** A stretch that was just skipped automatically. */
data class SkipNotice(
    val segment: SponsorSegment,
)

/**
 * Jumps over the SponsorBlock stretches of the video in the player. It lives as long as the
 * player, so skipping goes on in the mini player and in the background, not only on the video screen.
 */
@Singleton
class SegmentSkipper
    @Inject
    constructor(
        private val playback: PlaybackManager,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var job: Job? = null
        private val ignored = mutableSetOf<SponsorSegment>()

        /** The video the segments belong to; `null` when there are none. */
        var video: VideoId? = null
            private set

        private val _segments = MutableStateFlow<List<SponsorSegment>>(emptyList())

        /** Stretches marked in the current video, for the progress bar. */
        val segments: StateFlow<List<SponsorSegment>> = _segments.asStateFlow()

        private val _notice = MutableStateFlow<SkipNotice?>(null)

        /** The last automatic skip, shown for a few seconds with an undo button. */
        val notice: StateFlow<SkipNotice?> = _notice.asStateFlow()

        /** Starts skipping [segments] of [video]; it ends by itself when another video plays. */
        fun start(
            video: VideoId,
            segments: List<SponsorSegment>,
        ) {
            clear()
            this.video = video
            _segments.value = segments
            if (segments.isEmpty()) return
            job =
                scope.launch {
                    while (isActive && playback.currentVideo.value == video) {
                        delay(CHECK_MS)
                        hit(segments)?.let(::skip)
                    }
                    if (this@SegmentSkipper.video == video) clear()
                }
        }

        fun clear() {
            job?.cancel()
            job = null
            video = null
            ignored.clear()
            _segments.value = emptyList()
            _notice.value = null
        }

        /** Puts the player back where the skipped stretch began and leaves that stretch alone from now on. */
        fun undo() {
            val notice = _notice.value ?: return
            ignored += notice.segment
            playback.player.seekTo(notice.segment.startMs)
            _notice.value = null
        }

        private fun hit(segments: List<SponsorSegment>): SponsorSegment? {
            val player = playback.player
            if (!player.isPlaying) return null
            val position = player.currentPosition
            return segments.firstOrNull {
                it !in ignored && position >= it.startMs && position < it.endMs - END_MARGIN_MS
            }
        }

        private fun skip(segment: SponsorSegment) {
            playback.player.seekTo(segment.endMs)
            _notice.value = SkipNotice(segment)
            scope.launch {
                delay(NOTICE_MS)
                if (_notice.value?.segment == segment) _notice.value = null
            }
        }

        private companion object {
            const val CHECK_MS = 250L
            const val END_MARGIN_MS = 500L
            const val NOTICE_MS = 6_000L
        }
    }
