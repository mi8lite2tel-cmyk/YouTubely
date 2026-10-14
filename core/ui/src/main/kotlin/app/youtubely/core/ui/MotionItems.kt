// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.core.ui

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Animates an item of a lazy list when it appears, disappears or moves, with the timing of the
 * theme's motion scheme. Plain `animateItem()` uses fixed springs and ignores the animation
 * speed the user chose.
 */
@Composable
fun LazyItemScope.motionItem(): Modifier {
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec(),
        fadeOutSpec = motion.fastEffectsSpec(),
    )
}

/** The same for an item of a lazy grid. */
@Composable
fun LazyGridItemScope.motionItem(): Modifier {
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec(),
        fadeOutSpec = motion.fastEffectsSpec(),
    )
}
