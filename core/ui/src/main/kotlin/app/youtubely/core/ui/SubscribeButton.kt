// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.youtubely.core.designsystem.icon.YouTubelyIcons

/**
 * Local subscription toggle. The expressive toggle button morphs from a round to a square shape
 * when checked and swaps its label with the motion scheme.
 */
@Composable
fun SubscribeButton(
    subscribed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    FilledTonalToggleButton(
        checked = subscribed,
        onCheckedChange = { onClick() },
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = subscribed,
            transitionSpec = { fadeIn(motion.fastEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec())) },
            label = "subscribe",
        ) { checked ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (checked) YouTubelyIcons.Subscribed else YouTubelyIcons.Subscribe),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(if (checked) R.string.subscribed else R.string.subscribe))
            }
        }
    }
}
