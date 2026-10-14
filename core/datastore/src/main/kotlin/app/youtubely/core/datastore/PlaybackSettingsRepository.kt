// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
                    audioOnly = prefs[AUDIO_ONLY] ?: defaults.audioOnly,
                    seekStepSeconds = prefs[SEEK_STEP] ?: defaults.seekStepSeconds,
                    historyEnabled = prefs[HISTORY] ?: defaults.historyEnabled,
                    resumePlayback = prefs[RESUME] ?: defaults.resumePlayback,
                    skipSilence = prefs[SKIP_SILENCE] ?: defaults.skipSilence,
                    normalizeVolume = prefs[NORMALIZE] ?: defaults.normalizeVolume,
                    autoplayNext = prefs[AUTOPLAY] ?: defaults.autoplayNext,
                    subtitlesEnabled = prefs[SUBTITLES] ?: defaults.subtitlesEnabled,
                    subtitleLanguage = prefs[SUBTITLE_LANGUAGE]?.takeIf { it.isNotBlank() },
                    subtitleScale = prefs[SUBTITLE_SCALE] ?: defaults.subtitleScale,
                    subtitleBackground = prefs[SUBTITLE_BACKGROUND] ?: defaults.subtitleBackground,
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

    suspend fun setAudioOnly(enabled: Boolean) {
        store.edit { it[AUDIO_ONLY] = enabled }
    }

    suspend fun setSeekStepSeconds(seconds: Int) {
        store.edit { it[SEEK_STEP] = seconds }
    }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        store.edit { it[HISTORY] = enabled }
    }

    suspend fun setResumePlayback(enabled: Boolean) {
        store.edit { it[RESUME] = enabled }
    }

    suspend fun setSkipSilence(enabled: Boolean) {
        store.edit { it[SKIP_SILENCE] = enabled }
    }

    suspend fun setNormalizeVolume(enabled: Boolean) {
        store.edit { it[NORMALIZE] = enabled }
    }

    suspend fun setAutoplayNext(enabled: Boolean) {
        store.edit { it[AUTOPLAY] = enabled }
    }

    suspend fun setSubtitlesEnabled(enabled: Boolean) {
        store.edit { it[SUBTITLES] = enabled }
    }

    /** `null` goes back to the app's language. */
    suspend fun setSubtitleLanguage(tag: String?) {
        store.edit { it[SUBTITLE_LANGUAGE] = tag.orEmpty() }
    }

    suspend fun setSubtitleScale(scale: Float) {
        store.edit { it[SUBTITLE_SCALE] = scale }
    }

    suspend fun setSubtitleBackground(enabled: Boolean) {
        store.edit { it[SUBTITLE_BACKGROUND] = enabled }
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
        val AUDIO_ONLY = booleanPreferencesKey("playback_audio_only")
        val SEEK_STEP = intPreferencesKey("playback_seek_step_seconds")
        val HISTORY = booleanPreferencesKey("history_enabled")
        val RESUME = booleanPreferencesKey("playback_resume")
        val SKIP_SILENCE = booleanPreferencesKey("playback_skip_silence")
        val NORMALIZE = booleanPreferencesKey("playback_normalize_volume")
        val AUTOPLAY = booleanPreferencesKey("playback_autoplay_next")
        val SUBTITLES = booleanPreferencesKey("playback_subtitles")
        val SUBTITLE_LANGUAGE = stringPreferencesKey("playback_subtitle_language")
        val SUBTITLE_SCALE = floatPreferencesKey("playback_subtitle_scale")
        val SUBTITLE_BACKGROUND = booleanPreferencesKey("playback_subtitle_background")
    }
}
