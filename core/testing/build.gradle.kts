// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.youtubely.android.library)
    alias(libs.plugins.youtubely.android.compose)
}

// Test support only: renders Compose screens to PNG files on the JVM (Robolectric native graphics),
// so a design can be looked at without a device or emulator. Used from `testImplementation`.
dependencies {
    api(projects.core.designsystem)
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.androidx.compose.ui.test.manifest)
    api(libs.robolectric)
    api(libs.junit4)
}
