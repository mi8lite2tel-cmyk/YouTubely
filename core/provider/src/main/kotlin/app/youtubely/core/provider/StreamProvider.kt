// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.provider

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

/**
 * The only way the app reaches a video source. Implementations (NewPipeExtractor by default,
 * Piped later) live in this module's sub-packages and can be swapped without touching features.
 *
 * Every function is main-safe: implementations switch to an IO dispatcher themselves.
 * Expected failures are thrown as [ProviderError]; cancellation propagates as usual.
 */
interface StreamProvider {
    suspend fun search(
        query: String,
        filter: SearchFilter,
        page: Page?,
    ): Paged<SearchItem>

    suspend fun suggestions(query: String): List<String>

    /** Formats, subtitles, chapters, related videos and loudness for one video. */
    suspend fun streamInfo(videoId: VideoId): StreamInfo

    suspend fun channel(
        id: ChannelId,
        tab: ChannelTab,
        page: Page?,
    ): ChannelPage

    suspend fun playlist(
        id: PlaylistId,
        page: Page?,
    ): PlaylistPage

    suspend fun comments(
        videoId: VideoId,
        sort: CommentSort,
        page: Page?,
    ): Paged<Comment>

    suspend fun trending(region: Region): List<StreamItem>

    /**
     * Public charts for the home screen, fetched live for [region]. Sections the source cannot
     * deliver right now are left out; fails only when none could be loaded.
     */
    suspend fun explore(region: Region): List<ExploreSection>

    /** Latest uploads of a channel through its public RSS feed — the cheap path for subscriptions. */
    suspend fun channelFeed(id: ChannelId): List<StreamItem>
}
