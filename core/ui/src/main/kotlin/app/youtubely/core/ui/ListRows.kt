// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.Playlist
import app.youtubely.core.model.bestFor
import coil3.compose.AsyncImage

private const val THUMBNAIL_ASPECT = 16f / 9f

/** A channel in search results and in the subscription list. */
@Composable
fun ChannelListItem(
    channel: ChannelRef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    ListItem(
        onClick = onClick,
        supportingContent = supporting?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { ChannelAvatar(channel.avatars, contentDescription = null, size = 56.dp) },
        trailingContent = trailing,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                channel.name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (channel.isVerified) VerifiedBadge()
        }
    }
}

/** A playlist with its thumbnail, owner and length. */
@Composable
fun PlaylistListItem(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = playlist.streamCount
    ListItem(
        onClick = onClick,
        supportingContent = {
            Text(
                listOfNotNull(
                    playlist.uploader?.name,
                    count?.let { pluralStringResource(R.plurals.playlist_videos, it.toInt(), it) },
                ).joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            AsyncImage(
                model = playlist.thumbnails.bestFor(THUMBNAIL_MIN_WIDTH)?.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .width(120.dp)
                        .aspectRatio(THUMBNAIL_ASPECT)
                        .clip(MaterialTheme.shapes.medium),
            )
        },
        modifier = modifier,
    ) {
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleSmallEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val THUMBNAIL_MIN_WIDTH = 336
