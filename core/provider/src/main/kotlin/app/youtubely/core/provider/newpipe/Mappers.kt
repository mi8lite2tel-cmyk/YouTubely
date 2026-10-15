// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.provider.newpipe

import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.ChannelRef
import app.youtubely.core.model.Comment
import app.youtubely.core.model.Image
import app.youtubely.core.model.Playlist
import app.youtubely.core.model.SearchItem
import app.youtubely.core.model.StreamItem
import app.youtubely.core.model.StreamType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.time.Duration
import org.schabi.newpipe.extractor.Image as NpImage
import org.schabi.newpipe.extractor.stream.StreamInfo as NpStreamInfo
import org.schabi.newpipe.extractor.stream.StreamType as NpStreamType

/** NewPipe reports unknown numbers as -1. */
private fun Long.knownOrNull(): Long? = takeIf { it >= 0 }

private fun Int.knownOrNull(): Int? = takeIf { it > 0 }

internal fun List<NpImage>.toImages(): List<Image> =
    map {
        Image(url = it.url, width = it.width.knownOrNull(), height = it.height.knownOrNull())
    }

internal fun channelRef(
    url: String?,
    name: String?,
    avatars: List<NpImage>,
    verified: Boolean,
): ChannelRef? {
    if (url.isNullOrBlank() || name.isNullOrBlank()) return null
    return runCatching {
        ChannelRef(channelIdFromUrl(url), name, avatars.toImages(), verified)
    }.getOrNull()
}

private fun NpStreamType.toStreamType(isShort: Boolean): StreamType =
    when {
        this == NpStreamType.LIVE_STREAM || this == NpStreamType.AUDIO_LIVE_STREAM -> StreamType.Live
        this == NpStreamType.POST_LIVE_STREAM || this == NpStreamType.POST_LIVE_AUDIO_STREAM -> StreamType.Video
        isShort -> StreamType.Short
        else -> StreamType.Video
    }

internal fun StreamInfoItem.toStreamItem(): StreamItem? =
    runCatching {
        StreamItem(
            id = videoIdFromUrl(url),
            title = name,
            channel = channelRef(uploaderUrl, uploaderName, uploaderAvatars, isUploaderVerified),
            thumbnails = thumbnails.toImages(),
            type = streamType.toStreamType(isShortFormContent),
            duration = duration.takeIf { it > 0 }?.let(Duration::ofSeconds),
            viewCount = viewCount.knownOrNull(),
            uploadedAt = uploadDate?.takeUnless { it.isApproximation }?.instant,
            uploadedAtText = textualUploadDate,
        )
    }.getOrNull()

internal fun NpStreamInfo.toStreamItem(): StreamItem? =
    runCatching {
        StreamItem(
            id = videoIdFromUrl(url),
            title = name,
            channel = channelRef(uploaderUrl, uploaderName, uploaderAvatars, isUploaderVerified),
            thumbnails = thumbnails.toImages(),
            type = streamType.toStreamType(isShortFormContent),
            duration = duration.takeIf { it > 0 }?.let(Duration::ofSeconds),
            viewCount = viewCount.knownOrNull(),
            uploadedAt = uploadDate?.takeUnless { it.isApproximation }?.instant,
            uploadedAtText = textualUploadDate,
        )
    }.getOrNull()

internal fun InfoItem.toSearchItem(): SearchItem? =
    when (this) {
        is StreamInfoItem -> {
            toStreamItem()?.let(SearchItem::Stream)
        }

        is ChannelInfoItem -> {
            channelRef(url, name, thumbnails, isVerified)?.let {
                SearchItem.ChannelResult(
                    channel = it,
                    subscriberCount = subscriberCount.knownOrNull(),
                    videoCount = streamCount.knownOrNull(),
                    description = description?.takeIf(String::isNotBlank),
                )
            }
        }

        is PlaylistInfoItem -> {
            toPlaylist()?.let(SearchItem::PlaylistResult)
        }

        else -> {
            null
        }
    }

internal fun PlaylistInfoItem.toPlaylist(): Playlist? =
    runCatching {
        Playlist(
            id = playlistIdFromUrl(url),
            name = name,
            uploader = channelRef(uploaderUrl, uploaderName, emptyList(), isUploaderVerified),
            thumbnails = thumbnails.toImages(),
            streamCount = streamCount.knownOrNull(),
        )
    }.getOrNull()

/** YouTube descriptions come as HTML; the app shows plain text with line breaks kept. */
internal fun Description.toPlainText(): String {
    val raw = content.orEmpty()
    if (type != Description.HTML) return raw
    val document = Jsoup.parse(raw.replace(Regex("(?i)<br\\s*/?>"), "\n"))
    document.outputSettings(Document.OutputSettings().prettyPrint(false))
    return document.wholeText().trim()
}

internal fun CommentsInfoItem.toComment(): Comment? {
    val author = channelRef(uploaderUrl, uploaderName, uploaderAvatars, isUploaderVerified) ?: return null
    return Comment(
        id = commentId,
        author = author,
        text = commentText.toPlainText(),
        publishedAtText = textualUploadDate,
        likeCount = likeCount.toLong().knownOrNull(),
        isPinned = isPinned,
        isHeartedByUploader = isHeartedByUploader,
        replyCount = replyCount.coerceAtLeast(0),
        replies = PageCodec.encode(replies),
    )
}
