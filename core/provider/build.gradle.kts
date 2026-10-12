// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.youtubely.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
}

// Tests against the real YouTube are opt-in: ./gradlew :core:provider:test -Plive=true
tasks.test {
    val live = providers.gradleProperty("live").map(String::toBoolean).orElse(false)
    inputs.property("live", live)
    systemProperty("youtubely.live", live.get())
    if (live.get()) outputs.upToDateWhen { false }
}
