// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.youtubely.core.model.SponsorBlockSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Persists [SponsorBlockSettings]. */
class SponsorBlockSettingsRepository(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<SponsorBlockSettings> =
        store.data
            .map { prefs ->
                val defaults = SponsorBlockSettings()
                SponsorBlockSettings(
                    enabled = prefs[ENABLED] ?: defaults.enabled,
                    categories = prefs[CATEGORIES] ?: defaults.categories,
                    serverUrl = prefs[SERVER]?.takeIf { it.isNotBlank() } ?: defaults.serverUrl,
                )
            }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
    }

    suspend fun setCategory(
        category: String,
        selected: Boolean,
    ) {
        store.edit { prefs ->
            val current = prefs[CATEGORIES] ?: SponsorBlockSettings.DEFAULT_CATEGORIES
            prefs[CATEGORIES] = if (selected) current + category else current - category
        }
    }

    /** An empty [url] goes back to the default server. */
    suspend fun setServerUrl(url: String) {
        store.edit { it[SERVER] = url.trim() }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("sponsorblock_enabled")
        val CATEGORIES = stringSetPreferencesKey("sponsorblock_categories")
        val SERVER = stringPreferencesKey("sponsorblock_server")
    }
}
