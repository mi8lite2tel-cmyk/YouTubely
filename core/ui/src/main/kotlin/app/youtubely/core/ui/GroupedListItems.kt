// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.core.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Colours for rows of a segmented group. Every row of a group sits on the same container,
 * whether it is a switch that is on, off, or a plain row; the switch itself shows the state.
 * Without this, rows with a switch that is on get the "selected" tint and a group looks patchy.
 */
@Composable
fun groupedListItemColors(): ListItemColors {
    val colors = MaterialTheme.colorScheme
    return ListItemDefaults.segmentedColors(
        containerColor = colors.surfaceContainer,
        selectedContainerColor = colors.surfaceContainer,
        disabledContainerColor = colors.surfaceContainer,
        selectedContentColor = colors.onSurface,
        selectedLeadingContentColor = colors.onSurfaceVariant,
        selectedTrailingContentColor = colors.onSurfaceVariant,
        selectedSupportingContentColor = colors.onSurfaceVariant,
        selectedOverlineContentColor = colors.onSurfaceVariant,
    )
}
