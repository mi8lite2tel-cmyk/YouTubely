// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.watch

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.Player

/**
 * Whether the player should pause because the app went to the background with background
 * playback off. Leaving a screen (to the mini player) or rotating the phone is not leaving the app.
 */
internal fun shouldPauseForBackground(
    activity: Activity?,
    backgroundPlayback: Boolean,
): Boolean = activity != null && !backgroundPlayback && !activity.isChangingConfigurations && isInBackground(activity)

/** The app is out of sight (home, screen off). Picture-in-picture and rotation do not count. */
internal fun isInBackground(activity: Activity?): Boolean {
    val started = (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
    return activity != null && !activity.isChangingConfigurations && !started
}

/**
 * What a video screen does as the app comes and goes: take the player back when shown, and when
 * the app goes to the background drop the picture (sound goes on, a picture nobody sees only
 * costs data) and, without background playback, pause.
 */
@Composable
internal fun PlaybackLifecycleEffect(
    key: Any?,
    player: Player,
    backgroundPlayback: Boolean,
    onVisible: () -> Unit,
    onVideoSuspended: (Boolean) -> Unit,
) {
    val activity = LocalActivity.current
    val background by rememberUpdatedState(backgroundPlayback)
    LifecycleStartEffect(key) {
        onVisible()
        onVideoSuspended(false)
        onStopOrDispose {
            if (isInBackground(activity)) onVideoSuspended(true)
            // PiP keeps the activity started, so it does not pause.
            if (shouldPauseForBackground(activity, background)) player.pause()
        }
    }
}
