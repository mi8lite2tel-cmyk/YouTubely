// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.youtubely.android.application)
    alias(libs.plugins.youtubely.android.compose)
    alias(libs.plugins.youtubely.android.hilt)
}

android {
    namespace = "app.youtubely"

    defaultConfig {
        applicationId = "app.youtubely"
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "distribution"
    productFlavors {
        // F-Droid compatible: no Google Play Services, no proprietary code.
        create("foss") {
            dimension = "distribution"
        }
        // Same app plus Google Cast. Cast is not added yet, see docs/adr/0001-license.md.
        create("full") {
            dimension = "distribution"
            versionNameSuffix = "-full"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // The dependency metadata block is signed with a Google key and breaks reproducible builds;
    // F-Droid rejects APKs that contain it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.provider)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
