// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.StreamItem

/**
 * Opens the actions of a video (watch later, playlists, share). Provided once at the app root;
 * every video card then offers them on long press and through a "more" button.
 */
val LocalVideoActions = staticCompositionLocalOf<((StreamItem) -> Unit)?> { null }

/** The "more" button of a video, or nothing when the app provides no actions. */
@Composable
fun VideoMoreButton(
    item: StreamItem,
    modifier: Modifier = Modifier,
) {
    val open = LocalVideoActions.current ?: return
    IconButton(onClick = { open(item) }, modifier = modifier.size(MORE_BUTTON_SIZE)) {
        Icon(painterResource(YouTubelyIcons.MoreVert), contentDescription = stringResource(R.string.video_more))
    }
}

private val MORE_BUTTON_SIZE = 48.dp
