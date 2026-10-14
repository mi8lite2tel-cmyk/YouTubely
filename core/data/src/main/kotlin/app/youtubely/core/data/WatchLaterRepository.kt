// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.database.WatchLaterDao
import app.youtubely.core.database.WatchLaterEntity
import app.youtubely.core.model.SavedVideo
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.VideoId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Videos put aside to watch later, newest first. */
@Singleton
class WatchLaterRepository
    @Inject
    constructor(
        private val dao: WatchLaterDao,
    ) {
        val videos: Flow<List<SavedVideo>> =
            dao.observeAll().map { rows ->
                rows.map { SavedVideo(it.video.toItem(), Instant.ofEpochMilli(it.addedAt)) }
            }

        /** Ids in the list, to show which videos are saved already. */
        val ids: Flow<Set<VideoId>> = dao.observeIds().map { ids -> ids.mapTo(HashSet(), ::VideoId) }

        suspend fun add(
            item: StreamItem,
            at: Instant = Instant.now(),
        ) = dao.upsert(WatchLaterEntity(item.toSnapshot(), at.toEpochMilli()))

        suspend fun remove(id: VideoId) = dao.delete(id.value)

        suspend fun clear() = dao.clear()
    }
