// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import app.youtubely.core.model.VideoId

/** Set by the app around its NavDisplay so screens can share elements across destinations. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * The video thumbnail in a list and the player on the watch screen are the same shared element,
 * so opening a video grows the thumbnail into the player (and back on predictive back).
 *
 * The same video can appear in several lists at once, so the key names the exact thumbnail
 * that was tapped (see [videoFrameKey]) and travels to the watch screen with the navigation key.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedVideoFrame(key: String): Modifier {
    val scope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current
    return with(scope) {
        this@sharedVideoFrame.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedScope,
        )
    }
}

/** Shared-element key for the thumbnail of [videoId] inside the list identified by [origin]. */
fun videoFrameKey(
    origin: String,
    videoId: VideoId,
): String = "video-frame:$origin:${videoId.value}"
