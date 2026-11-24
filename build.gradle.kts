import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    `maven-publish`
    alias(libs.plugins.ktlint)
}

group = "uk.dominikdias.kmpflavors"
version = "0.1.0-SNAPSHOT"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

val functionalTest: SourceSet = sourceSets.create("functionalTest")

val testPluginClasspath: Configuration =
    configurations.create("testPluginClasspath") {
        isCanBeConsumed = false
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
            attribute(
                GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE,
                objects.named(GradleVersion.current().version),
            )
        }
    }

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.android.gradle.api)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.android.manifest.merger)
    compileOnly(libs.android.tools.common)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)

    "functionalTestImplementation"(platform(libs.junit.bom))
    "functionalTestImplementation"(libs.junit.jupiter)
    "functionalTestImplementation"(kotlin("test"))
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestRuntimeOnly"(libs.junit.platform.launcher)

    // Lets the TestKit builds apply the Kotlin plugin from the same classloader as this plugin.
    testPluginClasspath(libs.kotlin.gradle.plugin)
    testPluginClasspath(libs.android.gradle.plugin)
    testPluginClasspath(libs.compose.gradle.plugin)
    testPluginClasspath(libs.compose.compiler.gradle.plugin)
}

gradlePlugin {
    testSourceSets(functionalTest)
    plugins {
        register("kmpFlavorsSettings") {
            id = "uk.dominikdias.kmp-flavors.settings"
            implementationClass = "uk.dominikdias.kmpflavors.KmpFlavorsSettingsPlugin"
            displayName = "KMP Flavors (settings)"
            description = "Android-style flavor dimensions, product flavors and build types for Kotlin Multiplatform."
        }
        register("kmpFlavors") {
            id = "uk.dominikdias.kmp-flavors"
            implementationClass = "uk.dominikdias.kmpflavors.KmpFlavorsPlugin"
            displayName = "KMP Flavors (project)"
            description = "Wires the active KMP flavor variant into a module's source sets, dependencies and BuildConfig."
        }
    }
}

tasks {
    pluginUnderTestMetadata {
        pluginClasspath.from(testPluginClasspath)
    }
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
    withType<Test>().configureEach {
        useJUnitPlatform()
        maxParallelForks = 1
    }
    val functionalTestTask =
        register<Test>("functionalTest") {
            description = "Runs TestKit builds against real Kotlin Multiplatform projects."
            group = "verification"
            testClassesDirs = functionalTest.output.classesDirs
            classpath = functionalTest.runtimeClasspath
            maxHeapSize = "512m"
        }
    check { dependsOn(functionalTestTask) }
}

ktlint {
    version.set(
        libs.versions.ktlint.version
            .get(),
    )
    android.set(false)
    ignoreFailures.set(false)
}
