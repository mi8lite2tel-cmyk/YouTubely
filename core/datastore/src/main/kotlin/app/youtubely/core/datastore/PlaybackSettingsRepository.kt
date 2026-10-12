// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import app.youtubely.core.model.PlaybackSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Persists [PlaybackSettings]. A stored height of 0 means "no limit". */
class PlaybackSettingsRepository(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<PlaybackSettings> =
        store.data
            .map { prefs ->
                val defaults = PlaybackSettings()
                PlaybackSettings(
                    maxHeightUnmetered = prefs[MAX_UNMETERED].limitOr(defaults.maxHeightUnmetered),
                    maxHeightMetered = prefs[MAX_METERED].limitOr(defaults.maxHeightMetered),
                    backgroundPlayback = prefs[BACKGROUND] ?: defaults.backgroundPlayback,
                    pictureInPicture = prefs[PIP] ?: defaults.pictureInPicture,
                    downloadOnWifiOnly = prefs[DOWNLOAD_WIFI_ONLY] ?: defaults.downloadOnWifiOnly,
                )
            }.distinctUntilChanged()

    suspend fun setMaxHeightUnmetered(height: Int?) {
        store.edit { it[MAX_UNMETERED] = height ?: NO_LIMIT }
    }

    suspend fun setMaxHeightMetered(height: Int?) {
        store.edit { it[MAX_METERED] = height ?: NO_LIMIT }
    }

    suspend fun setBackgroundPlayback(enabled: Boolean) {
        store.edit { it[BACKGROUND] = enabled }
    }

    suspend fun setPictureInPicture(enabled: Boolean) {
        store.edit { it[PIP] = enabled }
    }

    suspend fun setDownloadOnWifiOnly(enabled: Boolean) {
        store.edit { it[DOWNLOAD_WIFI_ONLY] = enabled }
    }

    private fun Int?.limitOr(default: Int?): Int? =
        when (this) {
            null -> default
            NO_LIMIT -> null
            else -> this
        }

    private companion object {
        const val NO_LIMIT = 0
        val MAX_UNMETERED = intPreferencesKey("playback_max_height_unmetered")
        val MAX_METERED = intPreferencesKey("playback_max_height_metered")
        val BACKGROUND = booleanPreferencesKey("playback_background")
        val PIP = booleanPreferencesKey("playback_pip")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
    }
}
