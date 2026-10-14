// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.youtubely.MainActivity
import app.youtubely.R
import app.youtubely.core.model.StreamItem
import kotlinx.coroutines.flow.first

/** The newest videos of the followed channels; a tap opens one in the app. */
class NewVideosWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val items =
            context
                .widgetEntryPoint()
                .feed()
                .state
                .first()
                .items
                .take(MAX_ITEMS)
        provideContent { GlanceTheme { Content(items) } }
    }

    @Composable
    private fun Content(items: List<StreamItem>) {
        val context = LocalContext.current
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(16.dp),
        ) {
            Text(
                context.getString(R.string.widget_new_videos),
                style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            )
            Spacer(GlanceModifier.height(8.dp))
            if (items.isEmpty()) {
                Text(
                    context.getString(R.string.widget_no_new_videos),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            } else {
                LazyColumn {
                    items(items, itemId = {
                        it.id.value
                            .hashCode()
                            .toLong()
                    }) { item ->
                        Column(
                            GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable(actionStartActivity(openIntent(context, item))),
                        ) {
                            Text(
                                item.title,
                                maxLines = 2,
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium),
                            )
                            item.channel?.let {
                                Text(
                                    it.name,
                                    maxLines = 1,
                                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** The app opens YouTube links itself, so a plain watch link shows the video. */
    private fun openIntent(
        context: Context,
        item: StreamItem,
    ) = Intent(
        Intent.ACTION_VIEW,
        "https://www.youtube.com/watch?v=${item.id.value}".toUri(),
        context,
        MainActivity::class.java,
    )

    private companion object {
        const val MAX_ITEMS = 8
    }
}

class NewVideosWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NewVideosWidget()
}
