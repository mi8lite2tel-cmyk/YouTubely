// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.Image
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.StreamType
import app.youtubely.core.model.VideoId
import kotlinx.coroutines.flow.first
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** What an import added. */
data class ImportSummary(
    val channels: Int,
    val playlists: Int,
    val videos: Int,
)

/** What to put in an export. */
enum class TransferScope { Subscriptions, Playlists, WatchLater, Everything }

/** Builds [TransferBundle]s from the library and merges them back into it. */
@Singleton
class LibraryTransfer
    @Inject
    constructor(
        private val subscriptions: SubscriptionRepository,
        private val watchLater: WatchLaterRepository,
        private val playlists: PlaylistRepository,
    ) {
        suspend fun export(scope: TransferScope): TransferBundle {
            val all = scope == TransferScope.Everything
            return TransferBundle(
                subscriptions =
                    if (all || scope == TransferScope.Subscriptions) {
                        subscriptions.details.first().map {
                            TransferChannel(it.channel.id.value, it.channel.name, it.group, it.notify, it.autoDownload)
                        }
                    } else {
                        emptyList()
                    },
                playlists =
                    if (all || scope == TransferScope.Playlists) {
                        playlists.playlists.first().map { playlist ->
                            TransferPlaylist(playlist.name, playlists.itemsOnce(playlist.id).map { it.toTransfer() })
                        }
                    } else {
                        emptyList()
                    },
                watchLater =
                    if (all || scope == TransferScope.WatchLater) {
                        watchLater.videos.first().map { it.item.toTransfer() }
                    } else {
                        emptyList()
                    },
            )
        }

        /**
         * Adds what [bundle] holds. Channels already followed keep their settings; playlists with
         * the name of an existing one are merged into it.
         */
        suspend fun import(bundle: TransferBundle): ImportSummary {
            var newChannels = 0
            val known =
                subscriptions.subscriptions
                    .first()
                    .map { it.id.value }
                    .toSet()
            bundle.subscriptions.forEach { channel ->
                subscriptions.subscribe(ChannelRef(ChannelId(channel.id), channel.name, emptyList(), false))
                if (channel.id !in known) newChannels++
                // Settings come with the channel only when it is new here.
                if (channel.id !in known) {
                    val id = ChannelId(channel.id)
                    subscriptions.setGroup(id, channel.group)
                    subscriptions.setNotify(id, channel.notify)
                    subscriptions.setAutoDownload(id, channel.autoDownload)
                }
            }
            val existing = playlists.playlists.first().associateBy { it.name }
            var videos = 0
            bundle.playlists.forEach { playlist ->
                val id = existing[playlist.name]?.id ?: playlists.create(playlist.name)
                playlists.add(id, playlist.videos.map { it.toItem() })
                videos += playlist.videos.size
            }
            bundle.watchLater.forEach { watchLater.add(it.toItem()) }
            return ImportSummary(newChannels, bundle.playlists.size, videos + bundle.watchLater.size)
        }

        private fun StreamItem.toTransfer() =
            TransferVideo(id.value, title, channel?.id?.value, channel?.name, duration?.seconds)

        private fun TransferVideo.toItem() =
            StreamItem(
                id = VideoId(id),
                title = title,
                channel = channelId?.let { ChannelRef(ChannelId(it), channelName ?: it, emptyList(), false) },
                thumbnails = listOf(Image("https://i.ytimg.com/vi/$id/mqdefault.jpg", 320, 180)),
                type = StreamType.Video,
                duration = durationSeconds?.let(Duration::ofSeconds),
                viewCount = null,
                uploadedAt = null,
                uploadedAtText = null,
            )
    }
