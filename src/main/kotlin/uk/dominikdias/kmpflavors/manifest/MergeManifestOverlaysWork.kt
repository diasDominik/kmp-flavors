package uk.dominikdias.kmpflavors.manifest

import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

/** Runs in an isolated classloader that contains `manifest-merger`; see [ManifestOverlayMerger]. */
abstract class MergeManifestOverlaysWork : WorkAction<MergeManifestOverlaysWork.Parameters> {
    interface Parameters : WorkParameters {
        val mainManifest: RegularFileProperty
        val overlays: ListProperty<RegularFile>
        val outputManifest: RegularFileProperty
    }

    override fun execute() {
        ManifestOverlayMerger.merge(
            main = parameters.mainManifest.get().asFile,
            overlays = parameters.overlays.get().map { it.asFile },
            output = parameters.outputManifest.get().asFile,
        )
    }
}
