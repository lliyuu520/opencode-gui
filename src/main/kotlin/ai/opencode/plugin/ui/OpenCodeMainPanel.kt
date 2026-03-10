package ai.opencode.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * Main panel for OpenCode plugin
 * Layout: Split pane with sidebar (left) and chat area (right)
 */
class OpenCodeMainPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val sidebarPanel: OpenCodeSidebarPanel
    private val chatPanel: OpenCodeChatPanel
    private val statusPanel: OpenCodeStatusPanel
    
    init {
        sidebarPanel = OpenCodeSidebarPanel(project)
        chatPanel = OpenCodeChatPanel(project)
        statusPanel = OpenCodeStatusPanel(project)
        sidebarPanel.onStateChanged = { statusPanel.refreshStatus() }
        
        // Create split pane
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, chatPanel).apply {
            resizeWeight = 0.2
            dividerSize = 1
            isContinuousLayout = true
            border = null
        }
        
        add(statusPanel, BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)
        
        preferredSize = Dimension(800, 600)
        border = JBUI.Borders.empty()
    }
}
