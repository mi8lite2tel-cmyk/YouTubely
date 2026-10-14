// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven("https://jitpack.io") {
            content { includeGroup("com.github.TeamNewPipe") }
        }
    }
}

rootProject.name = "YouTubely"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:download")
include(":core:model")
include(":core:network")
include(":core:player")
include(":core:provider")
include(":core:sponsorblock")
include(":core:testing")
include(":core:sync")
include(":core:ui")
include(":feature:actions")
include(":feature:channel")
include(":feature:downloads")
include(":feature:home")
include(":feature:library")
include(":feature:playlist")
include(":feature:search")
include(":feature:settings")
include(":feature:transfer")
include(":feature:watch")
