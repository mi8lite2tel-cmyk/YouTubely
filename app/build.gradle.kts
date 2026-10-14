// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.youtubely.android.application)
    alias(libs.plugins.youtubely.android.compose)
    alias(libs.plugins.youtubely.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.youtubely"

    defaultConfig {
        applicationId = "app.youtubely"
        versionCode = 5
        versionName = "0.4.1"
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

    // The release key comes from GitHub Secrets in the release workflow (docs/releasing.md)
    // and never lives in the repository. Without it, release APKs are built unsigned.
    val keystoreFile = providers.environmentVariable("YOUTUBELY_KEYSTORE_FILE").orNull
    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = providers.environmentVariable("YOUTUBELY_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("YOUTUBELY_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("YOUTUBELY_KEY_PASSWORD").get()
                // v3 allows rotating the key later without breaking updates.
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
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
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.designsystem)
    implementation(projects.core.download)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.player)
    implementation(projects.core.provider)
    implementation(projects.core.sync)
    implementation(projects.core.ui)
    implementation(projects.feature.channel)
    implementation(projects.feature.downloads)
    implementation(projects.feature.home)
    implementation(projects.feature.library)
    implementation(projects.feature.playlist)
    implementation(projects.feature.search)
    implementation(projects.feature.settings)
    implementation(projects.feature.watch)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.json)
}
