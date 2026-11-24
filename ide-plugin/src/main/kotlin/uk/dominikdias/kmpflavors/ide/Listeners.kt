package uk.dominikdias.kmpflavors.ide

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

/** Gradle sync finished (IntelliJ IDEA and Android Studio both import through the external system). */
class SyncListener(
    private val project: Project,
) : ProjectDataImportListener {
    override fun onImportFinished(projectPath: String?) = project.service<KmpFlavorsService>().onSyncEnded()

    override fun onImportFailed(
        projectPath: String?,
        t: Throwable,
    ) = project.service<KmpFlavorsService>().onSyncEnded()
}

/** The model file or `local.properties` changed, e.g. after a command-line build or a manual edit. */
class ModelFileListener(
    private val project: Project,
) : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        if (events.any { it.path.endsWith("/${FlavorModel.PATH}") || it.path.endsWith("/local.properties") }) {
            project.service<KmpFlavorsService>().refreshUi()
        }
    }
}

class KmpFlavorsStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = project.service<KmpFlavorsService>()
        // Load the model file into the VFS so that its changes reach ModelFileListener.
        service.root()?.let { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it.resolve(FlavorModel.PATH)) }
        service.refreshUi()
    }
}
