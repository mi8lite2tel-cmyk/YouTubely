// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.youtubely.core.datastore.SyncSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the periodic refresh of subscriptions in line with the settings. */
@Singleton
class SyncScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settings: SyncSettingsRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val workManager get() = WorkManager.getInstance(context)

        /** Call once at app start. */
        fun start() {
            scope.launch {
                settings.settings
                    .map { Triple(it.refreshEnabled, it.refreshIntervalHours, it.refreshOnWifiOnly) }
                    .distinctUntilChanged()
                    .collect { (enabled, hours, wifiOnly) -> if (enabled) schedule(hours, wifiOnly) else cancel() }
            }
        }

        /** Refreshes right away (once), e.g. after subscriptions were imported. */
        fun refreshNow() {
            val request =
                OneTimeWorkRequestBuilder<FeedSyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            workManager.enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.REPLACE, request)
        }

        private fun schedule(
            hours: Int,
            wifiOnly: Boolean,
        ) {
            val request =
                PeriodicWorkRequestBuilder<FeedSyncWorker>(hours.toLong(), TimeUnit.HOURS)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                            .build(),
                    ).build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        private fun cancel() {
            workManager.cancelUniqueWork(PERIODIC_WORK)
        }

        private companion object {
            const val PERIODIC_WORK = "feed-refresh"
            const val NOW_WORK = "feed-refresh-now"
        }
    }
