// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
import app.youtubely.buildlogic.JAVA_TARGET
import app.youtubely.buildlogic.configureKotlinCompilerOptions
import app.youtubely.buildlogic.configureQuality
import app.youtubely.buildlogic.libs
import app.youtubely.buildlogic.library
import app.youtubely.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Pure Kotlin/JVM module without Android dependencies (models, interfaces, pure logic). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-jvm"))
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JAVA_TARGET
            targetCompatibility = JAVA_TARGET
        }
        configureKotlinCompilerOptions()
        dependencies {
            add("testImplementation", libs.library("kotlin-test"))
        }
        tasks.named("test", org.gradle.api.tasks.testing.Test::class.java) {
            useJUnitPlatform()
        }
        configureQuality()
    }
}
