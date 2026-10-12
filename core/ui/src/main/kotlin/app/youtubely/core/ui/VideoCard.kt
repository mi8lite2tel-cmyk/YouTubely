// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.StreamType
import app.youtubely.core.model.bestFor
import coil3.compose.AsyncImage

private const val THUMBNAIL_ASPECT = 16f / 9f

/** 16:9 thumbnail with a duration or LIVE badge. */
@Composable
fun VideoThumbnail(
    item: StreamItem,
    sharedKey: String,
    modifier: Modifier = Modifier,
    minWidth: Int = 480,
) {
    Box(
        modifier =
            modifier
                .sharedVideoFrame(sharedKey)
                .aspectRatio(THUMBNAIL_ASPECT)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        AsyncImage(
            model = item.thumbnails.bestFor(minWidth)?.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        val badge =
            when {
                item.type == StreamType.Live -> stringResource(R.string.live)
                item.duration != null -> formatDuration(item.duration!!)
                else -> null
            }
        if (badge != null) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color =
                    if (item.type == StreamType.Live) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f)
                    },
                contentColor =
                    if (item.type == StreamType.Live) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.inverseOnSurface
                    },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun videoMetadataText(item: StreamItem): String =
    listOfNotNull(
        item.channel?.name,
        item.viewCount?.let { viewCountText(it) },
        item.uploadedAtText,
    ).joinToString(" · ")

/** Full-width card: thumbnail on top, avatar + title + metadata below. Used in feeds. */
@Composable
fun VideoCard(
    item: StreamItem,
    sharedKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.semantics { role = Role.Button },
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            VideoThumbnail(item, sharedKey, Modifier.fillMaxWidth(), minWidth = 720)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item.channel?.let { ChannelAvatar(it.avatars, contentDescription = null) }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = videoMetadataText(item),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Compact row: thumbnail on the left. Used for search results and related videos. */
@Composable
fun VideoRow(
    item: StreamItem,
    sharedKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailWidth: Dp = 168.dp,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VideoThumbnail(item, sharedKey, Modifier.width(thumbnailWidth), minWidth = 336)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                item.channel?.let { channel ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (channel.isVerified) VerifiedBadge()
                    }
                }
                val meta = listOfNotNull(item.viewCount?.let { viewCountText(it) }, item.uploadedAtText)
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun VerifiedBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.verified)
    Icon(
        painter = painterResource(YouTubelyIcons.Verified),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(14.dp).semantics { contentDescription = description },
    )
}
