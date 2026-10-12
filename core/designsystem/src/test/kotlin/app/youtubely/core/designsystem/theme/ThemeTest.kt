// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun pureBlackKeepsContainersDistinct() {
        val scheme = seedDarkColorScheme.toPureBlack()
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
        assertNotEquals(Color.Black, scheme.surfaceContainer)
        assertEquals(seedDarkColorScheme.primary, scheme.primary)
    }

    @Test
    fun seedSchemesHaveExpectedLightness() {
        assertTrue(seedLightColorScheme.background.luminance() > 0.5f)
        assertTrue(seedDarkColorScheme.background.luminance() < 0.5f)
        assertTrue(seedLightHighContrastColorScheme.background.luminance() > 0.5f)
        assertTrue(seedDarkHighContrastColorScheme.background.luminance() < 0.5f)
    }

    @Test
    fun highContrastIncreasesPrimaryContrast() {
        fun contrast(
            a: Color,
            b: Color,
        ): Float {
            val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
            return (hi + 0.05f) / (lo + 0.05f)
        }
        val normal = contrast(seedLightColorScheme.primary, seedLightColorScheme.surface)
        val high = contrast(seedLightHighContrastColorScheme.primary, seedLightHighContrastColorScheme.surface)
        assertTrue("normal=$normal high=$high", high > normal)
    }
}
