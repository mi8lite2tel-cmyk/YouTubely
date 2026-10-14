// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import app.youtubely.core.ui.groupedListItemColors

/** Title above a group of rows. */
@Composable
internal fun SectionHeader(
    title: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** Rows that belong together, drawn as one segmented block. */
@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap), content = content)
}

/** Position of a row in its group; decides which corners are rounded. */
internal data class RowPosition(
    val index: Int,
    val count: Int,
)

/** A setting that is on or off. */
@Composable
internal fun SwitchItem(
    title: Int,
    summary: Int?,
    icon: Int,
    checked: Boolean,
    position: RowPosition,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index = position.index, count = position.count),
        colors = groupedListItemColors(),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        supportingContent = summary?.let { { Text(stringResource(it)) } },
    ) { Text(stringResource(title)) }
}

/** One row that opens a menu of [options]; the current one is shown below the title and ticked in the menu. */
@Composable
internal fun <T> ChoiceRow(
    title: Int,
    icon: Int,
    options: List<Pair<T, String>>,
    selected: T,
    position: RowPosition,
    detail: String? = null,
    onChange: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SegmentedListItem(
            onClick = { expanded = true },
            shapes = ListItemDefaults.segmentedShapes(index = position.index, count = position.count),
            colors = groupedListItemColors(),
            leadingContent = { Icon(painterResource(icon), contentDescription = null) },
            trailingContent = { Icon(painterResource(YouTubelyIcons.ExpandMore), contentDescription = null) },
            supportingContent = {
                val value = options.firstOrNull { it.first == selected }?.second.orEmpty()
                Text(if (detail == null) value else "$value · $detail")
            },
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

/** An entry of the main settings page that opens a sub-page. */
@Composable
internal fun CategoryRow(
    title: Int,
    summary: Int,
    icon: Int,
    position: RowPosition,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = position.index, count = position.count),
        colors = groupedListItemColors(),
        leadingContent = {
            Surface(
                shape = MaterialShapes.Cookie6Sided.toShape(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
        },
        trailingContent = { Icon(painterResource(YouTubelyIcons.ChevronRight), contentDescription = null) },
        supportingContent = { Text(stringResource(summary), maxLines = 2) },
    ) { Text(stringResource(title), style = MaterialTheme.typography.titleMediumEmphasized) }
}
