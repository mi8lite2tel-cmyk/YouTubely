// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One-off app state that is not a user setting. */
class AppPreferences(
    private val store: DataStore<Preferences>,
) {
    /** The unofficial-client notice (CLAUDE.md, A3) has been acknowledged. */
    val noticeAccepted: Flow<Boolean> = store.data.map { it[NOTICE_ACCEPTED] ?: false }

    suspend fun acceptNotice() {
        store.edit { it[NOTICE_ACCEPTED] = true }
    }

    private companion object {
        val NOTICE_ACCEPTED = booleanPreferencesKey("notice_accepted")
    }
}
