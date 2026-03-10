package ai.opencode.plugin.ui

import ai.opencode.plugin.service.MCPServerInfo
import ai.opencode.plugin.service.OpenCodeService
import ai.opencode.plugin.service.SessionInfo
import ai.opencode.plugin.settings.OpenCodeSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.LinkedHashMap
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Settings workspace shown in the main content area.
 */
class OpenCodeSidebarPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val contentLayout = CardLayout()
    private val contentPanel = JPanel(contentLayout).apply {
        isOpaque = false
    }
    private val tabButtons = LinkedHashMap<String, JButton>()

    private val configPanel: OpenCodeConfigPanel
    private val sessionsPanel = SessionsPanel(project)
    private val mcpPanel = MCPServersPanel(project)
    private val agentsPanel: AgentsPanel
    private var selectedTabId = TAB_CONFIG

    init {
        background = OpenCodeTheme.background
        border = JBUI.Borders.empty(8, 12, 12, 12)

        configPanel = OpenCodeConfigPanel(project, ::notifyStateChanged)
        agentsPanel = AgentsPanel(project, ::notifyStateChanged)

        listOf(
            WorkspaceTab(TAB_CONFIG, "配置", configPanel),
            WorkspaceTab(TAB_SESSIONS, "会话", sessionsPanel),
            WorkspaceTab(TAB_MCP, "MCP", mcpPanel),
            WorkspaceTab(TAB_AGENTS, "智能体", agentsPanel)
        ).forEach { tab ->
            contentPanel.add(tab.component, tab.id)
        }

        add(createWorkspaceHeader(), BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)

        selectTab(TAB_CONFIG)
    }

    var onStateChanged: (() -> Unit)? = null

    private fun notifyStateChanged() {
        onStateChanged?.invoke()
    }

    fun openConfigTab() {
        selectTab(TAB_CONFIG)
    }

    private fun createWorkspaceHeader(): JComponent {
        val segmentedPanel = createSurfacePanel(FlowLayout(FlowLayout.LEFT, 6, 6)).apply {
            border = JBUI.Borders.empty(6)
        }

        listOf(
            TAB_CONFIG to "配置",
            TAB_SESSIONS to "会话",
            TAB_MCP to "MCP",
            TAB_AGENTS to "智能体"
        ).forEach { (id, title) ->
            val button = JButton(title).apply {
                applyFlatButtonStyle(this, compact = true)
                addActionListener { selectTab(id) }
            }
            tabButtons[id] = button
            segmentedPanel.add(button)
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(12)
            add(
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    isOpaque = false
                    add(createSectionTitle("工作区"))
                    add(Box.createVerticalStrut(4))
                    add(createSectionDescription("管理 OpenCode 配置、会话、MCP 连接和默认智能体。"))
                },
                BorderLayout.NORTH
            )
            add(Box.createVerticalStrut(10), BorderLayout.CENTER)
            add(segmentedPanel, BorderLayout.SOUTH)
        }
    }

    private fun selectTab(tabId: String) {
        selectedTabId = tabId
        contentLayout.show(contentPanel, tabId)
        tabButtons.forEach { (id, button) ->
            applyFlatButtonStyle(
                button,
                tone = if (id == tabId) FlatTone.PRIMARY else FlatTone.DEFAULT,
                compact = true
            )
        }
    }

    private data class WorkspaceTab(
        val id: String,
        val title: String,
        val component: JComponent
    )

    private companion object {
        const val TAB_CONFIG = "config"
        const val TAB_SESSIONS = "sessions"
        const val TAB_MCP = "mcp"
        const val TAB_AGENTS = "agents"
    }
}

class SessionsPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val service = ApplicationManager.getApplication().getService(OpenCodeService::class.java)
    private val sessionListModel = DefaultListModel<String>()
    private val sessionList = JBList(sessionListModel).apply {
        visibleRowCount = 12
        cellRenderer = FlatTextListCellRenderer()
        background = OpenCodeTheme.surface
        selectionBackground = OpenCodeTheme.primaryLight
        selectionForeground = OpenCodeTheme.textPrimary
        border = JBUI.Borders.empty(6)
    }
    private val statusLabel = JLabel("Ready")
    private val timeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    init {
        layout = BorderLayout()
        background = OpenCodeTheme.background
        border = JBUI.Borders.empty(8, 0, 0, 0)

        applyChipStyle(statusLabel)

        add(createPanelHeader("项目会话", "查看当前工程下 OpenCode 已生成的会话。") {
            add(createToolbarButton("新建") { createNewSession() })
            add(createToolbarButton("刷新") { refreshSessions() })
        }, BorderLayout.NORTH)
        add(createSurfacePanel(BorderLayout()).apply {
            border = JBUI.Borders.empty()
            add(createFlatScrollPane(sessionList), BorderLayout.CENTER)
        }, BorderLayout.CENTER)
        add(
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 8)).apply {
                isOpaque = false
                add(statusLabel)
            },
            BorderLayout.SOUTH
        )

        refreshSessions()
    }

    private fun createNewSession() {
        Messages.showInfoMessage(
            project,
            "当前版本还没有接入显式新建会话。开始聊天后，CLI 支持时会自动创建会话。",
            "OpenCode"
        )
    }

    private fun refreshSessions() {
        updateStatus("正在刷新会话", FlatTone.DEFAULT)

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = if (!service.isCLIAvailable()) {
                Result.failure(IllegalStateException("OpenCode CLI 不可用"))
            } else {
                runBlocking { service.listSessions(project.basePath) }
            }

            SwingUtilities.invokeLater {
                result.fold(
                    onSuccess = { sessions -> updateSessions(sessions) },
                    onFailure = { error ->
                        sessionListModel.clear()
                        sessionListModel.addElement("当前没有可用会话")
                        updateStatus(error.message ?: "加载会话失败", FlatTone.WARNING)
                    }
                )
            }
        }
    }

    private fun updateSessions(sessions: List<SessionInfo>) {
        sessionListModel.clear()
        if (sessions.isEmpty()) {
            sessionListModel.addElement("当前工程还没有会话")
            updateStatus("暂无当前工程会话", FlatTone.DEFAULT)
            return
        }

        sessions.forEach { session ->
            sessionListModel.addElement(
                listOf(session.title, formatUpdatedAt(session.updatedAtEpochMs))
                    .filter { it.isNotBlank() }
                    .joinToString("  ·  ")
            )
        }
        updateStatus("已加载 ${sessions.size} 个会话", FlatTone.SUCCESS)
    }

    private fun updateStatus(text: String, tone: FlatTone) {
        statusLabel.text = text
        applyChipStyle(statusLabel, tone)
    }

    private fun formatUpdatedAt(updatedAtEpochMs: Long): String {
        if (updatedAtEpochMs <= 0L) return ""
        return timeFormatter.format(Instant.ofEpochMilli(updatedAtEpochMs).atZone(ZoneId.systemDefault()))
    }
}

class MCPServersPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val service = ApplicationManager.getApplication().getService(OpenCodeService::class.java)
    private val mcpListModel = DefaultListModel<MCPServerItem>()
    private val mcpList = JBList(mcpListModel).apply {
        cellRenderer = MCPServerCellRenderer()
        background = OpenCodeTheme.surface
        selectionBackground = OpenCodeTheme.primaryLight
        selectionForeground = OpenCodeTheme.textPrimary
        border = JBUI.Borders.empty(6)
    }
    private val statusLabel = JLabel("Ready")
    private val checkButton = createToolbarButton("检查") { refreshMCPServers(checkOnly = true) }
    private val refreshButton = createToolbarButton("刷新") { refreshMCPServers() }

    init {
        background = OpenCodeTheme.background
        border = JBUI.Borders.empty(8, 0, 0, 0)
        applyChipStyle(statusLabel)

        add(createPanelHeader("MCP 服务器", "查看已连接的 MCP 服务器和连接状态。") {
            add(createToolbarButton("新增") {
                Messages.showInfoMessage(
                    project,
                    "当前版本还没有接入 MCP 新建流程。刷新会读取 CLI 已知的服务器列表。",
                    "OpenCode"
                )
            })
            add(checkButton)
            add(refreshButton)
        }, BorderLayout.NORTH)
        add(createSurfacePanel(BorderLayout()).apply {
            add(createFlatScrollPane(mcpList), BorderLayout.CENTER)
        }, BorderLayout.CENTER)
        add(
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 8)).apply {
                isOpaque = false
                add(statusLabel)
            },
            BorderLayout.SOUTH
        )

        refreshMCPServers()
    }

    private fun refreshMCPServers(checkOnly: Boolean = false) {
        checkButton.isEnabled = false
        refreshButton.isEnabled = false
        updateStatus(if (checkOnly) "正在检查连接" else "正在刷新列表", FlatTone.DEFAULT)

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = if (!service.isCLIAvailable()) {
                Result.failure(IllegalStateException("OpenCode CLI 不可用"))
            } else {
                runBlocking { service.listMCPServers() }
            }

            SwingUtilities.invokeLater {
                checkButton.isEnabled = true
                refreshButton.isEnabled = true
                result.fold(
                    onSuccess = { servers -> updateMcpServers(servers) },
                    onFailure = { error ->
                        mcpListModel.clear()
                        updateStatus(error.message ?: "加载 MCP 服务器失败", FlatTone.WARNING)
                    }
                )
            }
        }
    }

    private fun updateMcpServers(servers: List<MCPServerInfo>) {
        mcpListModel.clear()
        if (servers.isEmpty()) {
            mcpListModel.addElement(MCPServerItem("未发现 MCP 服务器", false))
            updateStatus("当前没有 MCP 服务器", FlatTone.DEFAULT)
            return
        }

        servers.forEach { server ->
            mcpListModel.addElement(MCPServerItem(server.name, server.connected))
        }
        val connectedCount = servers.count { it.connected }
        updateStatus("已连接 $connectedCount / ${servers.size}", FlatTone.SUCCESS)
    }

    private fun updateStatus(text: String, tone: FlatTone) {
        statusLabel.text = text
        applyChipStyle(statusLabel, tone)
    }
}

class AgentsPanel(
    private val project: Project,
    private val onStateChanged: () -> Unit = {}
) : JPanel(BorderLayout()) {
    private val service = ApplicationManager.getApplication().getService(OpenCodeService::class.java)
    private val settings = OpenCodeSettings.INSTANCE
    private val agentComboBox = JComboBox(arrayOf(settings.defaultAgent)).apply {
        renderer = OpenCodeAssistantPresentation.createRenderer()
        preferredSize = Dimension(320, preferredSize.height)
        applyInputStyle(this)
    }
    private val descriptionLabel = createSectionDescription("这里设置的是界面层覆盖项；留空时完全沿用 opencode / oh-my-opencode 默认配置。")
    private val statusLabel = JLabel("Ready")

    init {
        background = OpenCodeTheme.background
        border = JBUI.Borders.empty(8, 0, 0, 0)
        applyChipStyle(statusLabel)

        add(createPanelHeader("智能体覆盖", "切换当前工程默认使用的 assistant。") {
            add(createToolbarButton("刷新") { refreshAgents() })
        }, BorderLayout.NORTH)

        add(
            createSurfacePanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(16, 16, 16, 16)
                add(
                    JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        isOpaque = false
                        add(createFieldLabel("默认智能体"))
                        add(Box.createVerticalStrut(8))
                        add(agentComboBox)
                        add(Box.createVerticalStrut(10))
                        add(descriptionLabel)
                    },
                    BorderLayout.NORTH
                )
            },
            BorderLayout.CENTER
        )

        add(
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 8)).apply {
                isOpaque = false
                add(statusLabel)
            },
            BorderLayout.SOUTH
        )

        agentComboBox.selectedItem = settings.defaultAgent
        agentComboBox.addActionListener {
            (agentComboBox.selectedItem as? String)?.let { agent ->
                settings.defaultAgent = agent
                onStateChanged()
            }
        }

        refreshAgents()
    }

    private fun refreshAgents() {
        updateStatus("正在刷新智能体列表", FlatTone.DEFAULT)

        ApplicationManager.getApplication().executeOnPooledThread {
            val agents = service.listAssistants()

            SwingUtilities.invokeLater {
                agentComboBox.removeAllItems()
                agentComboBox.addItem("")
                agents.forEach(agentComboBox::addItem)
                if (settings.defaultAgent.isBlank()) {
                    agentComboBox.selectedItem = ""
                } else if (agents.contains(settings.defaultAgent)) {
                    agentComboBox.selectedItem = settings.defaultAgent
                } else if (agents.isNotEmpty()) {
                    agentComboBox.selectedIndex = 0
                    settings.defaultAgent = agents.first()
                }

                updateStatus("已加载 ${agents.size} 个智能体", FlatTone.SUCCESS)
            }
        }
    }

    private fun updateStatus(text: String, tone: FlatTone) {
        statusLabel.text = text
        applyChipStyle(statusLabel, tone)
    }
}

data class MCPServerItem(
    val name: String,
    val connected: Boolean
) {
    override fun toString(): String {
        return "${if (connected) "●" else "○"} $name"
    }
}

private class MCPServerCellRenderer : ColoredListCellRenderer<MCPServerItem>() {
    private val connectedColor = OpenCodeTheme.success
    private val disconnectedColor = OpenCodeTheme.error

    override fun customizeCellRenderer(
        list: JList<out MCPServerItem>,
        value: MCPServerItem?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        if (value == null) return

        border = JBUI.Borders.empty(8, 10)
        background = if (selected) OpenCodeTheme.primaryLight else OpenCodeTheme.surface

        append(
            "● ",
            SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, if (value.connected) connectedColor else disconnectedColor)
        )
        append(value.name, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, OpenCodeTheme.textPrimary))
        append(
            if (value.connected) "  已连接" else "  未连接",
            SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, if (value.connected) connectedColor else disconnectedColor)
        )
    }
}

private class FlatTextListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        border = JBUI.Borders.empty(8, 10)
        background = if (isSelected) OpenCodeTheme.primaryLight else OpenCodeTheme.surface
        foreground = OpenCodeTheme.textPrimary
        font = OpenCodeTheme.Typography.fontPrimary.deriveFont(12f)
        return this
    }
}

private fun createPanelHeader(
    title: String,
    description: String,
    actions: JPanel.() -> Unit
): JComponent {
    val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
        isOpaque = false
        actions()
    }

    return JPanel(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.emptyBottom(10)
        add(
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(createSectionTitle(title))
                add(Box.createVerticalStrut(4))
                add(createSectionDescription(description))
            },
            BorderLayout.WEST
        )
        add(actionsPanel, BorderLayout.EAST)
    }
}

private fun createToolbarButton(text: String, action: () -> Unit): JButton {
    return JButton(text).apply {
        applyFlatButtonStyle(this, compact = true)
        addActionListener { action() }
    }
}
