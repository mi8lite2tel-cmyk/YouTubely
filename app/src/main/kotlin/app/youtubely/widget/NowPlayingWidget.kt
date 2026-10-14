// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.youtubely.MainActivity
import app.youtubely.R
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.bestFor
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What is playing, with play / pause; a tap opens the app. */
class NowPlayingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val playback = context.widgetEntryPoint().playback()
        val playing = playback.nowPlaying.value
        val isPlaying = playback.isPlaying.value
        val artwork =
            playing
                ?.item
                ?.thumbnails
                ?.bestFor(ARTWORK_WIDTH)
                ?.url
                ?.let { loadBitmap(context, it) }
        provideContent {
            GlanceTheme {
                Content(playing?.item?.title, playing?.item?.channel?.name, isPlaying, artwork)
            }
        }
    }

    @Composable
    private fun Content(
        title: String?,
        channel: String?,
        isPlaying: Boolean,
        artwork: Bitmap?,
    ) {
        val context = LocalContext.current
        Row(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(24.dp)
                    .padding(12.dp)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (artwork != null) {
                Image(
                    provider = ImageProvider(artwork),
                    contentDescription = null,
                    modifier = GlanceModifier.size(width = 96.dp, height = 54.dp).cornerRadius(12.dp),
                )
                Spacer(GlanceModifier.width(12.dp))
            }
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    title ?: context.getString(R.string.widget_nothing_playing),
                    maxLines = 2,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium),
                )
                if (channel != null) {
                    Spacer(GlanceModifier.height(2.dp))
                    Text(channel, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                }
            }
            if (title != null) {
                Image(
                    provider = ImageProvider(if (isPlaying) YouTubelyIcons.Pause else YouTubelyIcons.Play),
                    contentDescription =
                        context.getString(
                            if (isPlaying) R.string.widget_pause else R.string.widget_play,
                        ),
                    colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                    modifier =
                        GlanceModifier
                            .size(48.dp)
                            .background(GlanceTheme.colors.primaryContainer)
                            .cornerRadius(24.dp)
                            .padding(12.dp)
                            .clickable(actionRunCallback<TogglePlayback>()),
                )
            }
        }
    }

    private suspend fun loadBitmap(
        context: Context,
        url: String,
    ): Bitmap? =
        runCatching {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .size(ARTWORK_WIDTH, ARTWORK_WIDTH * 9 / 16)
                    .allowHardware(false)
                    .build()
            SingletonImageLoader
                .get(context)
                .execute(request)
                .image
                ?.toBitmap()
        }.getOrNull()

    private companion object {
        const val ARTWORK_WIDTH = 320
    }
}

/** Plays or pauses the shared player from the widget. */
class TogglePlayback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val playback = context.widgetEntryPoint().playback()
        withContext(Dispatchers.Main) {
            val player = playback.player
            if (player.isPlaying) player.pause() else player.play()
        }
        NowPlayingWidget().updateAll(context)
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NowPlayingWidget()
}
