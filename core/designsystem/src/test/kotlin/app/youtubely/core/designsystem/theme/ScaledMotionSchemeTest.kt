// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaledMotionSchemeTest {
    private val expressive = MotionScheme.expressive()

    @Test
    fun springsGetSofterButKeepTheirDamping() {
        val original = expressive.defaultSpatialSpec<Float>() as SpringSpec<Float>
        val scaled = ScaledMotionScheme(expressive, 2f).defaultSpatialSpec<Float>() as SpringSpec<Float>

        assertEquals(original.dampingRatio, scaled.dampingRatio, 0f)
        // Twice as long needs a quarter of the stiffness.
        assertEquals(original.stiffness / 4f, scaled.stiffness, 0.001f)
    }

    @Test
    fun aScaleOfOneChangesNothing() {
        val original = expressive.fastEffectsSpec<Float>() as SpringSpec<Float>
        val scaled = ScaledMotionScheme(expressive, 1f).fastEffectsSpec<Float>() as SpringSpec<Float>
        assertEquals(original.stiffness, scaled.stiffness, 0.001f)
    }

    @Test
    fun tweensAreStretched() {
        val fixed =
            object : MotionScheme {
                override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = tween(200, 50)

                override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = tween(100)

                override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = tween(400)

                override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(200)

                override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(100)

                override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(400)
            }
        val spec = ScaledMotionScheme(fixed, 1.5f).defaultSpatialSpec<Float>()
        assertTrue(spec is TweenSpec)
        assertEquals(300, (spec as TweenSpec).durationMillis)
        assertEquals(75, spec.delay)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aNonPositiveScaleIsRejected() {
        ScaledMotionScheme(expressive, 0f)
    }
}
