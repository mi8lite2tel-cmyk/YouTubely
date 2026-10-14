// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.youtubely.android.library)
    alias(libs.plugins.youtubely.android.hilt)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.download)
    implementation(projects.core.provider)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
}
