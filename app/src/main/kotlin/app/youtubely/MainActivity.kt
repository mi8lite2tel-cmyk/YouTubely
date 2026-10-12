// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youtubely.core.designsystem.theme.YouTubelyTheme
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
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val noticeAccepted by viewModel.noticeAccepted.collectAsStateWithLifecycle()
            val settings = theme ?: return@setContent
            YouTubelyTheme(settings) {
                YouTubelyApp(versionName = versionName)
                if (noticeAccepted == false) NoticeDialog(onAccept = viewModel::acceptNotice)
            }
        }
    }
}
