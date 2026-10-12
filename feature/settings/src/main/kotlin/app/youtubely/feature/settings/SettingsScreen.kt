// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.designsystem.theme.isDynamicColorSupported
import app.youtubely.core.model.DarkMode
import app.youtubely.core.model.PlaybackSettings

private const val SOURCE_URL = "https://github.com/mi8lite2tel-cmyk/YouTubely"

@Composable
fun SettingsScreen(
    versionName: String,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val theme by viewModel.themeSettings.collectAsStateWithLifecycle()
    val playback by viewModel.playbackSettings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            header(R.string.settings_appearance)
            item { PalettePreview() }
            item { DarkModeSelector(theme.darkMode, viewModel::setDarkMode) }
            switches(
                listOf(
                    SwitchRow(
                        title = R.string.settings_dynamic_color,
                        summary =
                            if (isDynamicColorSupported) {
                                R.string.settings_dynamic_color_summary
                            } else {
                                R.string.settings_dynamic_color_unsupported
                            },
                        icon = YouTubelyIcons.Palette,
                        checked = theme.dynamicColor && isDynamicColorSupported,
                        enabled = isDynamicColorSupported,
                        onChange = viewModel::setDynamicColor,
                    ),
                    SwitchRow(
                        title = R.string.settings_pure_black,
                        summary = R.string.settings_pure_black_summary,
                        icon = YouTubelyIcons.DarkMode,
                        checked = theme.pureBlack,
                        enabled = true,
                        onChange = viewModel::setPureBlack,
                    ),
                    SwitchRow(
                        title = R.string.settings_high_contrast,
                        summary = R.string.settings_high_contrast_summary,
                        icon = YouTubelyIcons.Contrast,
                        checked = theme.highContrast,
                        enabled = !(theme.dynamicColor && isDynamicColorSupported),
                        onChange = viewModel::setHighContrast,
                    ),
                ),
            )

            header(R.string.settings_playback)
            switches(
                listOf(
                    SwitchRow(
                        title = R.string.settings_background,
                        summary = R.string.settings_background_summary,
                        icon = YouTubelyIcons.Music,
                        checked = playback.backgroundPlayback,
                        enabled = true,
                        onChange = viewModel::setBackgroundPlayback,
                    ),
                    SwitchRow(
                        title = R.string.settings_pip,
                        summary = R.string.settings_pip_summary,
                        icon = YouTubelyIcons.Video,
                        checked = playback.pictureInPicture,
                        enabled = true,
                        onChange = viewModel::setPictureInPicture,
                    ),
                ),
            )
            item {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    QualityLimitRow(
                        title = R.string.settings_max_quality_wifi,
                        value = playback.maxHeightUnmetered,
                        index = 0,
                        onChange = viewModel::setMaxHeightUnmetered,
                    )
                    QualityLimitRow(
                        title = R.string.settings_max_quality_mobile,
                        value = playback.maxHeightMetered,
                        index = 1,
                        onChange = viewModel::setMaxHeightMetered,
                    )
                }
            }

            header(R.string.settings_about)
            item {
                SegmentedListItem(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri())) },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                    leadingContent = { Icon(painterResource(YouTubelyIcons.Info), contentDescription = null) },
                    supportingContent = { Text(stringResource(R.string.settings_version, versionName)) },
                ) { Text(stringResource(R.string.settings_source)) }
            }
            item {
                SegmentedListItem(
                    onClick = {},
                    enabled = false,
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                    leadingContent = { Icon(painterResource(YouTubelyIcons.Language), contentDescription = null) },
                    supportingContent = { Text(stringResource(R.string.settings_license_summary)) },
                ) { Text(stringResource(R.string.settings_license)) }
            }
        }
    }
}

private fun LazyListScope.header(title: Int) {
    item(key = "header-$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        )
    }
}

private class SwitchRow(
    val title: Int,
    val summary: Int,
    val icon: Int,
    val checked: Boolean,
    val enabled: Boolean,
    val onChange: (Boolean) -> Unit,
)

private fun LazyListScope.switches(rows: List<SwitchRow>) {
    rows.forEachIndexed { index, row ->
        item(key = "switch-${row.title}") {
            SegmentedListItem(
                checked = row.checked,
                onCheckedChange = row.onChange,
                enabled = row.enabled,
                shapes = ListItemDefaults.segmentedShapes(index = index, count = rows.size),
                leadingContent = { Icon(painterResource(row.icon), contentDescription = null) },
                trailingContent = { Switch(checked = row.checked, onCheckedChange = null, enabled = row.enabled) },
                supportingContent = { Text(stringResource(row.summary)) },
            ) { Text(stringResource(row.title)) }
        }
    }
}

/** System / light / dark as a connected button group with shape-morphing toggle buttons. */
@Composable
private fun DarkModeSelector(
    selected: DarkMode,
    onSelect: (DarkMode) -> Unit,
) {
    val modes =
        listOf(
            Triple(DarkMode.System, R.string.settings_theme_system, YouTubelyIcons.BrightnessAuto),
            Triple(DarkMode.Light, R.string.settings_theme_light, YouTubelyIcons.LightMode),
            Triple(DarkMode.Dark, R.string.settings_theme_dark, YouTubelyIcons.DarkMode),
        )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        modes.forEachIndexed { index, (mode, label, icon) ->
            ToggleButton(
                checked = selected == mode,
                onCheckedChange = { onSelect(mode) },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                modifier = Modifier.weight(1f),
            ) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(label), Modifier.padding(start = 6.dp), maxLines = 1)
            }
        }
    }
}

/** Live preview of the current scheme; it cross-fades together with the rest of the app. */
@Composable
private fun PalettePreview() {
    val colors = MaterialTheme.colorScheme
    val swatches =
        listOf(
            colors.primary to MaterialShapes.Cookie9Sided,
            colors.secondary to MaterialShapes.Clover4Leaf,
            colors.tertiary to MaterialShapes.SoftBurst,
            colors.primaryContainer to MaterialShapes.Sunny,
            colors.tertiaryContainer to MaterialShapes.Pill,
        )
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(20.dp).height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            swatches.forEach { (color, shape) ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(48.dp).clip(shape.toShape()).background(color))
                }
            }
        }
    }
}

@Composable
private fun QualityLimitRow(
    title: Int,
    value: Int?,
    index: Int,
    onChange: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val noLimit = stringResource(R.string.settings_quality_no_limit)
    Box {
        SegmentedListItem(
            onClick = { expanded = true },
            shapes = ListItemDefaults.segmentedShapes(index = index, count = 2),
            leadingContent = { Icon(painterResource(YouTubelyIcons.Tune), contentDescription = null) },
            supportingContent = { Text(value?.let { "${it}p" } ?: noLimit) },
        ) { Text(stringResource(title)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (listOf<Int?>(null) + PlaybackSettings.HEIGHT_CHOICES).forEach { height ->
                DropdownMenuItem(
                    text = { Text(height?.let { "${it}p" } ?: noLimit) },
                    trailingIcon =
                        if (height == value) {
                            { Icon(painterResource(YouTubelyIcons.Check), contentDescription = null) }
                        } else {
                            null
                        },
                    onClick = {
                        onChange(height)
                        expanded = false
                    },
                )
            }
        }
    }
}
