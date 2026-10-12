// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.youtubely.core.designsystem.R

/**
 * Roboto Flex (OFL) as one variable font. Each weight is a named instance of the `wght` axis,
 * so emphasized styles get real heavier glyphs instead of synthetic bold.
 */
private val RobotoFlex =
    FontFamily(
        listOf(300, 400, 500, 600, 700, 800).map { weight ->
            Font(
                resId = R.font.roboto_flex,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        },
    )

private fun TextStyle.flex() = copy(fontFamily = RobotoFlex)

private val base = Typography()

internal val YouTubelyTypography =
    Typography(
        displayLarge = base.displayLarge.flex(),
        displayMedium = base.displayMedium.flex(),
        displaySmall = base.displaySmall.flex(),
        headlineLarge = base.headlineLarge.flex(),
        headlineMedium = base.headlineMedium.flex(),
        headlineSmall = base.headlineSmall.flex(),
        titleLarge = base.titleLarge.flex(),
        titleMedium = base.titleMedium.flex(),
        titleSmall = base.titleSmall.flex(),
        bodyLarge = base.bodyLarge.flex(),
        bodyMedium = base.bodyMedium.flex(),
        bodySmall = base.bodySmall.flex(),
        labelLarge = base.labelLarge.flex(),
        labelMedium = base.labelMedium.flex(),
        labelSmall = base.labelSmall.flex(),
        displayLargeEmphasized = base.displayLargeEmphasized.flex(),
        displayMediumEmphasized = base.displayMediumEmphasized.flex(),
        displaySmallEmphasized = base.displaySmallEmphasized.flex(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.flex(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.flex(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.flex(),
        titleLargeEmphasized = base.titleLargeEmphasized.flex(),
        titleMediumEmphasized = base.titleMediumEmphasized.flex(),
        titleSmallEmphasized = base.titleSmallEmphasized.flex(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.flex(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.flex(),
        bodySmallEmphasized = base.bodySmallEmphasized.flex(),
        labelLargeEmphasized = base.labelLargeEmphasized.flex(),
        labelMediumEmphasized = base.labelMediumEmphasized.flex(),
        labelSmallEmphasized = base.labelSmallEmphasized.flex(),
    )
