// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.youtubely.core.model.DarkMode
import app.youtubely.core.model.ThemeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Persists [ThemeSettings]. Unknown or missing values fall back to the defaults. */
class ThemeSettingsRepository(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<ThemeSettings> =
        store.data
            .map { prefs ->
                val defaults = ThemeSettings()
                ThemeSettings(
                    darkMode =
                        prefs[DARK_MODE]?.let { name -> DarkMode.entries.firstOrNull { it.name == name } }
                            ?: defaults.darkMode,
                    dynamicColor = prefs[DYNAMIC_COLOR] ?: defaults.dynamicColor,
                    pureBlack = prefs[PURE_BLACK] ?: defaults.pureBlack,
                    highContrast = prefs[HIGH_CONTRAST] ?: defaults.highContrast,
                )
            }.distinctUntilChanged()

    suspend fun setDarkMode(mode: DarkMode) {
        store.edit { it[DARK_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setPureBlack(enabled: Boolean) {
        store.edit { it[PURE_BLACK] = enabled }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        store.edit { it[HIGH_CONTRAST] = enabled }
    }

    private companion object {
        val DARK_MODE = stringPreferencesKey("theme_dark_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("theme_dynamic_color")
        val PURE_BLACK = booleanPreferencesKey("theme_pure_black")
        val HIGH_CONTRAST = booleanPreferencesKey("theme_high_contrast")
    }
}
