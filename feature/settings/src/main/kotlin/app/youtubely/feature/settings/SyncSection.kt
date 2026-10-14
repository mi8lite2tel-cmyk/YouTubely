// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.youtubely.core.datastore.SyncSettingsRepository
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.SyncSettings
import app.youtubely.core.model.TimeWindow

/** Refresh of subscriptions in the background and announcements of new videos. */
@Composable
internal fun RefreshSettings(
    settings: SyncSettings,
    onChange: (suspend SyncSettingsRepository.() -> Unit) -> Unit,
    askNotificationPermission: () -> Unit,
) {
    val hours = SyncSettings.INTERVAL_CHOICES.map { it to stringResource(R.string.settings_every_hours, it) }
    SettingsGroup {
        SwitchItem(
            title = R.string.settings_refresh,
            summary = R.string.settings_refresh_summary,
            icon = YouTubelyIcons.Subscriptions.filled,
            checked = settings.refreshEnabled,
            position = RowPosition(0, REFRESH_ROWS),
        ) { onChange { setRefreshEnabled(it) } }
        ChoiceRow(
            title = R.string.settings_refresh_interval,
            icon = YouTubelyIcons.Refresh,
            options = hours,
            selected = settings.refreshIntervalHours,
            position = RowPosition(1, REFRESH_ROWS),
        ) { onChange { setRefreshIntervalHours(it) } }
        SwitchItem(
            title = R.string.settings_refresh_wifi,
            summary = R.string.settings_refresh_wifi_summary,
            icon = YouTubelyIcons.Wifi,
            checked = settings.refreshOnWifiOnly,
            position = RowPosition(2, REFRESH_ROWS),
        ) { onChange { setRefreshOnWifiOnly(it) } }
        SwitchItem(
            title = R.string.settings_notifications,
            summary = R.string.settings_notifications_summary,
            icon = YouTubelyIcons.Notifications,
            checked = settings.notificationsEnabled,
            position = RowPosition(3, REFRESH_ROWS),
        ) {
            if (it) askNotificationPermission()
            onChange { setNotificationsEnabled(it) }
        }
    }
}

/** Defaults for the automatic download of channels that have it on; each channel can change them. */
@Composable
internal fun AutoDownloadSettings(
    settings: SyncSettings,
    onChange: (suspend SyncSettingsRepository.() -> Unit) -> Unit,
) {
    val audioOnly = stringResource(R.string.settings_auto_audio_only)
    val qualities =
        SyncSettings.AUTO_HEIGHT_CHOICES.map<Int, Pair<Int?, String>> { it to "${it}p" } + (null to audioOnly)
    val keep = SyncSettings.KEEP_LAST_CHOICES.map { it to stringResource(R.string.settings_auto_keep_count, it) }
    val noLimit = stringResource(R.string.settings_quality_no_limit)
    val limits =
        SyncSettings.LIMIT_GB_CHOICES.map { it to if (it == 0) noLimit else stringResource(R.string.settings_gb, it) }
    val perRun =
        SyncSettings.PER_RUN_CHOICES.map {
            it to if (it == 0) noLimit else stringResource(R.string.settings_auto_per_run_value, it)
        }
    val windows =
        listOf<TimeWindow?>(null).plus(TimeWindow.PRESETS).map { window ->
            window to
                if (window == null) {
                    stringResource(R.string.settings_auto_window_any)
                } else {
                    stringResource(R.string.settings_auto_window_range, window.startHour, window.endHour)
                }
        }
    SettingsGroup {
        ChoiceRow(
            title = R.string.settings_auto_quality,
            icon = YouTubelyIcons.Tune,
            options = qualities,
            selected = settings.autoDownloadMaxHeight,
            position = RowPosition(0, AUTO_ROWS),
        ) { onChange { setAutoDownloadMaxHeight(it) } }
        ChoiceRow(
            title = R.string.settings_auto_keep,
            icon = YouTubelyIcons.Download,
            options = keep,
            selected = settings.autoDownloadKeepLast,
            position = RowPosition(1, AUTO_ROWS),
        ) { onChange { setAutoDownloadKeepLast(it) } }
        ChoiceRow(
            title = R.string.settings_auto_per_run,
            icon = YouTubelyIcons.PlaylistPlay,
            options = perRun,
            selected = settings.autoDownloadPerRun,
            position = RowPosition(2, AUTO_ROWS),
        ) { onChange { setAutoDownloadPerRun(it) } }
        ChoiceRow(
            title = R.string.settings_auto_window,
            icon = YouTubelyIcons.Schedule,
            options = windows,
            selected = settings.autoDownloadWindow,
            position = RowPosition(3, AUTO_ROWS),
        ) { onChange { setAutoDownloadWindow(it) } }
        ChoiceRow(
            title = R.string.settings_auto_limit,
            icon = YouTubelyIcons.Storage,
            options = limits,
            selected = settings.autoDownloadLimitGb,
            position = RowPosition(4, AUTO_ROWS),
        ) { onChange { setAutoDownloadLimitGb(it) } }
        SwitchItem(
            title = R.string.settings_auto_skip_watched,
            summary = R.string.settings_auto_skip_watched_summary,
            icon = YouTubelyIcons.HistoryOff,
            checked = settings.autoDownloadSkipWatched,
            position = RowPosition(5, AUTO_ROWS),
        ) { onChange { setAutoDownloadSkipWatched(it) } }
        SwitchItem(
            title = R.string.settings_auto_charging,
            summary = R.string.settings_auto_charging_summary,
            icon = YouTubelyIcons.Battery,
            checked = settings.autoDownloadOnlyCharging,
            position = RowPosition(6, AUTO_ROWS),
        ) { onChange { setAutoDownloadOnlyCharging(it) } }
    }
}

private const val REFRESH_ROWS = 4
private const val AUTO_ROWS = 7
