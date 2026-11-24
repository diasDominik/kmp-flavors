package uk.dominikdias.kmpflavors

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import uk.dominikdias.kmpflavors.internal.ResourceLayerMerger

/**
 * Overlays the active variant's resource folders (`composeResources`, Android `res` / `assets`)
 * onto the source set's own, Android-style.
 */
@CacheableTask
abstract class MergeResourceLayersTask : DefaultTask() {
    /** One label per layer (`main`, then flavor/build type/variant components), lowest priority first. */
    @get:Input
    abstract val layerLabels: ListProperty<String>

    /** `COMPOSE`, `ANDROID_RES` or `FILES`. */
    @get:Input
    abstract val mode: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val layerDirectories: ListProperty<Directory>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun merge() {
        val layers =
            layerLabels.get().zip(layerDirectories.get()) { label, dir -> ResourceLayerMerger.Layer(label, dir.asFile) }
        ResourceLayerMerger.merge(layers, outputDirectory.get().asFile, ResourceLayerMerger.Mode.valueOf(mode.get()))
    }
}
