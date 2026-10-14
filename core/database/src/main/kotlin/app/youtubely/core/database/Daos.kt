// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY watchedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT videoId, positionMs, durationSeconds FROM history")
    fun observeProgress(): Flow<List<WatchProgressRow>>

    @Query("SELECT * FROM history WHERE videoId = :videoId")
    suspend fun get(videoId: String): HistoryEntity?

    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query("UPDATE history SET positionMs = :positionMs WHERE videoId = :videoId")
    suspend fun updatePosition(
        videoId: String,
        positionMs: Long,
    )

    @Query("DELETE FROM history WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE channelId = :channelId")
    suspend fun get(channelId: String): SubscriptionEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE channelId = :channelId)")
    fun observeIsSubscribed(channelId: String): Flow<Boolean>

    /** Keeps the row (and its settings) of a channel that is already followed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(subscription: SubscriptionEntity): Long

    @Query(
        "UPDATE subscriptions SET name = :name, avatarUrl = :avatarUrl, isVerified = :isVerified " +
            "WHERE channelId = :channelId",
    )
    suspend fun updateProfile(
        channelId: String,
        name: String,
        avatarUrl: String?,
        isVerified: Boolean,
    )

    @Query("UPDATE subscriptions SET notify = :enabled WHERE channelId = :channelId")
    suspend fun setNotify(
        channelId: String,
        enabled: Boolean,
    )

    @Query("UPDATE subscriptions SET autoDownload = :enabled WHERE channelId = :channelId")
    suspend fun setAutoDownload(
        channelId: String,
        enabled: Boolean,
    )

    @Query("UPDATE subscriptions SET groupName = :group WHERE channelId = :channelId")
    suspend fun setGroup(
        channelId: String,
        group: String?,
    )

    @Query(
        "UPDATE subscriptions SET adQuality = :quality, adKeepLast = :keepLast, adSkipShorts = :skipShorts, " +
            "adSkipLive = :skipLive, adMinMinutes = :minMinutes, adMaxMinutes = :maxMinutes, " +
            "adIncludeWords = :include, adExcludeWords = :exclude, adDeleteWatched = :deleteWatched " +
            "WHERE channelId = :channelId",
    )
    suspend fun setAutoDownloadRule(
        channelId: String,
        quality: Int?,
        keepLast: Int?,
        skipShorts: Boolean,
        skipLive: Boolean,
        minMinutes: Int,
        maxMinutes: Int,
        include: String?,
        exclude: String?,
        deleteWatched: Boolean,
    )

    @Query("UPDATE subscriptions SET lastRefreshedAt = :at WHERE channelId = :channelId")
    suspend fun markRefreshed(
        channelId: String,
        at: Long,
    )

    @Query("DELETE FROM subscriptions WHERE channelId = :channelId")
    suspend fun delete(channelId: String)
}

@Dao
interface FeedDao {
    @Query("SELECT * FROM feed_items ORDER BY COALESCE(uploadedAt, firstSeenAt) DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<FeedItemEntity>>

    /** Videos found after [since] (epoch millis), for the "new" badge. */
    @Query("SELECT COUNT(*) FROM feed_items WHERE firstSeenAt > :since")
    fun observeCountSince(since: Long): Flow<Int>

    /** Returns the row id per item, or -1 for a video that was already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<FeedItemEntity>): List<Long>

    @Query("SELECT * FROM feed_items WHERE pending = 1 ORDER BY COALESCE(uploadedAt, firstSeenAt)")
    suspend fun pending(): List<FeedItemEntity>

    @Query("UPDATE feed_items SET pending = 0 WHERE videoId IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Query("SELECT * FROM feed_items WHERE downloadPending = 1 ORDER BY COALESCE(uploadedAt, firstSeenAt)")
    suspend fun downloadPending(): List<FeedItemEntity>

    @Query("UPDATE feed_items SET downloadPending = 0 WHERE videoId IN (:ids)")
    suspend fun clearDownloadPending(ids: List<String>)

    @Query("DELETE FROM feed_items WHERE channelId = :channelId")
    suspend fun deleteChannel(channelId: String)

    /** Keeps the [keep] most recent videos. */
    @Query(
        "DELETE FROM feed_items WHERE videoId NOT IN " +
            "(SELECT videoId FROM feed_items ORDER BY COALESCE(uploadedAt, firstSeenAt) DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int)
}

@Dao
interface WatchLaterDao {
    @Query("SELECT * FROM watch_later ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchLaterEntity>>

    @Query("SELECT videoId FROM watch_later")
    fun observeIds(): Flow<List<String>>

    @Upsert
    suspend fun upsert(item: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM watch_later")
    suspend fun clear()
}

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id, p.name, p.updatedAt, " +
            "(SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS itemCount, " +
            "(SELECT i.thumbnailUrl FROM playlist_items i WHERE i.playlistId = p.id ORDER BY i.id LIMIT 1) " +
            "AS thumbnailUrl " +
            "FROM playlists p ORDER BY p.updatedAt DESC",
    )
    fun observeSummaries(): Flow<List<PlaylistSummaryRow>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY id")
    fun observeItems(id: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY id")
    suspend fun items(id: Long): List<PlaylistItemEntity>

    /** Ids of the playlists that already contain [videoId]. */
    @Query("SELECT playlistId FROM playlist_items WHERE videoId = :videoId")
    fun observeContaining(videoId: String): Flow<List<Long>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :at WHERE id = :id")
    suspend fun rename(
        id: Long,
        name: String,
        at: Long,
    )

    @Query("UPDATE playlists SET updatedAt = :at WHERE id = :id")
    suspend fun touch(
        id: Long,
        at: Long,
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<PlaylistItemEntity>): List<Long>

    @Query("DELETE FROM playlist_items WHERE playlistId = :id AND videoId = :videoId")
    suspend fun removeItem(
        id: Long,
        videoId: String,
    )

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Transaction
    suspend fun addItems(
        id: Long,
        items: List<PlaylistItemEntity>,
        at: Long,
    ) {
        insertItems(items.map { it.copy(playlistId = id) })
        touch(id, at)
    }
}

@Dao
interface AutoDownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AutoDownloadEntity)

    @Query("SELECT * FROM auto_downloads WHERE channelId = :channelId ORDER BY createdAt DESC")
    suspend fun forChannel(channelId: String): List<AutoDownloadEntity>

    @Query("DELETE FROM auto_downloads WHERE videoId = :videoId")
    suspend fun delete(videoId: String)
}

@Dao
interface ChannelPlaybackDao {
    @Query("SELECT * FROM channel_playback WHERE channelId = :channelId")
    suspend fun get(channelId: String): ChannelPlaybackEntity?

    @Upsert
    suspend fun upsert(entity: ChannelPlaybackEntity)

    @Query("DELETE FROM channel_playback WHERE channelId = :channelId")
    suspend fun delete(channelId: String)

    @Query("DELETE FROM channel_playback")
    suspend fun clear()
}

/** Sum of traffic for one group, as returned by the aggregate queries of [TrafficDao]. */
data class TrafficSum(
    val metered: Boolean,
    val kind: String,
    val bytes: Long,
)

/** Bytes of one day over one kind of network. */
data class TrafficDay(
    val day: Long,
    val metered: Boolean,
    val bytes: Long,
)

@Dao
interface TrafficDao {
    @Query(
        "INSERT INTO traffic_daily (day, metered, kind, bytes) VALUES (:day, :metered, :kind, :bytes) " +
            "ON CONFLICT(day, metered, kind) DO UPDATE SET bytes = bytes + :bytes",
    )
    suspend fun add(
        day: Long,
        metered: Boolean,
        kind: String,
        bytes: Long,
    )

    @Query("SELECT metered, kind, SUM(bytes) AS bytes FROM traffic_daily WHERE day >= :from GROUP BY metered, kind")
    fun observeSums(from: Long): Flow<List<TrafficSum>>

    @Query("SELECT day, metered, SUM(bytes) AS bytes FROM traffic_daily WHERE day >= :from GROUP BY day, metered")
    fun observeDays(from: Long): Flow<List<TrafficDay>>

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM traffic_daily WHERE metered = 1 AND day >= :from")
    fun observeMeteredSince(from: Long): Flow<Long>

    @Query("DELETE FROM traffic_daily WHERE day < :before")
    suspend fun deleteBefore(before: Long)

    @Query("DELETE FROM traffic_daily")
    suspend fun clear()
}
