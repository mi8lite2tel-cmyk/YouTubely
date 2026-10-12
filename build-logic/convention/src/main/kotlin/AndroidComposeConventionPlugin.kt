// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
import app.youtubely.buildlogic.libs
import app.youtubely.buildlogic.library
import app.youtubely.buildlogic.pluginId
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/** Enables Compose for an Android module. Apply after the application/library convention. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-compose"))
        extensions.getByType<CommonExtension>().buildFeatures.compose = true

        dependencies {
            val bom = libs.library("androidx-compose-bom")
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))
            add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
            add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
        }

        // Compiler metrics/reports for stability audits: ./gradlew assembleRelease -PcomposeCompilerReports=true
        extensions.configure(ComposeCompilerGradlePluginExtension::class.java) {
            val reportsEnabled = providers.gradleProperty("composeCompilerReports").map(String::toBoolean).orElse(false)
            val reportsDir = layout.buildDirectory.dir("compose-compiler")
            metricsDestination.set(reportsDir.zip(reportsEnabled) { dir, on -> if (on) dir else null })
            reportsDestination.set(reportsDir.zip(reportsEnabled) { dir, on -> if (on) dir else null })
            stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("config/compose/stability.conf"))
        }
    }
}
