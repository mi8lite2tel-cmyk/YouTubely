// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

private val UnselectedCorner = 8.dp
private val SelectedCorner = 20.dp

/**
 * Filter chip whose corners morph from rounded rectangle to pill when selected, driven by the
 * theme's spatial motion spec, with an optional icon that swaps to a check mark.
 */
@Composable
fun ExpressiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
) {
    val corner by animateDpAsState(
        targetValue = if (selected) SelectedCorner else UnselectedCorner,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "chipCorner",
    )
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            val typography = MaterialTheme.typography
            Text(label, style = if (selected) typography.labelLargeEmphasized else typography.labelLarge)
        },
        leadingIcon =
            icon?.let {
                {
                    Icon(
                        painter = painterResource(it),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                }
            },
        shape = RoundedCornerShape(corner),
        modifier = modifier,
    )
}
