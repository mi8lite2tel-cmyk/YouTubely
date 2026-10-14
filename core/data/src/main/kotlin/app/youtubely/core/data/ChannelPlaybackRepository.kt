// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.database.ChannelPlaybackDao
import app.youtubely.core.database.ChannelPlaybackEntity
import app.youtubely.core.model.ChannelId
import javax.inject.Inject
import javax.inject.Singleton

/** Quality and speed the user last chose for the videos of one channel; `null` = automatic / normal. */
data class ChannelPlayback(
    val height: Int?,
    val speed: Float?,
)

/** Remembers per channel what suits it: a lecture channel at 1.5×, a music channel in 1080p. */
@Singleton
class ChannelPlaybackRepository
    @Inject
    constructor(
        private val dao: ChannelPlaybackDao,
    ) {
        suspend fun get(channel: ChannelId): ChannelPlayback? =
            dao.get(channel.value)?.let { ChannelPlayback(it.height, it.speed) }

        suspend fun setHeight(
            channel: ChannelId,
            height: Int?,
        ) = update(channel) { it.copy(height = height) }

        /** A speed of 1× is stored as "nothing remembered". */
        suspend fun setSpeed(
            channel: ChannelId,
            speed: Float?,
        ) = update(channel) { it.copy(speed = speed?.takeIf { value -> value != NORMAL_SPEED }) }

        suspend fun forget(channel: ChannelId) = dao.delete(channel.value)

        suspend fun clear() = dao.clear()

        private suspend fun update(
            channel: ChannelId,
            change: (ChannelPlaybackEntity) -> ChannelPlaybackEntity,
        ) {
            val current = dao.get(channel.value) ?: ChannelPlaybackEntity(channel.value, null, null, 0)
            val next = change(current).copy(updatedAt = System.currentTimeMillis())
            if (next.height == null && next.speed == null) dao.delete(channel.value) else dao.upsert(next)
        }

        private companion object {
            const val NORMAL_SPEED = 1f
        }
    }
