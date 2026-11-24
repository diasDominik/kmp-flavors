package uk.dominikdias.kmpflavors.ide

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.SimpleTextAttributes
import java.util.function.Function
import javax.swing.JComponent

/** `src/<flavor folder>` containing [file], with its component. */
internal fun FlavorModel.flavorFolderOf(file: VirtualFile): Pair<VirtualFile, String>? =
    generateSequence(file) { it.parent }
        .filter { it.isDirectory && it.parent?.name == "src" }
        .firstNotNullOfOrNull { folder -> componentOfFolder(folder.name)?.let { folder to it } }

/** Greys out flavor folders under `src/` that the active variant does not use. */
class FlavorFolderDecorator : ProjectViewNodeDecorator {
    override fun decorate(
        node: ProjectViewNode<*>,
        data: PresentationData,
    ) {
        val folder = node.virtualFile?.takeIf { it.isDirectory && it.parent?.name == "src" } ?: return
        val model = node.project?.service<KmpFlavorsService>()?.model() ?: return
        val component = model.componentOfFolder(folder.name) ?: return
        if (model.isActive(component)) return
        data.clearText()
        data.addText(folder.name, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        data.addText("  not in ${model.active}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
}

/** Explains why code in an inactive flavor folder is not analysed, and offers to switch to it. */
class InactiveFlavorNotificationProvider :
    EditorNotificationProvider,
    DumbAware {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        val service = project.service<KmpFlavorsService>()
        val model = service.model() ?: return null
        val (folder, component) = model.flavorFolderOf(file) ?: return null
        if (model.isActive(component)) return null
        val target = model.closestVariantWith(component)
        return Function { editor ->
            EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info).apply {
                text = "src/${folder.name} is not part of the active variant ${model.active}, so the IDE does not analyse it."
                if (target != null) createActionLabel("Switch to ${target.name}") { service.select(target.name) }
                createActionLabel("Choose variant…") { VariantPopup.create(project)?.showCenteredInCurrentWindow(project) }
            }
        }
    }
}
