// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.watch

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

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
