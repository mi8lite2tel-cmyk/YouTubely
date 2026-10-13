// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.database

import androidx.room3.Entity
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
)

/** Watched share of a video, for the progress bar on thumbnails. */
data class WatchProgressRow(
    val videoId: String,
    val positionMs: Long,
    val durationSeconds: Long?,
)
