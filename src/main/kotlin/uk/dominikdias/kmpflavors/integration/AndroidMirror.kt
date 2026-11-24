package uk.dominikdias.kmpflavors.integration

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import uk.dominikdias.kmpflavors.KmpFlavorsSettingsExtension
import uk.dominikdias.kmpflavors.MergeManifestOverlaysTask
import uk.dominikdias.kmpflavors.MergeResourceLayersTask
import uk.dominikdias.kmpflavors.internal.FailAction
import uk.dominikdias.kmpflavors.internal.VariantModel
import uk.dominikdias.kmpflavors.internal.capitalized
import uk.dominikdias.kmpflavors.internal.decapitalized
import uk.dominikdias.kmpflavors.internal.flavorSourceSetName

/**
 * Copies the shared flavor definition onto Android modules, so `com.android.application` and
 * `com.android.library` get the same dimensions, flavors, build types and filter as the
 * multiplatform modules. Anything else can still be configured on those flavors in `android { }`.
 */
internal object AndroidMirror {
    fun application(
        project: Project,
        settings: KmpFlavorsSettingsExtension,
    ) {
        val android = project.extensions.getByType(ApplicationExtension::class.java)
        val model = settings.model
        model.dimensions.forEach { if (it !in android.flavorDimensions) android.flavorDimensions += it }

        settings.productFlavors.forEach { source ->
            val target = android.productFlavors.maybeCreate(source.name)
            target.dimension = model.dimensionOf(source.name)
            if (source.isDefault) target.isDefault = true
            source.applicationId?.let { target.applicationId = it }
            source.applicationIdSuffix?.let { target.applicationIdSuffix = it }
            source.versionNameSuffix?.let { target.versionNameSuffix = it }
            source.versionCode?.let { target.versionCode = it }
            source.versionName?.let { target.versionName = it }
            target.matchingFallbacks.addMissing(source.matchingFallbacks)
            target.manifestPlaceholders.putAll(source.manifestPlaceholders)
        }
        settings.buildTypes.forEach { source ->
            val target = android.buildTypes.maybeCreate(source.name)
            target.isDebuggable = source.isDebuggable
            if (source.isDefault) target.isDefault = true
            source.isMinifyEnabled?.let { target.isMinifyEnabled = it }
            source.applicationIdSuffix?.let { target.applicationIdSuffix = it }
            source.versionNameSuffix?.let { target.versionNameSuffix = it }
            target.matchingFallbacks.addMissing(source.matchingFallbacks)
            target.manifestPlaceholders.putAll(source.manifestPlaceholders)
        }

        val enabled = model.variants.map { it.name }.toSet()
        project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java).apply {
            beforeVariants(selector().all()) { if (it.name !in enabled) it.enable = false }
        }
        enforceActiveVariant(project, settings)
    }

    fun library(
        project: Project,
        settings: KmpFlavorsSettingsExtension,
    ) {
        val android = project.extensions.getByType(LibraryExtension::class.java)
        val model = settings.model
        model.dimensions.forEach { if (it !in android.flavorDimensions) android.flavorDimensions += it }

        settings.productFlavors.forEach { source ->
            val target = android.productFlavors.maybeCreate(source.name)
            target.dimension = model.dimensionOf(source.name)
            if (source.isDefault) target.isDefault = true
            target.matchingFallbacks.addMissing(source.matchingFallbacks)
            target.manifestPlaceholders.putAll(source.manifestPlaceholders)
        }
        settings.buildTypes.forEach { source ->
            val target = android.buildTypes.maybeCreate(source.name)
            if (source.isDefault) target.isDefault = true
            source.isMinifyEnabled?.let { target.isMinifyEnabled = it }
            target.matchingFallbacks.addMissing(source.matchingFallbacks)
            target.manifestPlaceholders.putAll(source.manifestPlaceholders)
        }

        val enabled = model.variants.map { it.name }.toSet()
        project.extensions.getByType(LibraryAndroidComponentsExtension::class.java).apply {
            beforeVariants(selector().all()) { if (it.name !in enabled) it.enable = false }
        }
        enforceActiveVariant(project, settings)
    }

    /**
     * `com.android.kotlin.multiplatform.library` has a single variant, and it derives `res`, `assets`
     * and `jniLibs` from the folders next to `androidMain`'s Java resource folders, all in one layer,
     * which rejects the same resource defined twice. So when the active variant has flavor `res` or
     * `assets` folders, those Java resource folders are handed to the variant API directly and `main`
     * plus the flavor layers (`src/androidFreeMain/res`, `src/androidDebugMain/res`, …) are merged
     * with Android's priorities into one generated `res` / `assets` directory.
     *
     * This plugin also ignores manifest overlays, so `src/androidFreeMain/AndroidManifest.xml` and
     * friends are merged into its merged manifest with Android's manifest merger instead.
     */
    fun kotlinMultiplatformLibrary(
        project: Project,
        settings: KmpFlavorsSettingsExtension,
    ) {
        val variant = settings.variant
        val projectDir = project.layout.projectDirectory
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val androidComponents = project.extensions.getByType(KotlinMultiplatformAndroidComponentsExtension::class.java)

        kotlin.targets
            .matching { it.platformType == KotlinPlatformType.androidJvm }
            .configureEach {
                compilations.matching { it.name == KotlinCompilation.MAIN_COMPILATION_NAME }.configureEach {
                    val sourceSet = defaultSourceSet
                    val flavorRoots =
                        variant.components.map { it to projectDir.dir("src/${flavorSourceSetName(sourceSet.name, it)}").asFile }
                    val manifestOverlays = flavorRoots.map { (_, root) -> root.resolve("AndroidManifest.xml") }.filter { it.isFile }
                    if (manifestOverlays.isNotEmpty()) {
                        val mergeManifest =
                            project.tasks.register(
                                "mergeKmpFlavorsManifestOverlays${sourceSet.name.capitalized()}",
                                MergeManifestOverlaysTask::class.java,
                            ) {
                                group = "build"
                                description = "Merges the ${variant.name} manifest overlays of ${sourceSet.name}."
                                overlays.set(manifestOverlays.map { projectDir.file(it.absolutePath) })
                                mergerClasspath.from(manifestMergerClasspath(project, androidComponents.pluginVersion))
                                outputManifest.set(
                                    project.layout.buildDirectory.file(
                                        "generated/kmpFlavors/android/${sourceSet.name}/AndroidManifest.xml",
                                    ),
                                )
                            }
                        androidComponents.onVariants(androidComponents.selector().all()) { androidVariant ->
                            androidVariant.artifacts
                                .use(mergeManifest)
                                .wiredWithFiles(MergeManifestOverlaysTask::mergedManifest, MergeManifestOverlaysTask::outputManifest)
                                .toTransform(SingleArtifact.MERGED_MANIFEST)
                        }
                    }
                    val layered =
                        flavorRoots.any { (_, root) -> root.resolve("res").isDirectory || root.resolve("assets").isDirectory }
                    if (!layered) return@configureEach

                    val javaResourceDirs = sourceSet.resources.srcDirs.toList()
                    sourceSet.resources.setSrcDirs(emptyList<Any>())
                    val flavorRootFiles = flavorRoots.map { it.second }.toSet()
                    val mainRoots = javaResourceDirs.map { it.parentFile }.filter { it !in flavorRootFiles }.distinct()
                    val roots = mainRoots.map { "main" to it } + flavorRoots

                    fun mergeTask(
                        kind: String,
                        mode: String,
                    ) = project.tasks.register(
                        "mergeKmpFlavorsAndroid${kind.capitalized()}${sourceSet.name.capitalized()}",
                        MergeResourceLayersTask::class.java,
                    ) {
                        group = "build"
                        description = "Merges the ${variant.name} Android $kind of ${sourceSet.name}."
                        layerLabels.set(roots.map { it.first })
                        layerDirectories.set(roots.map { (_, root) -> projectDir.dir(root.resolve(kind).absolutePath) })
                        this.mode.set(mode)
                        outputDirectory.set(project.layout.buildDirectory.dir("generated/kmpFlavors/android/${sourceSet.name}/$kind"))
                    }
                    val mergeRes = mergeTask("res", "ANDROID_RES")
                    val mergeAssets = mergeTask("assets", "FILES")
                    val jniLibs = roots.map { (_, root) -> root.resolve("jniLibs") }.filter { it.isDirectory }

                    androidComponents.onVariants(androidComponents.selector().all()) { androidVariant ->
                        val sources = androidVariant.sources
                        javaResourceDirs.filter { it.isDirectory }.forEach { sources.resources?.addStaticSourceDirectory(it.absolutePath) }
                        sources.res?.addGeneratedSourceDirectory(mergeRes, MergeResourceLayersTask::outputDirectory)
                        sources.assets?.addGeneratedSourceDirectory(mergeAssets, MergeResourceLayersTask::outputDirectory)
                        jniLibs.forEach { sources.jniLibs?.addStaticSourceDirectory(it.absolutePath) }
                    }
                }
            }
    }

    /** `manifest-merger` of the build's AGP version: Android tools libraries are versioned AGP major + 23. */
    private fun manifestMergerClasspath(
        project: Project,
        agp: AndroidPluginVersion,
    ): FileCollection {
        val preview = agp.previewType?.let { "-$it" + agp.preview.toString().padStart(2, '0') } ?: ""
        val version = "${agp.major + 23}.${agp.minor}.${agp.micro}$preview"
        return project.configurations.detachedConfiguration(
            project.dependencies.create("com.android.tools.build:manifest-merger:$version"),
        )
    }

    /**
     * Multiplatform dependencies are compiled for the active variant only, so building another
     * Android variant in the same build (`assemble`, `assembleDebug` with flavors, …) would mix
     * variants. Fail its `pre<Variant>Build` task with instructions instead.
     */
    private fun enforceActiveVariant(
        project: Project,
        settings: KmpFlavorsSettingsExtension,
    ) {
        if (!settings.enforceActiveVariantOnAndroid) return
        val active = settings.variant.name
        val others =
            settings.model.variants
                .map { it.name }
                .filter { it != active }
                .toSet()
        project.tasks
            .named { it.startsWith("pre") && it.endsWith("Build") }
            .configureEach {
                val variant = name.removePrefix("pre").removeSuffix("Build").decapitalized()
                if (variant in others) {
                    val message =
                        "kmp-flavors: ${project.path} is building variant \"$variant\" but this build compiles " +
                            "multiplatform code for \"$active\". Build one variant per invocation, e.g. " +
                            "assemble${variant.capitalized()}, or pass -Pkmp.variant=$variant."
                    doFirst(FailAction(message))
                }
            }
    }

    private fun VariantModel.dimensionOf(flavor: String): String =
        flavorsByDimension.entries.first { (_, flavors) -> flavors.any { it.name == flavor } }.key

    private fun MutableList<String>.addMissing(values: List<String>) {
        values.forEach { if (it !in this) add(it) }
    }
}
