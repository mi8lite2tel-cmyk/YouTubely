// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youtubely.core.data.TrafficMeter
import app.youtubely.core.datastore.PlaybackSettingsRepository
import app.youtubely.core.datastore.SponsorBlockSettingsRepository
import app.youtubely.core.datastore.SyncSettingsRepository
import app.youtubely.core.datastore.ThemeSettingsRepository
import app.youtubely.core.model.AnimationSpeed
import app.youtubely.core.model.DarkMode
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.SponsorBlockSettings
import app.youtubely.core.model.SyncSettings
import app.youtubely.core.model.ThemeSettings
import app.youtubely.core.model.TrafficSummary
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
        private val sync: SyncSettingsRepository,
        private val sponsorBlock: SponsorBlockSettingsRepository,
        private val trafficMeter: TrafficMeter,
    ) : ViewModel() {
        private val sharing = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS)

        val themeSettings: StateFlow<ThemeSettings> = theme.settings.stateIn(viewModelScope, sharing, ThemeSettings())

        val playbackSettings: StateFlow<PlaybackSettings> =
            playback.settings.stateIn(viewModelScope, sharing, PlaybackSettings())

        val syncSettings: StateFlow<SyncSettings> = sync.settings.stateIn(viewModelScope, sharing, SyncSettings())

        /** Applies one change to the sync settings, e.g. `sync { setRefreshEnabled(true) }`. */
        fun sync(change: suspend SyncSettingsRepository.() -> Unit) = launch { sync.change() }

        val sponsorBlockSettings: StateFlow<SponsorBlockSettings> =
            sponsorBlock.settings.stateIn(viewModelScope, sharing, SponsorBlockSettings())

        /** Applies one change to the SponsorBlock settings. */
        fun sponsorBlock(change: suspend SponsorBlockSettingsRepository.() -> Unit) = launch { sponsorBlock.change() }

        /** What the app used this month and over the last days. */
        val traffic: StateFlow<TrafficSummary> = trafficMeter.summary.stateIn(viewModelScope, sharing, TrafficSummary())

        fun resetTraffic() = launch { trafficMeter.reset() }

        fun setDarkMode(mode: DarkMode) = launch { theme.setDarkMode(mode) }

        fun setAnimationSpeed(speed: AnimationSpeed) = launch { theme.setAnimationSpeed(speed) }

        fun setDynamicColor(enabled: Boolean) = launch { theme.setDynamicColor(enabled) }

        fun setPureBlack(enabled: Boolean) = launch { theme.setPureBlack(enabled) }

        fun setHighContrast(enabled: Boolean) = launch { theme.setHighContrast(enabled) }

        /** Applies one change to the playback settings, e.g. `playback { setAudioOnly(true) }`. */
        fun playback(change: suspend PlaybackSettingsRepository.() -> Unit) = launch { playback.change() }

        private fun launch(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
