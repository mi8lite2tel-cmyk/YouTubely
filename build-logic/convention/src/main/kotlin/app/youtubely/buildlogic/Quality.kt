// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

private const val LICENSE_HEADER = """// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later"""

/** Spotless (ktlint + SPDX header) and Detekt for every module. */
internal fun Project.configureQuality() {
    pluginManager.apply(libs.pluginId("spotless"))
    pluginManager.apply(libs.pluginId("detekt"))

    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**")
            ktlint(libs.version("ktlint"))
            licenseHeader(LICENSE_HEADER)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.version("ktlint"))
            licenseHeader(LICENSE_HEADER, "(plugins|import|android|dependencies)")
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        source.setFrom("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin")
    }
    tasks.withType(Detekt::class.java).configureEach {
        jvmTarget = KOTLIN_JVM_TARGET.target
        reports {
            html.required.set(true)
            sarif.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            md.required.set(false)
        }
    }
}
