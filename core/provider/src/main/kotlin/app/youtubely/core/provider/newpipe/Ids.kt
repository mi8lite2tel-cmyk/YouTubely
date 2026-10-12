// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.provider.newpipe

import app.youtubely.core.model.ChannelId
import app.youtubely.core.model.PlaylistId
import app.youtubely.core.model.VideoId
import org.schabi.newpipe.extractor.ServiceList

private val youtube get() = ServiceList.YouTube

internal fun videoIdFromUrl(url: String) = VideoId(youtube.streamLHFactory.getId(url))

internal fun channelIdFromUrl(url: String) = ChannelId(youtube.channelLHFactory.getId(url).removePrefix("channel/"))

internal fun playlistIdFromUrl(url: String) = PlaylistId(youtube.playlistLHFactory.getId(url))

internal fun VideoId.toUrl(): String = youtube.streamLHFactory.getUrl(value)

internal fun ChannelId.toUrl(): String = youtube.channelLHFactory.getUrl("channel/$value")

internal fun PlaylistId.toUrl(): String = youtube.playlistLHFactory.getUrl(value)
