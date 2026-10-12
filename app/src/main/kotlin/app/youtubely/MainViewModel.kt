// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.datastore.AppPreferences
import app.youtubely.core.datastore.ThemeSettingsRepository
import app.youtubely.core.model.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        themeRepository: ThemeSettingsRepository,
        private val preferences: AppPreferences,
    ) : ViewModel() {
        /** `null` until loaded, so the first frame doesn't flash the default theme. */
        val theme: StateFlow<ThemeSettings?> =
            themeRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val noticeAccepted: StateFlow<Boolean?> =
            preferences.noticeAccepted.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        fun acceptNotice() {
            viewModelScope.launch { preferences.acceptNotice() }
        }
    }
