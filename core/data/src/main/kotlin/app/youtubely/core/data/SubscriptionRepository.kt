// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.database.AutoDownloadRuleColumns
import app.youtubely.core.database.FeedDao
import app.youtubely.core.database.SubscriptionDao
import app.youtubely.core.database.SubscriptionEntity
import app.youtubely.core.model.AutoDownloadRule
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.Image
import app.youtubely.core.model.Subscription
import app.youtubely.core.model.bestFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Channels followed on this device. No account is involved; YouTube never learns about them. */
@Singleton
class SubscriptionRepository
    @Inject
    constructor(
        private val dao: SubscriptionDao,
        private val feed: FeedDao,
    ) {
        /** Followed channels, by name. */
        val subscriptions: Flow<List<ChannelRef>> = dao.observeAll().map { rows -> rows.map { it.toChannel() } }

        /** The same channels with their notification, download and group settings. */
        val details: Flow<List<Subscription>> = dao.observeAll().map { rows -> rows.map { it.toSubscription() } }

        /** Groups in use, sorted. */
        val groups: Flow<List<String>> =
            dao.observeAll().map { rows -> rows.mapNotNull { it.groupName }.distinct().sorted() }

        fun isSubscribed(id: ChannelId): Flow<Boolean> = dao.observeIsSubscribed(id.value)

        suspend fun get(id: ChannelId): Subscription? = dao.get(id.value)?.toSubscription()

        /** Follows [channel]; one that is already followed keeps its settings and only gets a fresh name and avatar. */
        suspend fun subscribe(channel: ChannelRef) {
            val avatar = channel.avatars.bestFor(AVATAR_WIDTH)?.url
            val added =
                dao.insertIfAbsent(
                    SubscriptionEntity(
                        channelId = channel.id.value,
                        name = channel.name,
                        avatarUrl = avatar,
                        isVerified = channel.isVerified,
                        subscribedAt = System.currentTimeMillis(),
                    ),
                )
            if (added == -1L) dao.updateProfile(channel.id.value, channel.name, avatar, channel.isVerified)
        }

        suspend fun unsubscribe(id: ChannelId) {
            dao.delete(id.value)
            feed.deleteChannel(id.value)
        }

        suspend fun setNotify(
            id: ChannelId,
            enabled: Boolean,
        ) = dao.setNotify(id.value, enabled)

        suspend fun setAutoDownload(
            id: ChannelId,
            enabled: Boolean,
        ) = dao.setAutoDownload(id.value, enabled)

        suspend fun setAutoDownloadRule(
            id: ChannelId,
            rule: AutoDownloadRule,
        ) = dao.setAutoDownloadRule(
            AutoDownloadRuleColumns(
                channelId = id.value,
                adQuality = if (rule.audioOnly) 0 else rule.maxHeight,
                adKeepLast = rule.keepLast,
                adSkipShorts = rule.skipShorts,
                adSkipLive = rule.skipLive,
                adMinMinutes = rule.minMinutes,
                adMaxMinutes = rule.maxMinutes,
                adIncludeWords = AutoDownloadRule.joinWords(rule.includeWords),
                adExcludeWords = AutoDownloadRule.joinWords(rule.excludeWords),
                adDeleteWatched = rule.deleteWatched,
            ),
        )

        /** `null` or a blank [group] removes the channel from its group. */
        suspend fun setGroup(
            id: ChannelId,
            group: String?,
        ) = dao.setGroup(id.value, group?.trim()?.takeIf { it.isNotEmpty() })

        private companion object {
            const val AVATAR_WIDTH = 176
        }
    }

internal fun SubscriptionEntity.toChannel() =
    ChannelRef(
        id = ChannelId(channelId),
        name = name,
        avatars = listOfNotNull(avatarUrl?.let { Image(it, null, null) }),
        isVerified = isVerified,
    )

internal fun SubscriptionEntity.toSubscription() =
    Subscription(
        channel = toChannel(),
        notify = notify,
        autoDownload = autoDownload,
        group = groupName,
        subscribedAt = Instant.ofEpochMilli(subscribedAt),
        autoRule =
            AutoDownloadRule(
                maxHeight = adQuality?.takeIf { it > 0 },
                audioOnly = adQuality == 0,
                keepLast = adKeepLast,
                skipShorts = adSkipShorts,
                skipLive = adSkipLive,
                minMinutes = adMinMinutes,
                maxMinutes = adMaxMinutes,
                includeWords = AutoDownloadRule.parseWords(adIncludeWords),
                excludeWords = AutoDownloadRule.parseWords(adExcludeWords),
                deleteWatched = adDeleteWatched,
            ),
    )
