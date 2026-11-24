package uk.dominikdias.kmpflavors

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import uk.dominikdias.kmpflavors.manifest.MergeManifestOverlaysWork
import javax.inject.Inject

/**
 * Merges flavor / build type manifest overlays into a library's merged manifest with Android's own
 * manifest merger, for `com.android.kotlin.multiplatform.library`, which ignores overlays. Runs in a
 * worker whose classpath is the `manifest-merger` version matching the build's Android Gradle plugin.
 */
@CacheableTask
abstract class MergeManifestOverlaysTask : DefaultTask() {
    /** AGP's merged manifest (the transform input). */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    /** Overlays, lowest priority first. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val overlays: ListProperty<RegularFile>

    @get:Classpath
    abstract val mergerClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputManifest: RegularFileProperty

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun merge() {
        workerExecutor
            .classLoaderIsolation { classpath.from(mergerClasspath) }
            .submit(MergeManifestOverlaysWork::class.java) {
                mainManifest.set(mergedManifest)
                overlays.set(this@MergeManifestOverlaysTask.overlays)
                outputManifest.set(this@MergeManifestOverlaysTask.outputManifest)
            }
    }
}
