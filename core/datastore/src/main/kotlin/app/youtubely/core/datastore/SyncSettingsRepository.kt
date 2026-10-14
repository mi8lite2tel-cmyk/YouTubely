// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import app.youtubely.core.model.SyncSettings
import app.youtubely.core.model.TimeWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Persists [SyncSettings] and when the feed was last looked at. A stored height of 0 means audio only. */
class SyncSettingsRepository(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<SyncSettings> =
        store.data
            .map { prefs ->
                val defaults = SyncSettings()
                SyncSettings(
                    refreshEnabled = prefs[REFRESH] ?: defaults.refreshEnabled,
                    refreshIntervalHours = prefs[INTERVAL] ?: defaults.refreshIntervalHours,
                    refreshOnWifiOnly = prefs[WIFI_ONLY] ?: defaults.refreshOnWifiOnly,
                    notificationsEnabled = prefs[NOTIFICATIONS] ?: defaults.notificationsEnabled,
                    autoDownloadMaxHeight =
                        when (val height = prefs[AUTO_HEIGHT]) {
                            null -> defaults.autoDownloadMaxHeight
                            AUDIO_ONLY -> null
                            else -> height
                        },
                    autoDownloadKeepLast = prefs[KEEP_LAST] ?: defaults.autoDownloadKeepLast,
                    autoDownloadLimitGb = prefs[LIMIT_GB] ?: defaults.autoDownloadLimitGb,
                    autoDownloadOnlyCharging = prefs[ONLY_CHARGING] ?: defaults.autoDownloadOnlyCharging,
                    autoDownloadWindow = window(prefs[WINDOW_START], prefs[WINDOW_END]),
                    autoDownloadPerRun = prefs[PER_RUN] ?: defaults.autoDownloadPerRun,
                    autoDownloadSkipWatched = prefs[SKIP_WATCHED] ?: defaults.autoDownloadSkipWatched,
                )
            }.distinctUntilChanged()

    /** Epoch millis when the feed was last opened; videos found later count as new. */
    val feedViewedAt: Flow<Long> = store.data.map { it[FEED_VIEWED_AT] ?: 0L }.distinctUntilChanged()

    suspend fun markFeedViewed(at: Long) {
        store.edit { it[FEED_VIEWED_AT] = at }
    }

    suspend fun setRefreshEnabled(enabled: Boolean) = store.edit { it[REFRESH] = enabled }.let {}

    suspend fun setRefreshIntervalHours(hours: Int) = store.edit { it[INTERVAL] = hours }.let {}

    suspend fun setRefreshOnWifiOnly(enabled: Boolean) = store.edit { it[WIFI_ONLY] = enabled }.let {}

    suspend fun setNotificationsEnabled(enabled: Boolean) = store.edit { it[NOTIFICATIONS] = enabled }.let {}

    suspend fun setAutoDownloadMaxHeight(height: Int?) = store.edit { it[AUTO_HEIGHT] = height ?: AUDIO_ONLY }.let {}

    suspend fun setAutoDownloadKeepLast(count: Int) = store.edit { it[KEEP_LAST] = count }.let {}

    suspend fun setAutoDownloadLimitGb(gigabytes: Int) = store.edit { it[LIMIT_GB] = gigabytes }.let {}

    suspend fun setAutoDownloadOnlyCharging(enabled: Boolean) = store.edit { it[ONLY_CHARGING] = enabled }.let {}

    /** `null` removes the window: downloads may start at any time. */
    suspend fun setAutoDownloadWindow(window: TimeWindow?) =
        store
            .edit {
                if (window == null) {
                    it.remove(WINDOW_START)
                    it.remove(WINDOW_END)
                } else {
                    it[WINDOW_START] = window.startHour
                    it[WINDOW_END] = window.endHour
                }
            }.let {}

    suspend fun setAutoDownloadPerRun(count: Int) = store.edit { it[PER_RUN] = count }.let {}

    suspend fun setAutoDownloadSkipWatched(enabled: Boolean) = store.edit { it[SKIP_WATCHED] = enabled }.let {}

    private fun window(
        start: Int?,
        end: Int?,
    ): TimeWindow? =
        if (start != null &&
            end != null
        ) {
            TimeWindow(start.coerceIn(0, LAST_HOUR), end.coerceIn(0, LAST_HOUR))
        } else {
            null
        }

    private companion object {
        const val LAST_HOUR = 23
        val WINDOW_START = intPreferencesKey("sync_window_start")
        val WINDOW_END = intPreferencesKey("sync_window_end")
        val PER_RUN = intPreferencesKey("sync_per_run")
        val SKIP_WATCHED = booleanPreferencesKey("sync_skip_watched")
        const val AUDIO_ONLY = 0
        val REFRESH = booleanPreferencesKey("sync_refresh")
        val INTERVAL = intPreferencesKey("sync_interval_hours")
        val WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
        val NOTIFICATIONS = booleanPreferencesKey("sync_notifications")
        val AUTO_HEIGHT = intPreferencesKey("sync_auto_height")
        val KEEP_LAST = intPreferencesKey("sync_keep_last")
        val LIMIT_GB = intPreferencesKey("sync_limit_gb")
        val ONLY_CHARGING = booleanPreferencesKey("sync_only_charging")
        val FEED_VIEWED_AT = longPreferencesKey("feed_viewed_at")
    }
}
