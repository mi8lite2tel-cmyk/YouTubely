// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youtubely.core.designsystem.theme.YouTubelyTheme
import app.youtubely.core.designsystem.theme.isDark
import app.youtubely.core.model.VideoId
import app.youtubely.core.model.YouTubeLink
import app.youtubely.core.sync.NewVideoNotifications
import app.youtubely.core.ui.LocalWatchProgress
import app.youtubely.ui.NoticeDialog
import app.youtubely.ui.YouTubelyApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Holds the splash screen until the saved theme is known, so the app never flashes the wrong one.
        installSplashScreen().setKeepOnScreenCondition { viewModel.theme.value == null }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Reopened from Recents the activity carries its first intent again; that link was shown already.
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) handle(intent)
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val noticeAccepted by viewModel.noticeAccepted.collectAsStateWithLifecycle()
            val watchProgress by viewModel.watchProgress.collectAsStateWithLifecycle()
            val pendingLink by viewModel.pendingLink.collectAsStateWithLifecycle()
            val settings = theme ?: return@setContent
            // The system bars follow the app's own light/dark choice, not just the system's.
            val dark = settings.darkMode.isDark()
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            YouTubelyTheme(settings) {
                CompositionLocalProvider(LocalWatchProgress provides watchProgress) {
                    YouTubelyApp(
                        versionName = versionName,
                        pendingLink = pendingLink,
                        onLinkHandled = viewModel::linkHandled,
                    )
                }
                if (noticeAccepted == false) NoticeDialog(onAccept = viewModel::acceptNotice)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A new-video notification, a link opened with the app or a link shared to it. */
    private fun handle(intent: Intent?) {
        val link =
            intent?.getStringExtra(NewVideoNotifications.EXTRA_OPEN_VIDEO)?.let { YouTubeLink.Video(VideoId(it)) }
                ?: intent?.dataString?.let(YouTubeLink::parse)
                ?: intent?.getStringExtra(Intent.EXTRA_TEXT)?.let(YouTubeLink::find)
        link?.let(viewModel::open)
    }
}
