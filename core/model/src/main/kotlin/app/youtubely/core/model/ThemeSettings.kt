// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

enum class DarkMode {
    System,
    Light,
    Dark,
}

data class ThemeSettings(
    val darkMode: DarkMode = DarkMode.System,
    /** Wallpaper-based colors on Android 12+. */
    val dynamicColor: Boolean = true,
    /** True black backgrounds in dark mode (AMOLED). */
    val pureBlack: Boolean = false,
    val highContrast: Boolean = false,
)
