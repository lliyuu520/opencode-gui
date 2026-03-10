package ai.opencode.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.Action
import javax.swing.JComponent

class OpenCodeSettingsDialog(
    project: Project,
    private val onStateChanged: () -> Unit = {}
) : DialogWrapper(project) {

    private val settingsPanel = OpenCodeSidebarPanel(project).apply {
        onStateChanged = this@OpenCodeSettingsDialog.onStateChanged
    }

    init {
        title = "OpenCode"
        setSize(760, 620)
        isResizable = true
        init()
    }

    override fun createCenterPanel(): JComponent = settingsPanel

    override fun createActions(): Array<Action> = arrayOf(okAction)

    fun openConfigTab() {
        settingsPanel.openConfigTab()
    }
}
