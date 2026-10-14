// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.youtubely.android.library)
    alias(libs.plugins.youtubely.android.hilt)
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.session)
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.provider)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.okhttp)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.media3.exoplayer.dash)
}
