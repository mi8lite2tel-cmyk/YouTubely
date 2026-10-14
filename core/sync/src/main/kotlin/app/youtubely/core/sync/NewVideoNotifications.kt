// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.sync

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import app.youtubely.core.model.FreshVideo

/** Announces new videos of channels the user asked to be told about. */
object NewVideoNotifications {
    /** Extra of the launch intent: the id of the video to open. */
    const val EXTRA_OPEN_VIDEO = "app.youtubely.OPEN_VIDEO"

    private const val CHANNEL_ID = "new_videos"
    private const val GROUP = "new_videos"
    private const val SUMMARY_ID = 1
    private const val MAX_SHOWN = 8

    @SuppressLint("MissingPermission")
    fun show(
        context: Context,
        videos: List<FreshVideo>,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (videos.isEmpty() || !manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val shown = videos.takeLast(MAX_SHOWN)
        shown.forEach { video ->
            manager.notify(
                video.item.id.value
                    .hashCode(),
                notification(context, video),
            )
        }
        manager.notify(
            SUMMARY_ID,
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_new_video)
                .setContentTitle(context.getString(R.string.sync_new_video))
                .setContentText(
                    context.resources.getQuantityString(R.plurals.sync_new_videos_summary, videos.size, videos.size),
                ).setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun notification(
        context: Context,
        video: FreshVideo,
    ) = NotificationCompat
        .Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_new_video)
        .setContentTitle(video.subscription.channel.name)
        .setContentText(video.item.title)
        .setStyle(NotificationCompat.BigTextStyle().bigText(video.item.title))
        .setContentIntent(openVideo(context, video))
        .setGroup(GROUP)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun openVideo(
        context: Context,
        video: FreshVideo,
    ): PendingIntent {
        val intent =
            context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply {
                    putExtra(EXTRA_OPEN_VIDEO, video.item.id.value)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                } ?: Intent()
        return PendingIntent.getActivity(
            context,
            video.item.id.value
                .hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sync_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}
