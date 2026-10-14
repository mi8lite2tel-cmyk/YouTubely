// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.feature.watch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.youtubely.core.designsystem.icon.YouTubelyIcons
import java.util.Locale
import kotlin.math.roundToInt

internal const val MIN_SPEED = 0.25f
internal const val MAX_SPEED = 4f
private const val SPEED_STEP = 0.05f
private const val PRESETS_PER_ROW = 4
private val SpeedPresets = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/**
 * Speed: the value large in the middle with − / + on the sides, a plain slider (no tick marks)
 * in 0.05 steps and the common presets as two connected button groups.
 */
@Composable
internal fun SpeedPage(
    speed: Float,
    onSpeed: (Float) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val slider = remember { SliderState(value = speed, trackRange = MIN_SPEED..MAX_SPEED) }
    LaunchedEffect(speed) { if (slider.value != speed) slider.value = speed }

    fun set(value: Float) = onSpeed(((value / SPEED_STEP).roundToInt() * SPEED_STEP).coerceIn(MIN_SPEED, MAX_SPEED))

    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { set(speed - SPEED_STEP * 5) },
                enabled = speed > MIN_SPEED,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) { Icon(painterResource(YouTubelyIcons.Remove), stringResource(R.string.watch_speed_slower)) }
            AnimatedContent(
                targetState = speed,
                transitionSpec = { fadeIn(motion.fastEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec())) },
                label = "speed",
                modifier = Modifier.weight(1f),
            ) { value ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        formatSpeed(value),
                        style = MaterialTheme.typography.displaySmallEmphasized,
                        textAlign = TextAlign.Center,
                    )
                    if (value == 1f) {
                        Text(
                            stringResource(R.string.watch_speed_normal),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            FilledTonalIconButton(
                onClick = { set(speed + SPEED_STEP * 5) },
                enabled = speed < MAX_SPEED,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) { Icon(painterResource(YouTubelyIcons.Add), stringResource(R.string.watch_speed_faster)) }
        }
        Slider(state = slider, onValueChange = ::set, modifier = Modifier.fillMaxWidth())
        SpeedPresets.chunked(PRESETS_PER_ROW).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                row.forEachIndexed { index, preset ->
                    ToggleButton(
                        checked = speed == preset,
                        onCheckedChange = { onSpeed(preset) },
                        shapes =
                            when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                row.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        modifier = Modifier.weight(1f),
                    ) { Text(formatSpeed(preset), maxLines = 1, softWrap = false) }
                }
            }
        }
        Text(
            stringResource(R.string.watch_speed_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 1×, 1.5×, 0.75×: no trailing zeros. */
internal fun formatSpeed(speed: Float): String {
    val text =
        if (speed == speed.toInt().toFloat()) {
            speed.toInt().toString()
        } else {
            "%.2f".format(Locale.ROOT, speed).trimEnd('0')
        }
    return "$text×"
}
