// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.youtubely.android.feature)
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.provider)
    implementation(projects.core.player)
    implementation(projects.core.download)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.ui.compose.material3)
    implementation(libs.androidx.activity.compose)
}
