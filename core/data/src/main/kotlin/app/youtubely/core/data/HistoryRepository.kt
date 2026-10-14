// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.database.HistoryDao
import app.youtubely.core.database.HistoryEntity
import app.youtubely.core.datastore.PlaybackSettingsRepository
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.HistoryEntry
import app.youtubely.core.model.Image
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.StreamType
import app.youtubely.core.model.VideoId
import app.youtubely.core.model.bestFor
import app.youtubely.core.model.unfinished
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Local watch history. Records nothing while history is turned off in the settings. */
@Singleton
class HistoryRepository
    @Inject
    constructor(
        private val dao: HistoryDao,
        private val settings: PlaybackSettingsRepository,
    ) {
        val history: Flow<List<HistoryEntry>> = dao.observeAll().map { rows -> rows.map { it.toEntry() } }

        /** Started but unfinished videos of the last weeks, for "Continue watching". */
        val continueWatching: Flow<List<HistoryEntry>> = history.map { it.unfinished(Instant.now()) }

        /** Watched share 0..1 per video, for progress bars on thumbnails. */
        val progress: Flow<Map<VideoId, Float>> =
            dao.observeProgress().map { rows ->
                rows
                    .mapNotNull { row ->
                        val length = row.durationSeconds?.takeIf { it > 0 }?.times(MILLIS) ?: return@mapNotNull null
                        VideoId(row.videoId) to (row.positionMs.toFloat() / length).coerceIn(0f, 1f)
                    }.toMap()
            }

        /** Puts [item] on top of the history, keeping a known position unless [positionMs] is given. */
        suspend fun record(
            item: StreamItem,
            positionMs: Long? = null,
        ) {
            if (!settings.settings.first().historyEnabled) return
            val previous = dao.get(item.id.value)
            val entry = item.toEntity(positionMs ?: previous?.positionMs ?: 0, Instant.now())
            // An offline copy knows less than the online page did; keep what is already stored.
            dao.upsert(
                entry.copy(
                    channelId = entry.channelId ?: previous?.channelId,
                    channelName = entry.channelName ?: previous?.channelName,
                    channelAvatarUrl = entry.channelAvatarUrl ?: previous?.channelAvatarUrl,
                    thumbnailUrl =
                        previous?.thumbnailUrl?.takeIf { entry.thumbnailUrl?.startsWith("file:") != false }
                            ?: entry.thumbnailUrl,
                    durationSeconds = entry.durationSeconds ?: previous?.durationSeconds,
                ),
            )
        }

        suspend fun savePosition(
            id: VideoId,
            positionMs: Long,
        ) {
            if (!settings.settings.first().historyEnabled) return
            dao.updatePosition(id.value, positionMs)
        }

        /**
         * Where to continue [id] from: the saved position, or 0 when resuming is off, the video was
         * never opened, or it was watched almost to the end.
         */
        suspend fun resumePosition(id: VideoId): Long {
            val row = if (settings.settings.first().resumePlayback) dao.get(id.value) else null
            val length = row?.durationSeconds?.times(MILLIS)
            return row?.positionMs?.takeIf { length != null && it in MIN_RESUME_MS until length - END_MARGIN_MS } ?: 0
        }

        suspend fun remove(id: VideoId) = dao.delete(id.value)

        suspend fun clear() = dao.clear()

        private companion object {
            const val MILLIS = 1_000L
            const val MIN_RESUME_MS = 5_000L
            const val END_MARGIN_MS = 15_000L
            const val THUMBNAIL_WIDTH = 480
        }

        private fun StreamItem.toEntity(
            positionMs: Long,
            at: Instant,
        ) = HistoryEntity(
            videoId = id.value,
            title = title,
            channelId = channel?.id?.value,
            channelName = channel?.name,
            channelAvatarUrl = channel?.avatars?.bestFor(THUMBNAIL_WIDTH)?.url,
            thumbnailUrl = thumbnails.bestFor(THUMBNAIL_WIDTH)?.url,
            durationSeconds = duration?.seconds,
            streamType = type.name,
            positionMs = positionMs,
            watchedAt = at.toEpochMilli(),
        )

        private fun HistoryEntity.toEntry(): HistoryEntry {
            val channelId = channelId
            val channelName = channelName
            return HistoryEntry(
                item =
                    StreamItem(
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
                        uploadedAt = null,
                        uploadedAtText = null,
                    ),
                positionMs = positionMs,
                watchedAt = Instant.ofEpochMilli(watchedAt),
            )
        }
    }
