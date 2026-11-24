package uk.dominikdias.kmpflavors.ide

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import com.intellij.util.ui.EmptyIcon
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JList

internal object VariantPopup {
    fun create(project: Project): JBPopup? {
        val service = project.service<KmpFlavorsService>()
        val model = service.model() ?: return null
        val selected = service.pending ?: model.active
        return JBPopupFactory
            .getInstance()
            .createPopupChooserBuilder(model.variants.map { it.name })
            .setTitle("KMP Variant")
            .setSelectedValue(selected, true)
            .setRenderer(
                object : ColoredListCellRenderer<String>() {
                    override fun customizeCellRenderer(
                        list: JList<out String>,
                        value: String,
                        index: Int,
                        isSelected: Boolean,
                        hasFocus: Boolean,
                    ) {
                        append(value)
                        icon = if (value == selected) AllIcons.Actions.Checked else EmptyIcon.ICON_16
                    }
                },
            ).setNamerForFiltering { it }
            .setItemChosenCallback { if (it != selected) service.select(it) }
            .createPopup()
    }
}

/** Tools | Select KMP Variant… */
class SelectVariantAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.service<KmpFlavorsService>()?.model() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        VariantPopup.create(project)?.showCenteredInCurrentWindow(project)
    }
}

class VariantWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = ID

    override fun getDisplayName() = "KMP Variant"

    override fun isAvailable(project: Project) = project.service<KmpFlavorsService>().model() != null

    override fun createWidget(project: Project): StatusBarWidget = VariantWidget(project)

    override fun canBeEnabledOn(statusBar: StatusBar) = true

    companion object {
        const val ID = "KmpFlavorsVariant"
    }
}

private class VariantWidget(
    private val project: Project,
) : StatusBarWidget,
    StatusBarWidget.TextPresentation {
    override fun ID() = VariantWidgetFactory.ID

    override fun getPresentation() = this

    override fun getText(): String {
        val service = project.service<KmpFlavorsService>()
        val model = service.model() ?: return ""
        val pending = service.pending
        return if (pending != null) "${model.active} → $pending" else model.active
    }

    override fun getAlignment() = Component.CENTER_ALIGNMENT

    override fun getTooltipText() = "kmp-flavors variant the IDE syncs. Click to switch."

    override fun getClickConsumer() =
        Consumer<MouseEvent> { event ->
            VariantPopup.create(project)?.let { popup ->
                val point = RelativePoint(event.component, event.point)
                popup.show(
                    RelativePoint(point.component, java.awt.Point(point.point.x, point.point.y - popup.content.preferredSize.height)),
                )
            }
        }
}
