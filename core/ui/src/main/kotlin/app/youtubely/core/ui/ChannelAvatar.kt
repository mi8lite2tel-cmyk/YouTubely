// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.youtubely.core.model.Image
import app.youtubely.core.model.bestFor
import coil3.compose.AsyncImage

/** Channel avatars use the expressive 9-sided cookie shape instead of a plain circle. */
@Composable
fun ChannelAvatar(
    avatars: List<Image>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val shape = MaterialShapes.Cookie9Sided.toShape()
    AsyncImage(
        model = avatars.bestFor(minWidth = 176)?.url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape),
    )
}
