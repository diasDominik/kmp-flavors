pluginManagement {
    // The kmp-flavors plugin from this repository; a real project uses a published version instead.
    includeBuild("..")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("uk.dominikdias.kmp-flavors.settings")
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// One flavor model for every module and platform: Android, iOS, desktop and web.
kmpFlavors {
    flavorDimensions("store")

    productFlavors {
        // Google Play: may use Google's closed-source SDKs (ML Kit).
        register("play") {
            dimension = "store"
            isDefault = true
        }
        // F-Droid and other FOSS channels: open-source dependencies only.
        register("oss") {
            dimension = "store"
            applicationIdSuffix = ".oss" // installable next to the play build
        }
    }
    // Build types: Android's debug and release.
}

rootProject.name = "kmp-flavors-showcase"

include(":shared", ":androidApp", ":desktopApp", ":webApp")
