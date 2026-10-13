// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.database.SubscriptionDao
import app.youtubely.core.database.SubscriptionEntity
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.Image
import app.youtubely.core.model.bestFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Channels followed on this device. No account is involved; YouTube never learns about them. */
@Singleton
class SubscriptionRepository
    @Inject
    constructor(
        private val dao: SubscriptionDao,
    ) {
        val subscriptions: Flow<List<ChannelRef>> =
            dao.observeAll().map { rows ->
                rows.map {
                    ChannelRef(
                        id = ChannelId(it.channelId),
                        name = it.name,
                        avatars = listOfNotNull(it.avatarUrl?.let { url -> Image(url, null, null) }),
                        isVerified = it.isVerified,
                    )
                }
            }

        fun isSubscribed(id: ChannelId): Flow<Boolean> = dao.observeIsSubscribed(id.value)

        suspend fun subscribe(channel: ChannelRef) {
            dao.upsert(
                SubscriptionEntity(
                    channelId = channel.id.value,
                    name = channel.name,
                    avatarUrl = channel.avatars.bestFor(AVATAR_WIDTH)?.url,
                    isVerified = channel.isVerified,
                    subscribedAt = System.currentTimeMillis(),
                ),
            )
        }

        suspend fun unsubscribe(id: ChannelId) = dao.delete(id.value)

        private companion object {
            const val AVATAR_WIDTH = 176
        }
    }
