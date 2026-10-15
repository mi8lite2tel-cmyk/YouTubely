// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import app.youtubely.core.model.StreamInfo
import app.youtubely.core.model.StreamType
import app.youtubely.core.model.Subtitle
import okhttp3.OkHttpClient
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Creates players and media sources that share the app's OkHttp client. */
@OptIn(UnstableApi::class)
@Singleton
class YouTubelyPlayer
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val bandwidth: BandwidthStore,
    ) {
        /** Lets the data saver shorten the buffer while the player runs. */
        internal val loadControl = SavingLoadControl(DefaultLoadControl())

        fun createPlayer(context: Context): ExoPlayer =
            ExoPlayer
                .Builder(context)
                // Keeps the CPU and Wi-Fi awake while playing with the screen off. Without this the phone
                // dozes after a few minutes, the buffer runs dry and playback stops for good.
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setLoadControl(loadControl)
                .setBandwidthMeter(bandwidthMeter(context))
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

        /** Starts from the speed the connection had last time instead of a cautious default. */
        private fun bandwidthMeter(context: Context): DefaultBandwidthMeter {
            val builder = DefaultBandwidthMeter.Builder(context)
            val wifi = bandwidth.estimate(metered = false)
            val mobile = bandwidth.estimate(metered = true)
            if (wifi != null) builder.setInitialBitrateEstimate(C.NETWORK_TYPE_WIFI, wifi)
            if (mobile != null) {
                MOBILE_NETWORK_TYPES.forEach { builder.setInitialBitrateEstimate(it, mobile) }
            }
            return builder.build()
        }

        /** Returns `null` when the video has nothing playable (e.g. only formats the app can't handle). */
        @Suppress("SpreadOperator") // Media3 takes the sources as a vararg; there are only a few.
        fun mediaSource(info: StreamInfo): MediaSource? {
            val main = videoSource(info) ?: return null
            val subtitles = subtitleSources(info)
            return if (subtitles.isEmpty()) main else MergingMediaSource(main, *subtitles.toTypedArray())
        }

        /** One lazily loaded source per subtitle track; the user's language comes first. */
        @Suppress("DEPRECATION") // Sideloaded text through the media item cannot be loaded lazily per track.
        private fun subtitleSources(info: StreamInfo): List<MediaSource> {
            if (info.item.type == StreamType.Live) return emptyList()
            val factory =
                SingleSampleMediaSource
                    .Factory(dataSourceFactory(emptyMap()))
                    // One unreachable subtitle file must not stop the video.
                    .setTreatLoadErrorsAsEndOfStream(true)
            val device = Locale.getDefault().language
            return info.subtitles
                .sortedWith(
                    compareByDescending<Subtitle> { it.languageTag.substringBefore('-') == device }
                        .thenBy { it.isAutoGenerated },
                ).take(MAX_SUBTITLES)
                .map { subtitle ->
                    val configuration =
                        MediaItem.SubtitleConfiguration
                            .Builder(subtitle.url.toUri())
                            .setMimeType(subtitle.mimeType)
                            .setLanguage(subtitle.languageTag)
                            .setLabel(subtitleLabel(subtitle))
                            .setSelectionFlags(0)
                            .build()
                    factory.createMediaSource(configuration, C.TIME_UNSET)
                }
        }

        private fun subtitleLabel(subtitle: Subtitle): String {
            val name = Locale.forLanguageTag(subtitle.languageTag).getDisplayLanguage(Locale.getDefault())
            return name.ifBlank { subtitle.languageTag }.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }

        private fun videoSource(info: StreamInfo): MediaSource? {
            val source = PlaybackSource.from(info) ?: return null
            val dataSourceFactory = dataSourceFactory(source.headers)
            val mediaItem =
                MediaItem
                    .Builder()
                    .setMediaId(info.item.id.value)
                    .setMediaMetadata(info.item.toMediaMetadata())
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
            const val MAX_SUBTITLES = 40
            val MOBILE_NETWORK_TYPES =
                listOf(
                    C.NETWORK_TYPE_2G,
                    C.NETWORK_TYPE_3G,
                    C.NETWORK_TYPE_4G,
                    C.NETWORK_TYPE_5G_NSA,
                    C.NETWORK_TYPE_5G_SA,
                    C.NETWORK_TYPE_CELLULAR_UNKNOWN,
                )

            /** Placeholder base for the generated manifest; every representation has an absolute BaseURL. */
            val MANIFEST_URI: Uri = "https://youtubely.invalid/manifest.mpd".toUri()
        }
    }

/** One selectable resolution with the best bitrate the source offers for it. */
data class QualityOption(
    val height: Int,
    val fps: Int,
    val bitrate: Int?,
)

/** Resolutions the player can switch between, highest first. */
@OptIn(UnstableApi::class)
fun Player.qualityOptions(): List<QualityOption> =
    currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group ->
            (0 until group.length).filter(group::isTrackSupported).map { group.getTrackFormat(it) }
        }.filter { it.height > 0 }
        .groupBy { it.height }
        .map { (height, formats) ->
            val best = formats.maxBy { it.bitrate }
            QualityOption(
                height = height,
                fps = best.frameRate.takeIf { it > 0 }?.toInt() ?: 0,
                bitrate = best.bitrate.takeIf { it > 0 },
            )
        }.sortedByDescending { it.height }

/** One subtitle language the current video offers. */
data class SubtitleOption(
    val language: String,
    val label: String,
    val selected: Boolean,
)

/** Subtitle languages of the current video, as the player has them. */
@OptIn(UnstableApi::class)
fun Player.subtitleOptions(): List<SubtitleOption> =
    currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_TEXT }
        .flatMap { group ->
            (0 until group.length).map { index ->
                val format = group.getTrackFormat(index)
                SubtitleOption(
                    language = format.language.orEmpty(),
                    label = format.label ?: format.language.orEmpty(),
                    selected = group.isTrackSelected(index),
                )
            }
        }.filter { it.language.isNotEmpty() }
        .distinctBy { it.language }
