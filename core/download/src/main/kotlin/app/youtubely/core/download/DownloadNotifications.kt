// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService

internal object DownloadNotifications {
    const val CHANNEL_ID = "downloads"
    private const val PERCENT = 100

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun progress(
        context: Context,
        title: String,
        progress: Float?,
        merging: Boolean,
    ) = NotificationCompat
        .Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title)
        .setContentText(
            context.getString(if (merging) R.string.download_merging else R.string.download_downloading),
        ).setOnlyAlertOnce(true)
        .setOngoing(true)
        .setSilent(true)
        .setProgress(PERCENT, ((progress ?: 0f) * PERCENT).toInt(), progress == null || merging)
        .build()
}
