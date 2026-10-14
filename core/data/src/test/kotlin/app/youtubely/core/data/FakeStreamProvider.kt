// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelPage
import app.youtubely.core.model.ChannelTab
import app.youtubely.core.model.Comment
import app.youtubely.core.model.CommentSort
import app.youtubely.core.model.ExploreSection
import app.youtubely.core.model.Page
import app.youtubely.core.model.Paged
import app.youtubely.core.model.PlaylistId
import app.youtubely.core.model.PlaylistPage
import app.youtubely.core.model.Region
import app.youtubely.core.model.SearchFilter
import app.youtubely.core.model.SearchItem
import app.youtubely.core.model.StreamInfo
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.VideoId
import app.youtubely.core.provider.ProviderError
import app.youtubely.core.provider.StreamProvider

internal class FakeStreamProvider(
    private val feeds: Map<ChannelId, List<StreamItem>>,
) : StreamProvider {
    override suspend fun channelFeed(id: ChannelId): List<StreamItem> =
        feeds[id] ?: throw ProviderError.Network(java.io.IOException("offline"))

    override suspend fun search(
        query: String,
        filter: SearchFilter,
        page: Page?,
    ): Paged<SearchItem> = error("unused")

    override suspend fun suggestions(query: String): List<String> = error("unused")

    override suspend fun streamInfo(videoId: VideoId): StreamInfo = error("unused")

    override suspend fun channel(
        id: ChannelId,
        tab: ChannelTab,
        page: Page?,
    ): ChannelPage = error("unused")

    override suspend fun playlist(
        id: PlaylistId,
        page: Page?,
    ): PlaylistPage = error("unused")

    override suspend fun comments(
        videoId: VideoId,
        sort: CommentSort,
        page: Page?,
    ): Paged<Comment> = error("unused")

    override suspend fun trending(region: Region): List<StreamItem> = error("unused")

    override suspend fun explore(region: Region): List<ExploreSection> = error("unused")
}
