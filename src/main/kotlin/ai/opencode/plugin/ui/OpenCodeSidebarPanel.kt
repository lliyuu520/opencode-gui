package ai.opencode.plugin.ui

import ai.opencode.plugin.service.MCPServerInfo
import ai.opencode.plugin.service.OpenCodeService
import ai.opencode.plugin.service.SessionInfo
import ai.opencode.plugin.settings.OpenCodeSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * Sidebar panel for OpenCode plugin
 * Contains: Sessions, MCP Servers, Skills/Agents tabs
 */
class OpenCodeSidebarPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val tabbedPane = JTabbedPane(JTabbedPane.TOP)
    
    private val configPanel: OpenCodeConfigPanel
    private val sessionsPanel = SessionsPanel(project)
    private val mcpPanel = MCPServersPanel(project)
    private val skillsPanel: SkillsPanel
    
    init {
        configPanel = OpenCodeConfigPanel(project, ::notifyStateChanged)
        skillsPanel = SkillsPanel(project, ::notifyStateChanged)
        tabbedPane.addTab("Config", null, configPanel, "OpenCode CLI and runtime settings")
        tabbedPane.addTab("Sessions", null, sessionsPanel, "Manage conversation sessions")
        tabbedPane.addTab("MCP", null, mcpPanel, "MCP Servers")
        tabbedPane.addTab("Skills", null, skillsPanel, "Skills and Agents")
        
        add(tabbedPane, BorderLayout.CENTER)
        
        preferredSize = Dimension(200, 600)
        minimumSize = Dimension(150, 100)
        border = JBUI.Borders.empty(5)
    }

    var onStateChanged: (() -> Unit)? = null

    private fun notifyStateChanged() {
        onStateChanged?.invoke()
    }

    fun openConfigTab() {
        tabbedPane.selectedComponent = configPanel
    }
}

/**
 * Sessions management panel
 */
class SessionsPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val service = ApplicationManager.getApplication().getService(OpenCodeService::class.java)
    private val sessionListModel = DefaultListModel<String>()
    private val sessionList = JBList(sessionListModel)
    private val statusLabel = JLabel("Ready")

    init {
        val toolbar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JButton("+").apply {
                toolTipText = "New Session"
                addActionListener { createNewSession() }
            })
            add(Box.createHorizontalStrut(5))
            add(JButton("↻").apply {
                toolTipText = "Refresh Sessions"
                addActionListener { refreshSessions() }
            })
        }

        add(toolbar, BorderLayout.NORTH)
        add(JScrollPane(sessionList), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
        
        border = JBUI.Borders.emptyTop(5)

        refreshSessions()
    }

    private fun createNewSession() {
        Messages.showInfoMessage(
            project,
            "Session creation is not wired yet. Start chatting and OpenCode will create a session when the CLI supports it.",
            "OpenCode"
        )
    }

    private fun refreshSessions() {
        statusLabel.text = "Refreshing..."

        CoroutineScope(Dispatchers.IO).launch {
            val result = if (!service.isCLIAvailable()) {
                Result.failure(IllegalStateException("OpenCode CLI is unavailable"))
            } else {
                service.listSessions()
            }

            SwingUtilities.invokeLater {
                result.fold(
                    onSuccess = { sessions -> updateSessions(sessions) },
                    onFailure = { error ->
                        sessionListModel.clear()
                        sessionListModel.addElement("No sessions available")
                        statusLabel.text = error.message ?: "Failed to load sessions"
                    }
                )
            }
        }
    }

    private fun updateSessions(sessions: List<SessionInfo>) {
        sessionListModel.clear()
        if (sessions.isEmpty()) {
            sessionListModel.addElement("No sessions found")
            statusLabel.text = "No sessions"
            return
        }

        sessions.forEach { session ->
            sessionListModel.addElement(
                listOf(session.id, session.project)
                    .filter { it.isNotBlank() }
                    .joinToString("  ")
            )
        }
        statusLabel.text = "Loaded ${sessions.size} session(s)"
    }
}

/**
 * MCP Servers management panel
 */
class MCPServersPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val service = ApplicationManager.getApplication().getService(OpenCodeService::class.java)
    private val mcpListModel = DefaultListModel<MCPServerItem>()
    private val mcpList = JBList(mcpListModel)
    private val statusLabel = JLabel("Ready")

    init {
        val toolbar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JButton("+").apply {
                toolTipText = "Add MCP Server"
                addActionListener {
                    Messages.showInfoMessage(
                        project,
                        "MCP server creation is not wired yet. Refresh reads the servers already known by the OpenCode CLI.",
                        "OpenCode"
                    )
                }
            })
            add(Box.createHorizontalStrut(5))
            add(JButton("↻").apply {
                toolTipText = "Refresh"
                addActionListener { refreshMCPServers() }
            })
        }

        add(toolbar, BorderLayout.NORTH)
        add(JScrollPane(mcpList), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
        
        border = JBUI.Borders.emptyTop(5)

        refreshMCPServers()
    }

    private fun refreshMCPServers() {
        statusLabel.text = "Refreshing..."

        CoroutineScope(Dispatchers.IO).launch {
            val result = if (!service.isCLIAvailable()) {
                Result.failure(IllegalStateException("OpenCode CLI is unavailable"))
            } else {
                service.listMCPServers()
            }

            SwingUtilities.invokeLater {
                result.fold(
                    onSuccess = { servers -> updateMcpServers(servers) },
                    onFailure = { error ->
                        mcpListModel.clear()
                        statusLabel.text = error.message ?: "Failed to load MCP servers"
                    }
                )
            }
        }
    }

    private fun updateMcpServers(servers: List<MCPServerInfo>) {
        mcpListModel.clear()
        if (servers.isEmpty()) {
            statusLabel.text = "No MCP servers"
            return
        }

        servers.forEach { server ->
            mcpListModel.addElement(MCPServerItem(server.name, server.connected))
        }
        statusLabel.text = "Loaded ${servers.size} server(s)"
    }
}

/**
 * Skills and Agents panel
 */
class SkillsPanel(
    private val project: Project,
    private val onStateChanged: () -> Unit = {}
) : JPanel(BorderLayout()) {
    private val service = ApplicationManager.getApplication().getService(OpenCodeService::class.java)
    private val settings = OpenCodeSettings.INSTANCE
    private val skillsListModel = DefaultListModel<String>()
    private val skillsList = JBList(skillsListModel)
    private val agentComboBox = JComboBox(arrayOf(settings.defaultAgent))
    private val statusLabel = JLabel("Ready")

    init {
        val topPanel = JPanel(BorderLayout()).apply {
            add(JBLabel("Agent: "), BorderLayout.WEST)
            add(agentComboBox, BorderLayout.CENTER)
            border = JBUI.Borders.emptyBottom(10)
        }
        
        val toolbar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JBLabel("Skills"))
            add(Box.createHorizontalGlue())
            add(JButton("↻").apply {
                toolTipText = "Refresh Skills"
                addActionListener { refreshSkills() }
            })
        }

        add(JPanel(BorderLayout()).apply {
            add(topPanel, BorderLayout.NORTH)
            add(toolbar, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
        add(JScrollPane(skillsList), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
        
        border = JBUI.Borders.emptyTop(5)

        agentComboBox.selectedItem = settings.defaultAgent
        agentComboBox.addActionListener {
            (agentComboBox.selectedItem as? String)?.let { agent ->
                settings.defaultAgent = agent
                onStateChanged()
            }
        }

        refreshSkills()
    }

    private fun refreshSkills() {
        skillsListModel.clear()
        statusLabel.text = "Refreshing..."

        ApplicationManager.getApplication().executeOnPooledThread {
            val skills = service.listLocalSkills()
            val agents = service.listAgents()

            SwingUtilities.invokeLater {
                agentComboBox.removeAllItems()
                agents.forEach(agentComboBox::addItem)
                if (agents.contains(settings.defaultAgent)) {
                    agentComboBox.selectedItem = settings.defaultAgent
                } else if (agents.isNotEmpty()) {
                    agentComboBox.selectedIndex = 0
                    settings.defaultAgent = agents.first()
                }

                if (skills.isEmpty()) {
                    statusLabel.text = "No local skills found"
                } else {
                    skills.forEach(skillsListModel::addElement)
                    statusLabel.text = "Loaded ${skills.size} local skill(s), ${agents.size} agent(s)"
                }
            }
        }
    }
}

/**
 * MCP Server list item
 */
data class MCPServerItem(
    val name: String,
    val connected: Boolean
) {
    override fun toString(): String {
        return "${if (connected) "●" else "○"} $name"
    }
}
