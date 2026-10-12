// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
import app.youtubely.buildlogic.configureKotlinAndroid
import app.youtubely.buildlogic.configureQuality
import app.youtubely.buildlogic.libs
import app.youtubely.buildlogic.pluginId
import app.youtubely.buildlogic.version
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-application"))
        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig.targetSdk = libs.version("targetSdk").toInt()
            testOptions.animationsDisabled = true
        }
        configureQuality()
    }
}
