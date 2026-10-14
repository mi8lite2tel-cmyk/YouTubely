// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.database

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): YouTubelyDatabase =
        Room
            .databaseBuilder(context, YouTubelyDatabase::class.java, "youtubely.db")
            // The platform SQLite keeps the APK small; Room 3 needs an explicit driver.
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    @Provides
    fun provideHistoryDao(database: YouTubelyDatabase): HistoryDao = database.historyDao()

    @Provides
    fun provideSubscriptionDao(database: YouTubelyDatabase): SubscriptionDao = database.subscriptionDao()

    @Provides
    fun provideFeedDao(database: YouTubelyDatabase): FeedDao = database.feedDao()

    @Provides
    fun provideWatchLaterDao(database: YouTubelyDatabase): WatchLaterDao = database.watchLaterDao()

    @Provides
    fun providePlaylistDao(database: YouTubelyDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideAutoDownloadDao(database: YouTubelyDatabase): AutoDownloadDao = database.autoDownloadDao()

    @Provides
    fun provideChannelPlaybackDao(database: YouTubelyDatabase): ChannelPlaybackDao = database.channelPlaybackDao()

    @Provides
    fun provideTrafficDao(database: YouTubelyDatabase): TrafficDao = database.trafficDao()
}
