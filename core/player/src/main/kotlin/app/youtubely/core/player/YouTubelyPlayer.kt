// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.youtubely.core.model.StreamInfo
import okhttp3.OkHttpClient

/** Creates players and media sources that share the app's OkHttp client. */
@OptIn(UnstableApi::class)
class YouTubelyPlayer(
    private val client: OkHttpClient,
) {
    fun createPlayer(context: Context): ExoPlayer =
        ExoPlayer
            .Builder(context)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            ).setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
            .apply {
                // Prefer the original audio track over dubs.
                trackSelectionParameters =
                    trackSelectionParameters
                        .buildUpon()
                        .setPreferredAudioRoleFlags(C.ROLE_FLAG_MAIN)
                        .build()
            }

    /** Returns `null` when the video has nothing playable (e.g. only formats the app can't handle). */
    fun mediaSource(info: StreamInfo): MediaSource? {
        val source = PlaybackSource.from(info) ?: return null
        val dataSourceFactory = dataSourceFactory(source.headers)
        val mediaItem =
            MediaItem
                .Builder()
                .setMediaId(info.item.id.value)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(info.item.title)
                        .setArtist(info.item.channel?.name)
                        .build(),
                )
        return when (source) {
            is PlaybackSource.GeneratedDash -> {
                val manifest = DashManifestParser().parse(MANIFEST_URI, source.manifest.byteInputStream())
                DashMediaSource.Factory(dataSourceFactory).createMediaSource(manifest, mediaItem.build())
            }

            is PlaybackSource.DashUrl -> {
                DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem.setUri(source.url).build())
            }

            is PlaybackSource.Hls -> {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem.setUri(source.url).build())
            }

            is PlaybackSource.Progressive -> {
                ProgressiveMediaSource
                    .Factory(dataSourceFactory)
                    .createMediaSource(mediaItem.setUri(source.url).build())
            }
        }
    }

    private fun dataSourceFactory(headers: Map<String, Map<String, String>>): DataSource.Factory =
        ResolvingDataSource.Factory(OkHttpDataSource.Factory(client)) { spec ->
            headers[spec.uri.toString()]?.let(spec::withAdditionalHeaders) ?: spec
        }

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L

        /** Placeholder base for the generated manifest; every representation has an absolute BaseURL. */
        val MANIFEST_URI: Uri = Uri.parse("https://youtubely.invalid/manifest.mpd")
    }
}

/** Video heights the player can switch between, highest first. */
fun Player.availableVideoHeights(): List<Int> =
    currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group ->
            (0 until group.length).filter(group::isTrackSupported).map { group.getTrackFormat(it).height }
        }.filter { it > 0 }
        .distinct()
        .sortedDescending()

/** `null` = automatic quality; otherwise pins playback to [height]. */
fun Player.setVideoHeight(height: Int?) {
    trackSelectionParameters =
        trackSelectionParameters
            .buildUpon()
            .apply {
                if (height == null) {
                    clearVideoSizeConstraints()
                    setMinVideoSize(0, 0)
                } else {
                    setMaxVideoSize(Int.MAX_VALUE, height)
                    setMinVideoSize(0, height)
                }
            }.build()
}

/** Height the user pinned with [setVideoHeight], or `null` for automatic. */
val Player.pinnedVideoHeight: Int?
    get() = trackSelectionParameters.maxVideoHeight.takeIf { it != Int.MAX_VALUE }
