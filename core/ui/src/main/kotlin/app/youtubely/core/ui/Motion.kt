// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Cross-fades between the states of a screen (loading, error, content), with the timing of the
 * theme's motion scheme, so the content never pops in.
 */
@Composable
fun <T> StateTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "state",
    content: @Composable (T) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
        },
        label = label,
    ) { content(it) }
}
