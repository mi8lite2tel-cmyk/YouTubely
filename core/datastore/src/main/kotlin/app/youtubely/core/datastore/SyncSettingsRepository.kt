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

    private companion object {
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
