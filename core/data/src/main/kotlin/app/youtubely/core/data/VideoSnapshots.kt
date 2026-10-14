// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.database.VideoSnapshot
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.Image
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.StreamType
import app.youtubely.core.model.VideoId
import app.youtubely.core.model.bestFor
import java.time.Duration
import java.time.Instant

private const val THUMBNAIL_WIDTH = 480

internal fun StreamItem.toSnapshot() =
    VideoSnapshot(
        videoId = id.value,
        title = title,
        channelId = channel?.id?.value,
        channelName = channel?.name,
        channelAvatarUrl = channel?.avatars?.bestFor(THUMBNAIL_WIDTH)?.url,
        thumbnailUrl = thumbnails.bestFor(THUMBNAIL_WIDTH)?.url,
        durationSeconds = duration?.seconds,
        streamType = type.name,
        uploadedAt = uploadedAt?.toEpochMilli(),
    )

internal fun VideoSnapshot.toItem(): StreamItem {
    val channelId = channelId
    val channelName = channelName
    return StreamItem(
        id = VideoId(videoId),
        title = title,
        channel =
            if (channelId != null && channelName != null) {
                ChannelRef(
                    id = ChannelId(channelId),
                    name = channelName,
                    avatars = listOfNotNull(channelAvatarUrl?.let { Image(it, null, null) }),
                    isVerified = false,
                )
            } else {
                null
            },
        thumbnails = listOfNotNull(thumbnailUrl?.let { Image(it, null, null) }),
        type = StreamType.entries.firstOrNull { it.name == streamType } ?: StreamType.Video,
        duration = durationSeconds?.let(Duration::ofSeconds),
        viewCount = null,
        uploadedAt = uploadedAt?.let(Instant::ofEpochMilli),
        uploadedAtText = null,
    )
}
