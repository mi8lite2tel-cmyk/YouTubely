// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.youtubely.android.library)
    alias(libs.plugins.youtubely.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(projects.core.provider)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
