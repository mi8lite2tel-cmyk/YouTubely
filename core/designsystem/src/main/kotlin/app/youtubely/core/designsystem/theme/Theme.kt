// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Root theme of the app: Material 3 Expressive with the expressive motion scheme.
 *
 * @param darkTheme dark or light color scheme.
 * @param dynamicColor use the wallpaper-based palette on Android 12+. When unavailable,
 *   the scheme generated from the brand seed (see `tools/color`) is used.
 * @param pureBlack AMOLED mode: replaces dark backgrounds and surfaces with true black.
 * @param highContrast high-contrast variant of the seed scheme. Dynamic schemes on Android 14+
 *   already follow the system contrast setting, so this flag only affects the seed scheme.
 */
@Composable
fun YouTubelyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme =
        remember(context, darkTheme, useDynamic, pureBlack, highContrast) {
            val base =
                when {
                    useDynamic && darkTheme -> dynamicDarkColorScheme(context)
                    useDynamic -> dynamicLightColorScheme(context)
                    darkTheme && highContrast -> seedDarkHighContrastColorScheme
                    darkTheme -> seedDarkColorScheme
                    highContrast -> seedLightHighContrastColorScheme
                    else -> seedLightColorScheme
                }
            if (darkTheme && pureBlack) base.toPureBlack() else base
        }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/**
 * AMOLED variant: the lowest surfaces become black, the container hierarchy is kept
 * (containers stay distinguishable from the background).
 */
internal fun ColorScheme.toPureBlack(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
    )
