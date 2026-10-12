// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.provider.newpipe

import app.youtubely.core.model.Channel
import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelPage
import app.youtubely.core.model.ChannelTab
import app.youtubely.core.model.Comment
import app.youtubely.core.model.CommentSort
import app.youtubely.core.model.ExploreKind
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.kiosk.YoutubeTrendingExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeLiveLinkHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeTrendingGamingVideosLinkHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeTrendingMoviesAndShowsTrailersLinkHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeTrendingMusicLinkHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeTrendingPodcastsEpisodesLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import org.schabi.newpipe.extractor.stream.StreamInfo as NpStreamInfo

/**
 * [StreamProvider] backed by NewPipeExtractor (GPL-3.0). All calls are blocking inside the
 * extractor, so they run on [io].
 */
class NewPipeStreamProvider(
    downloader: Downloader,
    private val io: CoroutineDispatcher,
    locale: Locale = Locale.getDefault(),
) : StreamProvider {
    private val youtube get() = ServiceList.YouTube

    init {
        NewPipe.init(
            downloader,
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifBlank { Locale.US.country }),
        )
    }

    private suspend fun <T> extract(block: () -> T): T =
        withContext(io) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                throw e.toProviderError()
            }
        }

    override suspend fun search(
        query: String,
        filter: SearchFilter,
        page: Page?,
    ): Paged<SearchItem> =
        extract {
            val handler = youtube.searchQHFactory.fromQuery(query, listOf(filter.type.contentFilter()), "")
            val (items, next) =
                if (page == null) {
                    val info = SearchInfo.getInfo(youtube, handler)
                    info.relatedItems to info.nextPage
                } else {
                    val more = SearchInfo.getMoreItems(youtube, handler, PageCodec.decode(page))
                    more.items to more.nextPage
                }
            Paged(
                items = items.mapNotNull { it.toSearchItem() }.filter(filter.duration::matches),
                nextPage = PageCodec.encode(next),
            )
        }

    override suspend fun suggestions(query: String): List<String> =
        extract {
            if (query.isBlank()) emptyList() else youtube.suggestionExtractor.suggestionList(query)
        }

    override suspend fun streamInfo(videoId: VideoId): StreamInfo =
        extract {
            val info = NpStreamInfo.getInfo(youtube, videoId.toUrl())
            val item = info.toStreamItem() ?: throw ProviderError.ExtractorOutdated()

            StreamInfo(
                item = item,
                description = info.description.toPlainText(),
                likeCount = info.likeCount.takeIf { it >= 0 },
                videoStreams = (info.videoStreams + info.videoOnlyStreams).mapNotNull { it.toVideoStream() },
                audioStreams = info.audioStreams.mapNotNull { it.toAudioStream() },
                dashManifestUrl = info.dashMpdUrl.takeIf { it.isNotBlank() },
                hlsManifestUrl = info.hlsUrl.takeIf { it.isNotBlank() },
                subtitles = info.subtitles.mapNotNull { it.toSubtitle() },
                chapters = info.streamSegments.map { it.toChapter() },
                related = info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toStreamItem() },
                loudnessDb = null,
                publishedAt = item.uploadedAt,
            )
        }

    override suspend fun channel(
        id: ChannelId,
        tab: ChannelTab,
        page: Page?,
    ): ChannelPage =
        extract {
            val info = ChannelInfo.getInfo(youtube, id.toUrl())
            val tabs = info.tabs.associateBy { it.contentFilters.firstOrNull() }
            val handler = tabs[tab.tabId()] ?: throw ProviderError.Removed()
            val (items, next) =
                if (page == null) {
                    val tabInfo = ChannelTabInfo.getInfo(youtube, handler)
                    tabInfo.relatedItems to tabInfo.nextPage
                } else {
                    val more = ChannelTabInfo.getMoreItems(youtube, handler, PageCodec.decode(page))
                    more.items to more.nextPage
                }
            ChannelPage(
                channel =
                    Channel(
                        id = id,
                        name = info.name,
                        handle = null,
                        description = info.description.orEmpty(),
                        avatars = info.avatars.toImages(),
                        banners = info.banners.toImages(),
                        subscriberCount = info.subscriberCount.takeIf { it >= 0 },
                        isVerified = info.isVerified,
                        tabs = ChannelTab.entries.filter { it.tabId() in tabs },
                    ),
                tab = tab,
                items = Paged(items.mapNotNull { it.toSearchItem() }, PageCodec.encode(next)),
            )
        }

    override suspend fun playlist(
        id: PlaylistId,
        page: Page?,
    ): PlaylistPage =
        extract {
            val info = PlaylistInfo.getInfo(youtube, id.toUrl())
            val (items, next) =
                if (page == null) {
                    info.relatedItems to info.nextPage
                } else {
                    val more = PlaylistInfo.getMoreItems(youtube, id.toUrl(), PageCodec.decode(page))
                    more.items to more.nextPage
                }
            PlaylistPage(
                playlist =
                    app.youtubely.core.model.Playlist(
                        id = id,
                        name = info.name,
                        uploader = null,
                        thumbnails = info.thumbnails.toImages(),
                        streamCount = info.streamCount.takeIf { it >= 0 },
                    ),
                items = Paged(items.mapNotNull { it.toStreamItem() }, PageCodec.encode(next)),
            )
        }

    /** YouTube's public comment API offers no ordering choice to clients, so [sort] is not applied. */
    override suspend fun comments(
        videoId: VideoId,
        sort: CommentSort,
        page: Page?,
    ): Paged<Comment> =
        extract {
            val url = videoId.toUrl()
            val (items, next) =
                if (page == null) {
                    val info = CommentsInfo.getInfo(youtube, url)
                    info.relatedItems to info.nextPage
                } else {
                    val more = CommentsInfo.getMoreItems(youtube, url, PageCodec.decode(page))
                    more.items to more.nextPage
                }
            Paged(items.mapNotNull { it.toComment() }, PageCodec.encode(next))
        }

    /** YouTube retired its general trending page in 2025; the first working chart stands in for it. */
    override suspend fun trending(region: Region): List<StreamItem> = explore(region).first().items

    override suspend fun explore(region: Region): List<ExploreSection> =
        coroutineScope {
            val sections =
                KIOSKS
                    .map { (kind, kioskId) ->
                        async {
                            runCatching { extract { kiosk(kioskId, region) } }
                                .map { ExploreSection(kind, it) }
                        }
                    }.awaitAll()
            val loaded = sections.mapNotNull { it.getOrNull() }.filter { it.items.isNotEmpty() }
            loaded.ifEmpty {
                throw sections.firstNotNullOfOrNull { it.exceptionOrNull() } ?: ProviderError.ExtractorOutdated()
            }
        }

    private fun kiosk(
        kioskId: String,
        region: Region,
    ): List<StreamItem> {
        val extractor = youtube.kioskList.getExtractorById(kioskId, null)
        extractor.forceContentCountry(ContentCountry(region.code))
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toStreamItem() }
    }

    override suspend fun channelFeed(id: ChannelId): List<StreamItem> =
        extract {
            val extractor = youtube.getFeedExtractor(id.toUrl())
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { it.toStreamItem() }
        }

    private companion object {
        /** Kiosk ids of NewPipeExtractor, in the order they appear on the home screen. */
        val KIOSKS =
            listOf(
                ExploreKind.Trending to YoutubeTrendingExtractor.KIOSK_ID,
                ExploreKind.Music to YoutubeTrendingMusicLinkHandlerFactory.KIOSK_ID,
                ExploreKind.Gaming to YoutubeTrendingGamingVideosLinkHandlerFactory.KIOSK_ID,
                ExploreKind.Live to YoutubeLiveLinkHandlerFactory.KIOSK_ID,
                ExploreKind.Podcasts to YoutubeTrendingPodcastsEpisodesLinkHandlerFactory.KIOSK_ID,
                ExploreKind.MoviesAndShows to YoutubeTrendingMoviesAndShowsTrailersLinkHandlerFactory.KIOSK_ID,
            )
    }

    private fun SearchFilter.Type.contentFilter(): String =
        when (this) {
            SearchFilter.Type.All -> YoutubeSearchQueryHandlerFactory.ALL
            SearchFilter.Type.Videos -> YoutubeSearchQueryHandlerFactory.VIDEOS
            SearchFilter.Type.Channels -> YoutubeSearchQueryHandlerFactory.CHANNELS
            SearchFilter.Type.Playlists -> YoutubeSearchQueryHandlerFactory.PLAYLISTS
        }

    private fun ChannelTab.tabId(): String =
        when (this) {
            ChannelTab.Videos -> ChannelTabs.VIDEOS
            ChannelTab.Shorts -> ChannelTabs.SHORTS
            ChannelTab.Live -> ChannelTabs.LIVESTREAMS
            ChannelTab.Playlists -> ChannelTabs.PLAYLISTS
        }
}
