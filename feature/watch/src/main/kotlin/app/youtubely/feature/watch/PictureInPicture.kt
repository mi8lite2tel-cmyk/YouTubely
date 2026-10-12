// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.watch

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer

private val VideoAspect = Rational(16, 9)

/** `true` while the activity is shown as a picture-in-picture window. */
@Composable
internal fun rememberIsInPictureInPicture(): Boolean {
    val activity = LocalActivity.current as? ComponentActivity ?: return false
    var inPip by remember { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

/**
 * While [enabled] (a video is playing and the setting is on), leaving the app shrinks it into a
 * picture-in-picture window: automatically on Android 12+, via the user-leave hint before that.
 * Apply it to the player: it keeps the window's source rectangle up to date, so the shrink
 * animation starts from the video itself.
 */
@Composable
internal fun Modifier.pictureInPicture(enabled: Boolean): Modifier {
    val activity = LocalActivity.current as? ComponentActivity
    val supported =
        remember(activity) {
            activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
        }
    return if (activity != null && supported) pictureInPicture(activity, enabled) else this
}

@Composable
private fun Modifier.pictureInPicture(
    activity: ComponentActivity,
    enabled: Boolean,
): Modifier {
    val currentEnabled by rememberUpdatedState(enabled)
    var sourceRect by remember { mutableStateOf<Rect?>(null) }

    fun params(): PictureInPictureParams =
        PictureInPictureParams
            .Builder()
            .setAspectRatio(VideoAspect)
            .apply { sourceRect?.let(::setSourceRectHint) }
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(currentEnabled) }
            .build()

    DisposableEffect(activity, enabled) {
        activity.setPictureInPictureParams(params())
        val leaveHint =
            Runnable {
                if (currentEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    activity.enterPictureInPictureMode(params())
                }
            }
        activity.addOnUserLeaveHintListener(leaveHint)
        onDispose {
            activity.removeOnUserLeaveHintListener(leaveHint)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
            }
        }
    }
    return onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        val rect = Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        if (rect != sourceRect && !rect.isEmpty) {
            sourceRect = rect
            activity.setPictureInPictureParams(params())
        }
    }
}
