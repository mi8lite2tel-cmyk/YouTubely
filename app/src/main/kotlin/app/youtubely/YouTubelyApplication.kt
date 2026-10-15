// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.youtubely.core.download.DownloadManager
import app.youtubely.core.sync.SyncScheduler
import app.youtubely.widget.WidgetUpdater
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class YouTubelyApplication :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    @Inject
    lateinit var client: OkHttpClient

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    @Inject
    lateinit var downloads: DownloadManager

    override fun onCreate() {
        super.onCreate()
        // Keeps the background refresh of subscriptions in line with the settings.
        syncScheduler.start()
        widgetUpdater.start()
        // Downloads interrupted by a kill or a reboot continue; records of vanished files go away.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { downloads.reconcile() } }
    }

    /** Lets WorkManager create Hilt-injected workers (downloads). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /** Thumbnails share the app's HTTP client (connection pool, proxy settings later on). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .crossfade(true)
            .build()
}
