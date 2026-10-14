// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.youtubely.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

/**
 * Plays every animation of [base] [durationScale] times slower (2 = twice as long, 1 = unchanged).
 * Springs keep their damping and get a softer stiffness: a spring's period grows with the square
 * root of its inverse stiffness, so the stiffness is divided by the scale squared. Tweens are
 * simply stretched. Because Material's components read the scheme from the theme, one setting
 * slows the whole app.
 */
class ScaledMotionScheme(
    private val base: MotionScheme,
    private val durationScale: Float,
) : MotionScheme {
    init {
        require(durationScale > 0f) { "durationScale must be positive, was $durationScale" }
    }

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = base.defaultSpatialSpec<T>().scaled()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = base.fastSpatialSpec<T>().scaled()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = base.slowSpatialSpec<T>().scaled()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = base.defaultEffectsSpec<T>().scaled()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = base.fastEffectsSpec<T>().scaled()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = base.slowEffectsSpec<T>().scaled()

    private fun <T> FiniteAnimationSpec<T>.scaled(): FiniteAnimationSpec<T> =
        when (this) {
            is SpringSpec<T> -> {
                spring(
                    dampingRatio = dampingRatio,
                    stiffness = stiffness / (durationScale * durationScale),
                    visibilityThreshold = visibilityThreshold,
                )
            }

            is TweenSpec<T> -> {
                tween((durationMillis * durationScale).toInt(), (delay * durationScale).toInt(), easing)
            }

            else -> {
                this
            }
        }
}
