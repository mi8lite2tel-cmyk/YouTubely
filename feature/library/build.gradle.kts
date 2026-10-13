// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.youtubely.android.feature)
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.datastore)
}
