// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.database

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * Everything the app keeps locally about the user: history, subscriptions with their feed,
 * watch later and playlists. Never leaves the device.
 */
@Database(
    entities = [
        HistoryEntity::class,
        SubscriptionEntity::class,
        FeedItemEntity::class,
        WatchLaterEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        AutoDownloadEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class YouTubelyDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    abstract fun subscriptionDao(): SubscriptionDao

    abstract fun feedDao(): FeedDao

    abstract fun watchLaterDao(): WatchLaterDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun autoDownloadDao(): AutoDownloadDao
}
