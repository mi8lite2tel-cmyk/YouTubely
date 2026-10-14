// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.youtubely.jvm.library)
}

dependencies {
    api(projects.core.model)
    api(libs.okhttp)
    api(libs.newpipe.extractor)

    testImplementation(libs.okhttp.mockwebserver)
}
