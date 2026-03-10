package ai.opencode.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import javax.swing.JPanel

/**
 * Main panel for OpenCode plugin
 * Layout: Status bar on top and switchable content in the center
 */
class OpenCodeMainPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val contentLayout = CardLayout()
    private val contentPanel = JPanel(contentLayout)
    private val sidebarPanel: OpenCodeSidebarPanel
    private val chatPanel: OpenCodeChatPanel
    private val statusPanel: OpenCodeStatusPanel
    private var settingsVisible = false

    init {
        sidebarPanel = OpenCodeSidebarPanel(project)
        chatPanel = OpenCodeChatPanel(project)
        statusPanel = OpenCodeStatusPanel(project) { toggleSettingsView() }
        sidebarPanel.onStateChanged = { statusPanel.refreshStatus() }

        contentPanel.add(chatPanel, CHAT_CARD)
        contentPanel.add(sidebarPanel, SETTINGS_CARD)

        add(statusPanel, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)

        background = OpenCodeTheme.background
        contentPanel.background = OpenCodeTheme.background
        preferredSize = Dimension(800, 600)
        border = JBUI.Borders.empty()

        showChatView()
    }

    private fun toggleSettingsView() {
        if (settingsVisible) {
            showChatView()
            return
        }

        settingsVisible = true
        sidebarPanel.openConfigTab()
        contentLayout.show(contentPanel, SETTINGS_CARD)
        statusPanel.setSettingsVisible(true)
    }

    private fun showChatView() {
        settingsVisible = false
        contentLayout.show(contentPanel, CHAT_CARD)
        statusPanel.setSettingsVisible(false)
    }

    private companion object {
        const val CHAT_CARD = "chat"
        const val SETTINGS_CARD = "settings"
    }
}
