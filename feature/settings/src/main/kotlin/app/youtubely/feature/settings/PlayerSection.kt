// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.youtubely.core.datastore.PlaybackSettingsRepository
import app.youtubely.core.datastore.SponsorBlockSettingsRepository
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.model.PlaybackSettings
import app.youtubely.core.model.SponsorBlockSettings
import app.youtubely.core.ui.groupedListItemColors

private const val SUBTITLE_ROWS = 3
private const val SPONSOR_ROWS = 3
private const val PERCENT = 100

/** Size and background of subtitles and whether they are shown by default. */
@Composable
internal fun SubtitleSettings(
    settings: PlaybackSettings,
    onChange: (suspend PlaybackSettingsRepository.() -> Unit) -> Unit,
) {
    val sizes = PlaybackSettings.SUBTITLE_SCALES.map { it to "${(it * PERCENT).toInt()}%" }
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SegmentedListItem(
            checked = settings.subtitlesEnabled,
            onCheckedChange = { enabled -> onChange { setSubtitlesEnabled(enabled) } },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = SUBTITLE_ROWS),
            colors = groupedListItemColors(),
            leadingContent = { Icon(painterResource(YouTubelyIcons.Language), contentDescription = null) },
            trailingContent = { Switch(checked = settings.subtitlesEnabled, onCheckedChange = null) },
            supportingContent = { Text(stringResource(R.string.settings_subtitles_default_summary)) },
        ) { Text(stringResource(R.string.settings_subtitles_default)) }
        ChoiceRow(
            title = R.string.settings_subtitle_size,
            icon = YouTubelyIcons.AspectRatio,
            options = sizes,
            selected = settings.subtitleScale,
            position = RowPosition(1, SUBTITLE_ROWS),
        ) { scale -> onChange { setSubtitleScale(scale) } }
        SegmentedListItem(
            checked = settings.subtitleBackground,
            onCheckedChange = { enabled -> onChange { setSubtitleBackground(enabled) } },
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = SUBTITLE_ROWS),
            colors = groupedListItemColors(),
            leadingContent = { Icon(painterResource(YouTubelyIcons.Contrast), contentDescription = null) },
            trailingContent = { Switch(checked = settings.subtitleBackground, onCheckedChange = null) },
            supportingContent = { Text(stringResource(R.string.settings_subtitle_background_summary)) },
        ) { Text(stringResource(R.string.settings_subtitle_background)) }
    }
}

/** SponsorBlock is opt-in; the rows say what is sent and let the user pick categories and a server. */
@Composable
internal fun SponsorBlockSettingsSection(
    settings: SponsorBlockSettings,
    onChange: ((suspend SponsorBlockSettingsRepository.() -> Unit)) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SegmentedListItem(
            checked = settings.enabled,
            onCheckedChange = { enabled -> onChange { setEnabled(enabled) } },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = SPONSOR_ROWS),
            colors = groupedListItemColors(),
            leadingContent = { Icon(painterResource(YouTubelyIcons.FastForward), contentDescription = null) },
            trailingContent = { Switch(checked = settings.enabled, onCheckedChange = null) },
            supportingContent = { Text(stringResource(R.string.settings_sponsorblock_summary)) },
        ) { Text(stringResource(R.string.settings_sponsorblock_enable)) }
        SegmentedListItem(
            onClick = { picking = true },
            enabled = settings.enabled,
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = SPONSOR_ROWS),
            colors = groupedListItemColors(),
            leadingContent = { Icon(painterResource(YouTubelyIcons.Tune), contentDescription = null) },
            supportingContent = {
                Text(stringResource(R.string.settings_sponsorblock_categories_count, settings.categories.size))
            },
        ) { Text(stringResource(R.string.settings_sponsorblock_categories)) }
        SegmentedListItem(
            onClick = { editingServer = true },
            enabled = settings.enabled,
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = SPONSOR_ROWS),
            colors = groupedListItemColors(),
            leadingContent = { Icon(painterResource(YouTubelyIcons.Language), contentDescription = null) },
            supportingContent = { Text(settings.serverUrl) },
        ) { Text(stringResource(R.string.settings_sponsorblock_server)) }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.settings_sponsorblock_categories)) },
            text = {
                Column {
                    SponsorBlockSettings.ALL_CATEGORIES.forEach { category ->
                        val checked = category in settings.categories
                        ListItem(
                            checked = checked,
                            onCheckedChange = { selected -> onChange { setCategory(category, selected) } },
                            leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                ),
                        ) { Text(stringResource(categoryName(category))) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.settings_done)) }
            },
        )
    }
    if (editingServer) {
        var text by remember { mutableStateOf(settings.serverUrl) }
        AlertDialog(
            onDismissRequest = { editingServer = false },
            title = { Text(stringResource(R.string.settings_sponsorblock_server)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
                    Text(stringResource(R.string.settings_sponsorblock_server_hint))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingServer = false
                        onChange { setServerUrl(text.takeIf { it.startsWith("https://") }.orEmpty()) }
                    },
                ) { Text(stringResource(R.string.settings_done)) }
            },
            dismissButton = {
                TextButton(onClick = { editingServer = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

private fun categoryName(category: String): Int =
    when (category) {
        "sponsor" -> R.string.settings_sb_sponsor
        "selfpromo" -> R.string.settings_sb_selfpromo
        "interaction" -> R.string.settings_sb_interaction
        "intro" -> R.string.settings_sb_intro
        "outro" -> R.string.settings_sb_outro
        "preview" -> R.string.settings_sb_preview
        "filler" -> R.string.settings_sb_filler
        else -> R.string.settings_sb_music_offtopic
    }
