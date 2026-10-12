// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
import app.youtubely.buildlogic.configureKotlinAndroid
import app.youtubely.buildlogic.configureQuality
import app.youtubely.buildlogic.libs
import app.youtubely.buildlogic.pluginId
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-library"))
        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            // Namespace follows the module path: :core:designsystem -> app.youtubely.core.designsystem
            namespace = "app.youtubely" + path.replace(':', '.').replace('-', '_')
            testOptions.animationsDisabled = true
        }
        configureQuality()
    }
}
