// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.youtubely.android.library)
    alias(libs.plugins.youtubely.android.compose)
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    implementation(projects.core.provider)
    api(libs.coil.compose)
    api(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.collections.immutable)

    testImplementation(libs.junit4)
}
