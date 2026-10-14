// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.database.PlaylistDao
import app.youtubely.core.database.PlaylistEntity
import app.youtubely.core.database.PlaylistItemEntity
import app.youtubely.core.model.LocalPlaylist
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.VideoId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Playlists made on this device. */
@Singleton
class PlaylistRepository
    @Inject
    constructor(
        private val dao: PlaylistDao,
    ) {
        val playlists: Flow<List<LocalPlaylist>> =
            dao.observeSummaries().map { rows ->
                rows.map {
                    LocalPlaylist(
                        it.id,
                        it.name,
                        it.itemCount,
                        it.thumbnailUrl,
                        Instant.ofEpochMilli(it.updatedAt),
                    )
                }
            }

        /** `null` once the playlist is deleted. */
        fun playlist(id: Long): Flow<String?> = dao.observe(id).map { it?.name }

        fun items(id: Long): Flow<List<StreamItem>> =
            dao.observeItems(id).map { rows -> rows.map { it.video.toItem() } }

        suspend fun itemsOnce(id: Long): List<StreamItem> = dao.items(id).map { it.video.toItem() }

        /** Ids of the playlists that contain [videoId], to tick them in the "save to" dialog. */
        fun containing(videoId: VideoId): Flow<Set<Long>> = dao.observeContaining(videoId.value).map { it.toSet() }

        suspend fun create(name: String): Long {
            val now = System.currentTimeMillis()
            return dao.insert(PlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now))
        }

        suspend fun rename(
            id: Long,
            name: String,
        ) = dao.rename(id, name.trim(), System.currentTimeMillis())

        suspend fun delete(id: Long) = dao.delete(id)

        suspend fun add(
            id: Long,
            items: List<StreamItem>,
        ) {
            val now = System.currentTimeMillis()
            dao.addItems(
                id,
                items.map { PlaylistItemEntity(playlistId = id, video = it.toSnapshot(), addedAt = now) },
                now,
            )
        }

        suspend fun remove(
            id: Long,
            videoId: VideoId,
        ) = dao.removeItem(id, videoId.value)
    }
