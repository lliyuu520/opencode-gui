package ai.opencode.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * Main panel for OpenCode plugin
 * Layout: Status bar on top and chat area in the center
 */
class OpenCodeMainPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val chatPanel: OpenCodeChatPanel
    private val statusPanel: OpenCodeStatusPanel
    private var settingsDialog: OpenCodeSettingsDialog? = null

    init {
        chatPanel = OpenCodeChatPanel(project)
        statusPanel = OpenCodeStatusPanel(project) { openSettingsDialog() }

        add(statusPanel, BorderLayout.NORTH)
        add(chatPanel, BorderLayout.CENTER)

        preferredSize = Dimension(800, 600)
        border = JBUI.Borders.empty()
    }

    private fun openSettingsDialog() {
        val dialog = settingsDialog ?: OpenCodeSettingsDialog(project) {
            statusPanel.refreshStatus()
        }.also {
            settingsDialog = it
        }
        dialog.openConfigTab()
        dialog.show()
    }
}
