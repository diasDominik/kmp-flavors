import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.intellij.platform")
    alias(libs.plugins.ktlint)
}

group = "uk.dominikdias.kmpflavors"
version = "0.1.0-SNAPSHOT"

kotlin {
    // The minimum IDE (2026.2) runs on Java 25.
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        // No bridges for the platform's Kotlin interfaces' default methods (several are deprecated).
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
    }
}

ktlint { version = libs.versions.ktlint.version }

dependencies {
    intellijPlatform {
        val local = providers.gradleProperty("ide.localPath")
        if (local.isPresent) local(local) else intellijIdea(providers.gradleProperty("ide.version"))
        bundledPlugin("com.intellij.gradle")
    }
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false
    pluginVerification {
        ides {
            val local = providers.gradleProperty("ide.localPath")
            if (local.isPresent) {
                local(
                    file(local),
                )
            } else {
                create(
                    org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdea,
                    providers.gradleProperty("ide.version"),
                )
            }
        }
    }
}

tasks.test { useJUnitPlatform() }
