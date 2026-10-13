// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase

/** Everything the app keeps locally about the user: history and subscriptions. Never leaves the device. */
@Database(
    entities = [HistoryEntity::class, SubscriptionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class YouTubelyDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    abstract fun subscriptionDao(): SubscriptionDao
}
