// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.database.AutoDownloadDao
import app.youtubely.core.database.AutoDownloadEntity
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.VideoId
import javax.inject.Inject
import javax.inject.Singleton

/** A download that was started by a channel's automatic download. */
data class AutoDownload(
    val videoId: VideoId,
    val downloadId: String,
)

/** Which downloads came from automatic downloads, so a channel keeps only its latest few. */
@Singleton
class AutoDownloadLog
    @Inject
    constructor(
        private val dao: AutoDownloadDao,
    ) {
        suspend fun record(
            videoId: VideoId,
            channelId: ChannelId,
            downloadId: String,
        ) = dao.insert(AutoDownloadEntity(videoId.value, channelId.value, downloadId, System.currentTimeMillis()))

        /** Newest first. */
        suspend fun forChannel(channelId: ChannelId): List<AutoDownload> =
            dao.forChannel(channelId.value).map { AutoDownload(VideoId(it.videoId), it.downloadId) }

        suspend fun forget(videoId: VideoId) = dao.delete(videoId.value)
    }
