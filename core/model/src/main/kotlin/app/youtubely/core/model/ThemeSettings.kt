// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

enum class DarkMode {
    System,
    Light,
    Dark,
}

/** How long animations take, as a multiple of Material's expressive timing. */
enum class AnimationSpeed(
    val durationScale: Float,
) {
    Standard(1f),
    Relaxed(1.5f),
    Slow(2f),
    VerySlow(3f),
}

data class ThemeSettings(
    val darkMode: DarkMode = DarkMode.System,
    /** Wallpaper-based colors on Android 12+. */
    val dynamicColor: Boolean = true,
    /** True black backgrounds in dark mode (AMOLED). */
    val pureBlack: Boolean = false,
    val highContrast: Boolean = false,
    /** Motion is calmer by default: the stock timing feels hurried to many people. */
    val animationSpeed: AnimationSpeed = AnimationSpeed.Relaxed,
)
