package uk.dominikdias.kmpflavors

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KmpFlavorsPluginFunctionalTest {
    @TempDir
    lateinit var dir: File

    private fun file(
        path: String,
        content: String,
    ) = dir.resolve(path).apply {
        parentFile.mkdirs()
        writeText(content.trimIndent())
    }

    private fun runner(vararg args: String) =
        GradleRunner
            .create()
            .withProjectDir(dir)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace", "--configuration-cache")
            .forwardOutput()

    private fun kmpProject() {
        file(
            "gradle.properties",
            """
            org.gradle.jvmargs=-Xmx1g
            kotlin.compiler.execution.strategy=in-process
            kotlin.native.ignoreDisabledTargets=true
            """,
        )
        file(
            "settings.gradle.kts",
            """
            plugins { id("uk.dominikdias.kmp-flavors.settings") }

            dependencyResolutionManagement { repositories { mavenCentral() } }

            kmpFlavors {
                flavorDimensions("tier", "store")
                productFlavors {
                    register("free") { dimension = "tier"; isDefault = true }
                    register("paid") { dimension = "tier"; extras["label"] = "Paid" }
                    register("play") { dimension = "store" }
                    register("foss") { dimension = "store" }
                }
                buildTypes {
                    register("staging") { initWith(getByName("release")); isDebuggable = true }
                }
                variantFilter { ignore = flavors["tier"] == "paid" && flavors["store"] == "foss" }
            }

            rootProject.name = "sample"
            include(":lib", ":app")
            """,
        )
        file("build.gradle.kts", "")
        file(
            "lib/build.gradle.kts",
            """
            plugins { kotlin("multiplatform") }
            kotlin { jvm() }
            """,
        )
        file("lib/src/commonMain/kotlin/lib/Billing.kt", "package lib\n\nobject Billing { fun provider() = \"billing\" }")
        file(
            "app/build.gradle.kts",
            """
            plugins { kotlin("multiplatform") }
            kotlin { jvm() }

            kmpFlavors {
                buildConfig {
                    packageName = "sample.app"
                    buildConfigField("BASE_URL", "https://prod.example")
                    buildConfigField("RETRIES", 3)
                    productFlavor("paid") { buildConfigField("ADS", false) }
                    productFlavor("free") { buildConfigField("ADS", true) }
                    buildType("debug") { buildConfigField("BASE_URL", "https://dev.example") }
                }
            }

            dependencies {
                "commonPaidMainImplementation"(project(":lib"))
            }

            listOf("FreePlayDebug", "PaidPlayRelease").forEach { name ->
                tasks.register("print" + name) {
                    val variant = kmpFlavors.variant.name
                    val label = kmpFlavors.extras["label"] ?: "none"
                    doLast { println("variant=" + variant + " label=" + label) }
                }
            }
            """,
        )
        file(
            "app/src/commonMain/kotlin/sample/app/App.kt",
            "package sample.app\n\nfun describe() = tier() + \"/\" + store() + \"/\" + BuildConfig.VARIANT",
        )
        file("app/src/commonFreeMain/kotlin/sample/app/Tier.kt", "package sample.app\n\nfun tier() = \"free\"")
        file("app/src/commonPaidMain/kotlin/sample/app/Tier.kt", "package sample.app\n\nfun tier() = \"paid:\" + lib.Billing.provider()")
        file("app/src/commonPlayMain/kotlin/sample/app/Store.kt", "package sample.app\n\nfun store() = \"play\"")
        file("app/src/commonFossMain/kotlin/sample/app/Store.kt", "package sample.app\n\nfun store() = \"foss\"")
        file("app/src/jvmDebugMain/kotlin/sample/app/Logging.kt", "package sample.app\n\nfun logging() = true")
        file("app/src/commonPaidPlayReleaseMain/kotlin/sample/app/Only.kt", "package sample.app\n\nfun onlyPaidPlayRelease() = 1")
    }

    private fun buildConfig() = dir.resolve("app/build/generated/kmpFlavors/buildConfig/sample/app/BuildConfig.kt").readText()

    @Test
    fun `default variant compiles only its own flavor sources`() {
        kmpProject()
        runner(":app:compileKotlinJvm").build()

        val config = buildConfig()
        assertContains(config, "const val VARIANT: String = \"freeFossDebug\"")
        assertContains(config, "const val BASE_URL: String = \"https://dev.example\"")
        assertContains(config, "const val ADS: Boolean = true")
        assertContains(config, "const val RETRIES: Int = 3")
        assertContains(config, "const val DEBUG: Boolean = true")
        assertContains(config, "const val FLAVOR_STORE: String = \"foss\"")
        val classes = dir.resolve("app/build/classes/kotlin/jvm/main/sample/app")
        assertTrue(classes.resolve("LoggingKt.class").exists())
        assertFalse(classes.resolve("OnlyKt.class").exists())
    }

    @Test
    fun `selected variant brings its sources dependencies and config`() {
        kmpProject()
        runner(":app:compileKotlinJvm", "-Pkmp.variant=paidPlayRelease").build()

        val config = buildConfig()
        assertContains(config, "const val VARIANT: String = \"paidPlayRelease\"")
        assertContains(config, "const val BASE_URL: String = \"https://prod.example\"")
        assertContains(config, "const val ADS: Boolean = false")
        assertContains(config, "const val DEBUG: Boolean = false")
        val classes = dir.resolve("app/build/classes/kotlin/jvm/main/sample/app")
        assertTrue(classes.resolve("OnlyKt.class").exists())
        assertFalse(classes.resolve("LoggingKt.class").exists())
    }

    @Test
    fun `per dimension properties and initWith build types`() {
        kmpProject()
        runner(":app:compileKotlinJvm", "-Pkmp.flavor.store=foss", "-Pkmp.buildType=staging").build()

        val config = buildConfig()
        assertContains(config, "const val VARIANT: String = \"freeFossStaging\"")
        assertContains(config, "const val DEBUG: Boolean = true")
    }

    @Test
    fun `requested task names select the variant and the configuration cache is reused`() {
        kmpProject()
        val first = runner(":app:printPaidPlayRelease").build()
        assertContains(first.output, "variant=paidPlayRelease label=Paid")
        val second = runner(":app:printPaidPlayRelease").build()
        assertContains(second.output, "Reusing configuration cache.")
        assertContains(runner(":app:printFreePlayDebug").build().output, "variant=freePlayDebug label=none")
    }

    @Test
    fun `ignored variants and mixed variants fail with a clear message`() {
        kmpProject()
        assertContains(
            runner("help", "-Pkmp.variant=paidFossDebug").buildAndFail().output,
            "Variant \"paidFossDebug\" is ignored by variantFilter",
        )
        assertContains(
            runner(":app:printFreePlayDebug", ":app:printPaidPlayRelease").buildAndFail().output,
            "one variant per build",
        )
    }

    @Test
    fun `ide selection in local properties is used and the model file is written for the ide`() {
        kmpProject()
        file("local.properties", "sdk.dir=/nowhere\nkmp.variant=paidPlayRelease\n")
        runner(":app:generateKmpFlavorsBuildConfig").build()

        assertContains(buildConfig(), "const val VARIANT: String = \"paidPlayRelease\"")
        val model = java.util.Properties().apply { load(dir.resolve(".gradle/kmp-flavors/model.properties").reader()) }
        assertEquals("paidPlayRelease", model.getProperty("active"))
        assertEquals("tier,store", model.getProperty("dimensions"))
        assertEquals("free,paid", model.getProperty("dimension.tier"))
        assertFalse("paidFossDebug" in model.getProperty("variants"))
        assertEquals("tier:paid,store:play", model.getProperty("variant.paidPlayRelease.flavors"))
        assertEquals("play,paid,paidPlay,release,paidPlayRelease", model.getProperty("variant.paidPlayRelease.components"))

        // Explicit choices for a single build still win, and a stale selection is ignored with a warning.
        file("local.properties", "kmp.variant=goneVariant\n")
        val output = runner(":app:generateKmpFlavorsBuildConfig").build().output
        assertContains(output, "ignoring kmp.variant=goneVariant")
        assertContains(buildConfig(), "freeFossDebug")
    }

    @Test
    fun `kmpVariants lists variants and marks the active one`() {
        kmpProject()
        // paid + the default store (foss) is ignored, so the closest enabled variant is chosen.
        val output = runner("kmpVariants", "-Pkmp.flavor.tier=paid").build().output
        assertContains(output, "* paidPlayDebug")
        assertContains(output, "  freeFossStaging")
        assertFalse("paidFoss" in output)
    }

    @Test
    fun `buildConfig is regenerated when the variant changes`() {
        kmpProject()
        val first = runner(":app:generateKmpFlavorsBuildConfig").build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":app:generateKmpFlavorsBuildConfig")?.outcome)
        val second = runner(":app:generateKmpFlavorsBuildConfig", "-Pkmp.variant=freePlayRelease").build()
        assertEquals(TaskOutcome.SUCCESS, second.task(":app:generateKmpFlavorsBuildConfig")?.outcome)
        assertContains(buildConfig(), "freePlayRelease")
    }

    @Test
    fun `android modules receive the shared flavors`() {
        file("gradle.properties", "org.gradle.jvmargs=-Xmx1536m\nandroid.useAndroidX=true")
        file("local.properties", "sdk.dir=" + (System.getenv("ANDROID_HOME") ?: System.getProperty("user.home") + "/Library/Android/sdk"))
        file(
            "settings.gradle.kts",
            """
            plugins { id("uk.dominikdias.kmp-flavors.settings") }
            dependencyResolutionManagement { repositories { google(); mavenCentral() } }
            kmpFlavors {
                flavorDimensions("tier")
                productFlavors {
                    register("free") { applicationIdSuffix = ".free"; isDefault = true }
                    register("paid") { applicationIdSuffix = ".paid"; versionNameSuffix = "-paid" }
                }
                buildTypes { getByName("debug") { applicationIdSuffix = ".debug" } }
                variantFilter { ignore = name == "paidDebug" }
            }
            include(":androidApp")
            """,
        )
        file("build.gradle.kts", "")
        file(
            "androidApp/build.gradle.kts",
            """
            plugins { id("com.android.application") }
            android {
                namespace = "sample.android"
                compileSdk = 36
                defaultConfig { applicationId = "sample.android"; minSdk = 26 }
            }
            val androidVariants = mutableListOf<String>()
            androidComponents { onVariants { androidVariants += it.name } }
            tasks.register("printAndroid") {
                val flavors = android.productFlavors.map { it.name + ":" + it.dimension + ":" + it.applicationIdSuffix }
                val debugSuffix = android.buildTypes.getByName("debug").applicationIdSuffix
                val variants = androidVariants.sorted()
                doLast { println("flavors=" + flavors + " debugSuffix=" + debugSuffix + " variants=" + variants) }
            }
            """,
        )
        val output = runner(":androidApp:printAndroid").build().output
        assertContains(output, "flavors=[free:tier:.free, paid:tier:.paid]")
        assertContains(output, "debugSuffix=.debug")
        assertContains(output, "variants=[freeDebug, freeRelease, paidRelease]")
    }

    @Test
    fun `android builds of a non active variant fail fast`() {
        file("gradle.properties", "org.gradle.jvmargs=-Xmx1536m\nandroid.useAndroidX=true")
        file("local.properties", "sdk.dir=" + (System.getenv("ANDROID_HOME") ?: System.getProperty("user.home") + "/Library/Android/sdk"))
        file(
            "settings.gradle.kts",
            """
            plugins { id("uk.dominikdias.kmp-flavors.settings") }
            dependencyResolutionManagement { repositories { google(); mavenCentral() } }
            kmpFlavors {
                flavorDimensions("tier")
                productFlavors { register("free"); register("paid") }
            }
            include(":androidApp")
            """,
        )
        file("build.gradle.kts", "")
        file(
            "androidApp/build.gradle.kts",
            """
            plugins { id("com.android.application") }
            android {
                namespace = "sample.android"
                compileSdk = 36
                defaultConfig { applicationId = "sample.android"; minSdk = 26 }
            }
            // Names no variant, so the default (freeDebug) stays active while paidRelease is built.
            tasks.register("mixed") { dependsOn("prePaidReleaseBuild") }
            """,
        )
        assertContains(
            runner(":androidApp:mixed").buildAndFail().output,
            "is building variant \"paidRelease\" but this build compiles multiplatform code for \"freeDebug\"",
        )
        runner(":androidApp:prePaidReleaseBuild").build()
    }

    private fun resourcesSettings() {
        file("gradle.properties", "org.gradle.jvmargs=-Xmx1536m\nandroid.useAndroidX=true\nkotlin.compiler.execution.strategy=in-process")
        file("local.properties", "sdk.dir=" + (System.getenv("ANDROID_HOME") ?: System.getProperty("user.home") + "/Library/Android/sdk"))
        file(
            "settings.gradle.kts",
            """
            plugins { id("uk.dominikdias.kmp-flavors.settings") }
            dependencyResolutionManagement { repositories { google(); mavenCentral() } }
            kmpFlavors {
                flavorDimensions("env")
                productFlavors { register("dev"); register("prod") }
            }
            include(":shared")
            """,
        )
        file("build.gradle.kts", "")
    }

    private fun strings(vararg entries: Pair<String, String>) =
        "<resources>\n" + entries.joinToString("\n") { (k, v) -> "    <string name=\"$k\">$v</string>" } + "\n</resources>\n"

    private fun decodedComposeStrings(): Map<String, String> =
        dir
            .resolve("shared/build/generated/compose/resourceGenerator/preparedResources/commonMain/composeResources/values")
            .listFiles()!!
            .flatMap { it.readLines() }
            .filter { it.startsWith("string|") }
            .associate { line ->
                val (_, name, value) = line.split('|')
                name to
                    String(
                        java.util.Base64
                            .getDecoder()
                            .decode(value),
                    )
            }

    @Test
    fun `compose resources are overlaid per flavor and build type`() {
        resourcesSettings()
        file(
            "shared/build.gradle.kts",
            """
            plugins {
                kotlin("multiplatform")
                kotlin("plugin.compose")
                id("org.jetbrains.compose")
            }
            kotlin { jvm() }
            compose.resources {
                packageOfResClass = "sample.res"
                generateResClass = always
            }
            """,
        )
        file(
            "shared/src/commonMain/composeResources/values/strings.xml",
            strings(
                "app_name" to "Base",
                "greeting" to "Hello",
                "bye" to "Bye",
            ),
        )
        file("shared/src/commonDevMain/composeResources/values/strings.xml", strings("app_name" to "Dev", "greeting" to "Hi dev"))
        file("shared/src/commonDebugMain/composeResources/values/strings.xml", strings("greeting" to "Hi debug"))
        file("shared/src/commonMain/composeResources/drawable/logo.xml", "<vector/>")
        file("shared/src/commonProdMain/composeResources/drawable/logo.png", "prod")

        runner(":shared:prepareComposeResourcesTaskForCommonMain").build()
        assertEquals(mapOf("app_name" to "Dev", "greeting" to "Hi debug", "bye" to "Bye"), decodedComposeStrings())
        val drawables =
            dir.resolve(
                "shared/build/generated/compose/resourceGenerator/preparedResources/commonMain/composeResources/drawable",
            )
        assertEquals(listOf("logo.xml"), drawables.list()!!.toList())

        runner(":shared:prepareComposeResourcesTaskForCommonMain", "-Pkmp.variant=prodRelease").build()
        assertEquals(mapOf("app_name" to "Base", "greeting" to "Hello", "bye" to "Bye"), decodedComposeStrings())
        assertEquals(listOf("logo.png"), drawables.list()!!.toList())
    }

    private fun aarManifest() =
        java.util.zip.ZipFile(dir.resolve("shared/build/outputs/aar/shared.aar")).use { aar ->
            aar.getInputStream(aar.getEntry("AndroidManifest.xml")).reader().readText()
        }

    @Test
    fun `android res and assets of a kotlin multiplatform library are layered like android`() {
        resourcesSettings()
        file(
            "shared/build.gradle.kts",
            """
            import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension

            plugins {
                kotlin("multiplatform")
                id("com.android.kotlin.multiplatform.library")
            }
            kotlin {
                extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
                    namespace = "sample.shared"
                    compileSdk = 36
                    minSdk = 26
                    androidResources { enable = true }
                }
            }
            """,
        )
        file("shared/src/androidMain/res/values/strings.xml", strings("a" to "main", "b" to "main", "c" to "main"))
        file("shared/src/androidDevMain/res/values/strings.xml", strings("b" to "dev", "c" to "dev", "d" to "dev"))
        file("shared/src/androidDebugMain/res/values/strings.xml", strings("c" to "debug"))
        file("shared/src/androidMain/assets/config.json", "main")
        file("shared/src/androidDevMain/assets/config.json", "dev")
        file("shared/src/androidMain/resources/java.properties", "main=1")
        file("shared/src/androidDevMain/resources/dev.properties", "dev=1")
        val tools = "xmlns:android=\"http://schemas.android.com/apk/res/android\" xmlns:tools=\"http://schemas.android.com/tools\""
        file(
            "shared/src/androidMain/AndroidManifest.xml",
            "<manifest $tools><application><meta-data android:name=\"env\" android:value=\"main\"/></application></manifest>",
        )
        file(
            "shared/src/androidDevMain/AndroidManifest.xml",
            "<manifest $tools><uses-permission android:name=\"android.permission.INTERNET\"/><application>" +
                "<meta-data android:name=\"env\" android:value=\"dev\" tools:replace=\"android:value\"/>" +
                "<provider android:name=\"sample.Init\" android:authorities=\"\${applicationId}.init\"/></application></manifest>",
        )
        file(
            "shared/src/androidDebugMain/AndroidManifest.xml",
            "<manifest $tools><application><meta-data android:name=\"env\" android:value=\"debug\" tools:replace=\"android:value\"/></application></manifest>",
        )

        runner(":shared:packageAndroidMainResources", ":shared:mergeAndroidMainAssets").build()
        val values =
            dir
                .resolve(
                    "shared/build/intermediates/packaged_res/androidMain/packageAndroidMainResources/res/values/values.xml",
                ).readText()
        listOf("a" to "main", "b" to "dev", "c" to "debug", "d" to "dev").forEach { (name, value) ->
            assertContains(values, "<string name=\"$name\">$value</string>")
        }
        val assets = dir.resolve("shared/build/intermediates/assets/androidMain/mergeAndroidMainAssets/config.json")
        assertEquals("dev", assets.readText())
        runner(":shared:bundleAndroidMainAar").build()
        // Java resources end up inside the AAR's classes.jar.
        val aarEntries =
            java.util.zip.ZipFile(dir.resolve("shared/build/outputs/aar/shared.aar")).use { aar ->
                java.util.zip.ZipInputStream(aar.getInputStream(aar.getEntry("classes.jar"))).use { jar ->
                    generateSequence { jar.nextEntry }.map { it.name }.toList()
                }
            }
        assertContains(aarEntries, "java.properties")
        assertContains(aarEntries, "dev.properties")

        // Manifest overlays: build type beats flavor beats main, placeholders stay for the app, minSdk stays.
        val manifest = aarManifest()
        assertContains(manifest, "android.permission.INTERNET")
        assertContains(manifest, "android:value=\"debug\"")
        assertContains(manifest, "\${applicationId}.init")
        assertContains(manifest, "android:minSdkVersion=\"26\"")

        runner(":shared:packageAndroidMainResources", ":shared:mergeAndroidMainAssets", "-Pkmp.variant=prodRelease").build()
        val prodValues =
            dir
                .resolve(
                    "shared/build/intermediates/packaged_res/androidMain/packageAndroidMainResources/res/values/values.xml",
                ).readText()
        assertContains(prodValues, "<string name=\"b\">main</string>")
        assertFalse("name=\"d\"" in prodValues)
        assertEquals("main", assets.readText())
        runner(":shared:bundleAndroidMainAar", "-Pkmp.variant=prodRelease").build()
        val prodManifest = aarManifest()
        assertFalse("INTERNET" in prodManifest)
        assertContains(prodManifest, "android:value=\"main\"")
    }
}
