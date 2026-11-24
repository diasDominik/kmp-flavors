package uk.dominikdias.kmpflavors.integration

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import uk.dominikdias.kmpflavors.KmpVariant
import uk.dominikdias.kmpflavors.MergeResourceLayersTask
import uk.dominikdias.kmpflavors.internal.DeleteOutputsAction
import uk.dominikdias.kmpflavors.internal.capitalized
import uk.dominikdias.kmpflavors.internal.flavorSourceSetName

/**
 * For every Kotlin source set `X` whose active-variant counterparts have a `composeResources`
 * folder (`src/commonFreeMain/composeResources`, …), merges them over `src/X/composeResources`
 * and points Compose's `customDirectory(X)` at the result. Source sets without flavor resources
 * keep Compose's default handling.
 */
internal object ComposeResourcesWiring {
    private const val DIR = "composeResources"

    fun wire(
        project: Project,
        kotlin: KotlinProjectExtension,
        variant: KmpVariant,
    ) {
        val compose = project.extensions.getByType(ComposeExtension::class.java)
        val resources = (compose as ExtensionAware).extensions.getByType(ResourcesExtension::class.java)
        val projectDir = project.layout.projectDirectory

        // Compose copies resources into Android assets without removing old files, so a variant
        // switch would leave the previous variant's resources in the APK.
        project.tasks
            .named { it.startsWith("copy") && it.endsWith("ComposeResourcesToAndroidAssets") }
            .configureEach { doFirst(DeleteOutputsAction()) }

        kotlin.sourceSets.configureEach {
            val sourceSetName = name
            val flavorLayers =
                variant.components
                    .map { it to "src/${flavorSourceSetName(sourceSetName, it)}/$DIR" }
                    .filter { (_, path) -> projectDir.dir(path).asFile.isDirectory }
            if (flavorLayers.isEmpty()) return@configureEach

            val layers = listOf("main" to "src/$sourceSetName/$DIR") + flavorLayers
            val merge =
                project.tasks.register(
                    "mergeKmpFlavorsComposeResources${sourceSetName.capitalized()}",
                    MergeResourceLayersTask::class.java,
                ) {
                    group = "build"
                    description = "Merges the ${variant.name} composeResources of $sourceSetName."
                    layerLabels.set(layers.map { it.first })
                    mode.set("COMPOSE")
                    layerDirectories.set(layers.map { projectDir.dir(it.second) })
                    outputDirectory.set(project.layout.buildDirectory.dir("generated/kmpFlavors/composeResources/$sourceSetName"))
                }
            resources.customDirectory(sourceSetName, merge.flatMap { it.outputDirectory })
        }
    }
}
