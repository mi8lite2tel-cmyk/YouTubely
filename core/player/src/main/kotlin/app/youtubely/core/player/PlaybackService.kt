// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hosts the media session around [PlaybackManager.player]. Media3 turns it into a foreground
 * service with a media notification while playing; the default task-removed behavior keeps
 * playing audio alive and stops the service otherwise.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject
    lateinit var manager: PlaybackManager

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val openApp =
            packageManager
                .getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                ?.let {
                    val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    PendingIntent.getActivity(this, 0, it, flags)
                }
        session =
            MediaSession
                .Builder(this, manager.player)
                .apply { openApp?.let(::setSessionActivity) }
                .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
