// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.youtubely.android.feature)
}

android {
    // Screenshot tests need the merged manifest (Compose test activity) and resources.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.provider)
    implementation(projects.core.player)
    implementation(projects.core.download)
    implementation(projects.core.datastore)
    implementation(projects.core.sponsorblock)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.ui.compose.material3)
    implementation(libs.androidx.activity.compose)

    testImplementation(projects.core.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
