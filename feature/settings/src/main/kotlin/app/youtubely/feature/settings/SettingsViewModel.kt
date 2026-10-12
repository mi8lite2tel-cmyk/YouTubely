// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.datastore.PlaybackSettingsRepository
import app.youtubely.core.datastore.ThemeSettingsRepository
import app.youtubely.core.model.DarkMode
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val theme: ThemeSettingsRepository,
        private val playback: PlaybackSettingsRepository,
    ) : ViewModel() {
        private val sharing = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS)

        val themeSettings: StateFlow<ThemeSettings> = theme.settings.stateIn(viewModelScope, sharing, ThemeSettings())

        val playbackSettings: StateFlow<PlaybackSettings> =
            playback.settings.stateIn(viewModelScope, sharing, PlaybackSettings())

        fun setDarkMode(mode: DarkMode) = launch { theme.setDarkMode(mode) }

        fun setDynamicColor(enabled: Boolean) = launch { theme.setDynamicColor(enabled) }

        fun setPureBlack(enabled: Boolean) = launch { theme.setPureBlack(enabled) }

        fun setHighContrast(enabled: Boolean) = launch { theme.setHighContrast(enabled) }

        fun setBackgroundPlayback(enabled: Boolean) = launch { playback.setBackgroundPlayback(enabled) }

        fun setPictureInPicture(enabled: Boolean) = launch { playback.setPictureInPicture(enabled) }

        fun setMaxHeightUnmetered(height: Int?) = launch { playback.setMaxHeightUnmetered(height) }

        fun setMaxHeightMetered(height: Int?) = launch { playback.setMaxHeightMetered(height) }

        fun setDownloadOnWifiOnly(enabled: Boolean) = launch { playback.setDownloadOnWifiOnly(enabled) }

        private fun launch(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
