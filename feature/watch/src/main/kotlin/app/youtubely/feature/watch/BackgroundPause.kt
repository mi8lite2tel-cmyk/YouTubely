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
): Boolean {
    val leaving = activity != null && !backgroundPlayback && !activity.isChangingConfigurations
    val started = (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
    return leaving && !started
}
