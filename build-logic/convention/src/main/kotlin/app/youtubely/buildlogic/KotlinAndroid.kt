// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Bytecode level for every module. Building itself runs on JDK 21 (see CI). */
internal val JAVA_TARGET = JavaVersion.VERSION_17
internal val KOTLIN_JVM_TARGET = JvmTarget.JVM_17

/**
 * Shared Android setup for application and library modules. Kotlin is compiled by AGP's
 * built-in Kotlin support (AGP 9+), so the `org.jetbrains.kotlin.android` plugin is not applied.
 */
internal fun Project.configureKotlinAndroid(android: CommonExtension) {
    android.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig.minSdk = libs.version("minSdk").toInt()
        compileOptions.sourceCompatibility = JAVA_TARGET
        compileOptions.targetCompatibility = JAVA_TARGET
        // NewPipeExtractor relies on java.time and java.nio APIs that need desugaring below API 26/33.
        compileOptions.isCoreLibraryDesugaringEnabled = true
        lint.apply {
            abortOnError = true
            warningsAsErrors = false
            checkDependencies = true
        }
    }
    dependencies {
        add("coreLibraryDesugaring", libs.library("android-desugarJdkLibs"))
    }
    configureKotlinCompilerOptions()
}

internal fun Project.configureKotlinCompilerOptions(extra: KotlinJvmCompilerOptions.() -> Unit = {}) {
    tasks.withType(KotlinJvmCompile::class.java).configureEach {
        compilerOptions {
            jvmTarget.set(KOTLIN_JVM_TARGET)
            allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false))
            freeCompilerArgs.addAll(
                "-Xconsistent-data-class-copy-visibility",
            )
            extra()
        }
    }
}
