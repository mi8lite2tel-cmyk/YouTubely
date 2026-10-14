// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.youtubely.core.datastore.SyncSettingsRepository
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.SyncSettings

/** One row that opens a menu of [options]; [selected] is ticked. */
@Composable
internal fun <T> ChoiceRow(
    title: Int,
    icon: Int,
    options: List<Pair<T, String>>,
    selected: T,
    index: Int,
    count: Int,
    onChange: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SegmentedListItem(
            onClick = { expanded = true },
            shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
            leadingContent = { Icon(painterResource(icon), contentDescription = null) },
            supportingContent = { Text(options.firstOrNull { it.first == selected }?.second.orEmpty()) },
        ) { Text(stringResource(title)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon =
                        if (value == selected) {
                            { Icon(painterResource(YouTubelyIcons.Check), contentDescription = null) }
                        } else {
                            null
                        },
                    onClick = {
                        onChange(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchItem(
    title: Int,
    summary: Int,
    icon: Int,
    checked: Boolean,
    index: Int,
    count: Int,
    onChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        supportingContent = { Text(stringResource(summary)) },
    ) { Text(stringResource(title)) }
}

/** Refresh of subscriptions in the background and announcements of new videos. */
@Composable
internal fun RefreshSettings(
    settings: SyncSettings,
    onChange: (suspend SyncSettingsRepository.() -> Unit) -> Unit,
    askNotificationPermission: () -> Unit,
) {
    val hours = SyncSettings.INTERVAL_CHOICES.map { it to stringResource(R.string.settings_every_hours, it) }
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SwitchItem(
            title = R.string.settings_refresh,
            summary = R.string.settings_refresh_summary,
            icon = YouTubelyIcons.Subscriptions.filled,
            checked = settings.refreshEnabled,
            index = 0,
            count = REFRESH_ROWS,
        ) { onChange { setRefreshEnabled(it) } }
        ChoiceRow(
            title = R.string.settings_refresh_interval,
            icon = YouTubelyIcons.Refresh,
            options = hours,
            selected = settings.refreshIntervalHours,
            index = 1,
            count = REFRESH_ROWS,
        ) { onChange { setRefreshIntervalHours(it) } }
        SwitchItem(
            title = R.string.settings_refresh_wifi,
            summary = R.string.settings_refresh_wifi_summary,
            icon = YouTubelyIcons.Wifi,
            checked = settings.refreshOnWifiOnly,
            index = 2,
            count = REFRESH_ROWS,
        ) { onChange { setRefreshOnWifiOnly(it) } }
        SwitchItem(
            title = R.string.settings_notifications,
            summary = R.string.settings_notifications_summary,
            icon = YouTubelyIcons.Notifications,
            checked = settings.notificationsEnabled,
            index = 3,
            count = REFRESH_ROWS,
        ) {
            if (it) askNotificationPermission()
            onChange { setNotificationsEnabled(it) }
        }
    }
}

/** Automatic download of new videos of channels that have it switched on. */
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
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        ChoiceRow(
            title = R.string.settings_auto_quality,
            icon = YouTubelyIcons.Tune,
            options = qualities,
            selected = settings.autoDownloadMaxHeight,
            index = 0,
            count = AUTO_ROWS,
        ) { onChange { setAutoDownloadMaxHeight(it) } }
        ChoiceRow(
            title = R.string.settings_auto_keep,
            icon = YouTubelyIcons.Download,
            options = keep,
            selected = settings.autoDownloadKeepLast,
            index = 1,
            count = AUTO_ROWS,
        ) { onChange { setAutoDownloadKeepLast(it) } }
        ChoiceRow(
            title = R.string.settings_auto_limit,
            icon = YouTubelyIcons.Storage,
            options = limits,
            selected = settings.autoDownloadLimitGb,
            index = 2,
            count = AUTO_ROWS,
        ) { onChange { setAutoDownloadLimitGb(it) } }
        SwitchItem(
            title = R.string.settings_auto_charging,
            summary = R.string.settings_auto_charging_summary,
            icon = YouTubelyIcons.Battery,
            checked = settings.autoDownloadOnlyCharging,
            index = 3,
            count = AUTO_ROWS,
        ) { onChange { setAutoDownloadOnlyCharging(it) } }
    }
}

private const val REFRESH_ROWS = 4
private const val AUTO_ROWS = 4
