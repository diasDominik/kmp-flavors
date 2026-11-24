import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// No productFlavors here: kmp-flavors copies play/oss (with applicationIdSuffix) from
// settings.gradle.kts, so the Build Variants panel shows playDebug, ossRelease, …
android {
    namespace = "sample.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "sample.showcase"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}

// F-Droid-style guard: an oss variant must not contain closed-source Google libraries.
// Runs before assemble<OssVariant> and with check; e.g. ./gradlew :androidApp:checkOssDebugNoProprietaryDependencies
androidComponents {
    onVariants(selector().withFlavor("store" to "oss")) { variant ->
        val forbidden =
            listOf("com.google.mlkit", "com.google.android.gms", "com.google.firebase", "com.google.android.odml", "com.google.android.datatransport")
        val modules =
            variant.runtimeConfiguration.incoming.resolutionResult.rootComponent.map { root ->
                val seen = mutableSetOf<ResolvedComponentResult>()
                val queue = ArrayDeque(listOf(root))
                while (queue.isNotEmpty()) {
                    val component = queue.removeFirst()
                    if (seen.add(component)) queue += component.dependencies.filterIsInstance<ResolvedDependencyResult>().map { it.selected }
                }
                seen.mapNotNull { (it.id as? ModuleComponentIdentifier)?.let { id -> "${id.group}:${id.module}:${id.version}" } }.sorted()
            }
        val variantName = variant.name
        val check =
            tasks.register("check${variantName.replaceFirstChar(Char::uppercase)}NoProprietaryDependencies") {
                group = "verification"
                description = "Fails if $variantName's runtime classpath has closed-source Google libraries."
                inputs.property("modules", modules)
                doLast {
                    val found = modules.get().filter { module -> forbidden.any { module.startsWith("$it:") } }
                    if (found.isNotEmpty()) throw GradleException("$variantName contains proprietary libraries:\n" + found.joinToString("\n"))
                    logger.lifecycle("$variantName: ${modules.get().size} runtime libraries, none of them proprietary.")
                }
            }
        tasks.named { it == "assemble${variantName.replaceFirstChar(Char::uppercase)}" || it == "check" }.configureEach { dependsOn(check) }
    }
}
