// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** One row per video: watching it again moves it to the top and updates the position. */
@Entity(tableName = "history", indices = [Index("watchedAt")])
data class HistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelId: String?,
    val channelName: String?,
    val channelAvatarUrl: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    /** Name of the StreamType enum constant. */
    val streamType: String,
    val positionMs: Long,
    val watchedAt: Long,
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val channelId: String,
    val name: String,
    val avatarUrl: String?,
    val isVerified: Boolean,
    val subscribedAt: Long,
    /** Notify about new videos of this channel. */
    @ColumnInfo(defaultValue = "0") val notify: Boolean = false,
    /** Save new videos of this channel for offline viewing. */
    @ColumnInfo(defaultValue = "0") val autoDownload: Boolean = false,
    /** User-chosen label to filter the feed by, `null` = no group. */
    val groupName: String? = null,
    /** Last successful feed load; `null` until the first one, whose videos are not "new". */
    val lastRefreshedAt: Long? = null,
)

/** Watched share of a video, for the progress bar on thumbnails. */
data class WatchProgressRow(
    val videoId: String,
    val positionMs: Long,
    val durationSeconds: Long?,
)

/** A video as shown in a list, kept with everything needed to draw it offline. */
data class VideoSnapshot(
    val videoId: String,
    val title: String,
    val channelId: String?,
    val channelName: String?,
    val channelAvatarUrl: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    /** Name of the StreamType enum constant. */
    val streamType: String,
    /** Upload time in epoch milliseconds when known. */
    val uploadedAt: Long?,
)

/** A video of a subscribed channel found by a feed refresh. */
@Entity(
    tableName = "feed_items",
    primaryKeys = ["videoId"],
    indices = [Index("channelId"), Index("uploadedAt")],
)
data class FeedItemEntity(
    @Embedded val video: VideoSnapshot,
    val firstSeenAt: Long,
    /** Found after the first load of its channel and not announced by a notification yet. */
    @ColumnInfo(defaultValue = "0") val pending: Boolean = false,
    /** Same, for the automatic download of the channel. */
    @ColumnInfo(defaultValue = "0") val downloadPending: Boolean = false,
)

@Entity(tableName = "watch_later", primaryKeys = ["videoId"], indices = [Index("addedAt")])
data class WatchLaterEntity(
    @Embedded val video: VideoSnapshot,
    val addedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["playlistId", "videoId"], unique = true)],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    @Embedded val video: VideoSnapshot,
    val addedAt: Long,
)

/** A playlist with its size and the thumbnail of its first video, for the list of playlists. */
data class PlaylistSummaryRow(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val itemCount: Int,
    val thumbnailUrl: String?,
)

/** A video saved by the automatic download of a channel, so it can be cleaned up later. */
@Entity(tableName = "auto_downloads", indices = [Index("channelId")])
data class AutoDownloadEntity(
    @PrimaryKey val videoId: String,
    val channelId: String,
    val downloadId: String,
    val createdAt: Long,
)
