package uk.dominikdias.kmpflavors.ide

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.EditorNotifications
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.nio.file.Path

/** Reads the variant model written by the Gradle plugin and switches the IDE's variant. */
@Service(Service.Level.PROJECT)
class KmpFlavorsService(
    private val project: Project,
) {
    private data class Cached(
        val file: File,
        val stamp: Long,
        val model: FlavorModel?,
    )

    @Volatile private var cached: Cached? = null

    /** The variant chosen in the IDE whose sync has not finished yet. */
    @Volatile var pending: String? = null
        private set

    /** The Gradle root that has a kmp-flavors model: a linked Gradle project, or the project directory. */
    fun root(): Path? {
        val linked = GradleSettings.getInstance(project).linkedProjectsSettings.map { Path.of(it.externalProjectPath) }
        return (linked + listOfNotNull(project.basePath?.let(Path::of)))
            .distinct()
            .firstOrNull { it.resolve(FlavorModel.PATH).toFile().isFile }
    }

    fun model(): FlavorModel? {
        val file = root()?.resolve(FlavorModel.PATH)?.toFile() ?: return null
        val stamp = file.lastModified() xor file.length()
        cached?.takeIf { it.file == file && it.stamp == stamp }?.let { return it.model }
        val model = runCatching { FlavorModel.read(file) }.getOrNull()
        cached = Cached(file, stamp, model)
        if (model != null && model.active == pending) pending = null
        return model
    }

    /** Writes `kmp.variant` to `local.properties` and syncs Gradle. Call on the EDT. */
    fun select(variant: String) {
        val root = root() ?: return
        val directory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root) ?: return
        WriteCommandAction.runWriteCommandAction(project, "Select KMP Variant", null, {
            val file = directory.findChild("local.properties") ?: directory.createChildData(this, "local.properties")
            val documents = FileDocumentManager.getInstance()
            val document = documents.getDocument(file)
            if (document != null) {
                document.setText(LocalProperties.withVariant(document.text, variant))
                documents.saveDocument(document)
            } else {
                VfsUtil.saveText(file, LocalProperties.withVariant(VfsUtil.loadText(file), variant))
            }
        })
        pending = variant.takeIf { it != model()?.active }
        refreshUi()
        sync()
    }

    fun onSyncEnded() {
        pending = null
        cached = null
        refreshUi()
    }

    private fun sync() {
        // Android Studio has its own sync entry point; IntelliJ IDEA uses the external system one.
        val androidSync = ActionManager.getInstance().getAction("Android.SyncProject")
        if (androidSync != null) {
            val event =
                AnActionEvent.createEvent(
                    androidSync,
                    SimpleDataContext.getProjectContext(project),
                    null,
                    "KmpFlavors",
                    ActionUiKind.NONE,
                    null,
                )
            ActionUtil.performAction(androidSync, event)
        } else {
            ExternalSystemUtil.refreshProjects(ImportSpecBuilder(project, GradleConstants.SYSTEM_ID))
        }
    }

    fun refreshUi() {
        ApplicationManager.getApplication().invokeLater({
            project.service<StatusBarWidgetsManager>().updateWidget(VariantWidgetFactory::class.java)
            WindowManager.getInstance().getStatusBar(project)?.updateWidget(VariantWidgetFactory.ID)
            ProjectView.getInstance(project).refresh()
            EditorNotifications.getInstance(project).updateAllNotifications()
        }, project.disposed)
    }
}
