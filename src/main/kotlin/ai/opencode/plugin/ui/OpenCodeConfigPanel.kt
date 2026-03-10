package ai.opencode.plugin.ui

import ai.opencode.plugin.service.OpenCodeCliConfig
import ai.opencode.plugin.service.OpenCodeCliDiagnostic
import ai.opencode.plugin.service.OpenCodeService
import ai.opencode.plugin.settings.OpenCodeSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class OpenCodeConfigPanel(
    private val project: Project,
    private val onStateChanged: () -> Unit = {}
) : JPanel(BorderLayout()) {

    private val service = ApplicationManager.getApplication().getService(OpenCodeService::class.java)
    private val settings = OpenCodeSettings.INSTANCE

    private val opencodePathField = JBTextField().apply {
        preferredSize = Dimension(400, preferredSize.height)
        toolTipText = "OpenCode executable path. Leave empty to use PATH."
        applyInputStyle(this)
    }
    private val configDirField = JBTextField().apply {
        preferredSize = Dimension(400, preferredSize.height)
        toolTipText = "Optional. Defaults to ~/.config/opencode."
        applyInputStyle(this)
    }
    private val configFileField = JBTextField().apply {
        preferredSize = Dimension(400, preferredSize.height)
        toolTipText = "Optional. Supports opencode.json and opencode.jsonc."
        applyInputStyle(this)
    }
    private val defaultModelCombo = ComboBox(arrayOf(settings.defaultModel.ifBlank { "" })).apply {
        preferredSize = Dimension(360, preferredSize.height)
        renderer = createOverrideRenderer()
        applyInputStyle(this)
    }
    private val defaultAgentCombo = ComboBox(arrayOf(settings.defaultAgent.ifBlank { "" })).apply {
        preferredSize = Dimension(360, preferredSize.height)
        renderer = OpenCodeAssistantPresentation.createRenderer()
        applyInputStyle(this)
    }
    private val serverPortField = JBTextField().apply {
        preferredSize = Dimension(140, preferredSize.height)
        applyInputStyle(this)
    }
    private val autoStartCheckbox = JCheckBox("项目打开时自动启动本地服务").apply {
        isOpaque = false
        foreground = OpenCodeTheme.textPrimary
        font = OpenCodeTheme.Typography.fontPrimary.deriveFont(12f)
    }

    private val resolvedConfigLabel = createValueLabel("Resolving...")
    private val ohMyConfigLabel = createValueLabel("Resolving...")
    private val statusLabel = JLabel("Ready")

    private val saveButton = JButton("保存").apply { applyFlatButtonStyle(this, tone = FlatTone.PRIMARY) }
    private val resetButton = JButton("重置").apply { applyFlatButtonStyle(this) }
    private val testButton = JButton("测试").apply { applyFlatButtonStyle(this) }
    private val refreshModelsButton = JButton("刷新模型").apply { applyFlatButtonStyle(this, compact = true) }
    private val refreshAgentsButton = JButton("刷新智能体").apply { applyFlatButtonStyle(this, compact = true) }

    init {
        background = OpenCodeTheme.background
        border = JBUI.Borders.empty(8, 0, 0, 0)
        applyChipStyle(statusLabel)

        add(createFlatScrollPane(buildContent()), BorderLayout.CENTER)

        bindActions()
        loadFromSettings()
        refreshResolvedConfiguration()
        refreshModels()
        refreshAgents()
    }

    private fun buildContent(): JComponent {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = true
            background = OpenCodeTheme.background
            border = JBUI.Borders.empty(0, 0, 8, 0)

            add(createHeaderCard())
            add(Box.createVerticalStrut(10))
            add(createRuntimeCard())
            add(Box.createVerticalStrut(10))
            add(createResolvedCard())
            add(Box.createVerticalStrut(10))
            add(createOverridesCard())
            add(Box.createVerticalStrut(10))
            add(createServerCard())
        }
    }

    private fun createHeaderCard(): JComponent {
        return createSurfacePanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(16, 16, 16, 16)
            add(
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    isOpaque = false
                    add(createSectionTitle("OpenCode 配置"))
                    add(Box.createVerticalStrut(4))
                    add(createSectionDescription("统一管理 CLI 路径、配置来源、模型覆盖和智能体覆盖。"))
                },
                BorderLayout.WEST
            )
            add(
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    isOpaque = false
                    add(saveButton)
                    add(Box.createHorizontalStrut(8))
                    add(resetButton)
                    add(Box.createHorizontalStrut(8))
                    add(testButton)
                    add(Box.createHorizontalStrut(12))
                    add(statusLabel)
                },
                BorderLayout.EAST
            )
        }
    }

    private fun createRuntimeCard(): JComponent {
        return createCard(
            title = "运行时",
            description = "指定 OpenCode CLI 的路径和配置来源。"
        ) {
            add(createFieldStack("OpenCode 可执行文件", opencodePathField, "留空时从 PATH 中解析。"))
            add(Box.createVerticalStrut(12))
            add(createFieldStack("配置目录", configDirField, "可选。默认通常为 ~/.config/opencode。"))
            add(Box.createVerticalStrut(12))
            add(createFieldStack("配置文件", configFileField, "可选。支持 opencode.json 与 opencode.jsonc。"))
        }
    }

    private fun createResolvedCard(): JComponent {
        return createCard(
            title = "解析结果",
            description = "这里显示当前配置最终落到的文件和 oh-my-opencode 状态。"
        ) {
            add(createInfoRow("Resolved Config", resolvedConfigLabel))
            add(Box.createVerticalStrut(10))
            add(createInfoRow("oh-my-opencode", ohMyConfigLabel))
        }
    }

    private fun createOverridesCard(): JComponent {
        return createCard(
            title = "覆盖项",
            description = "只在 GUI 层覆盖默认 model / assistant；留空则沿用配置默认值。"
        ) {
            add(createFieldStack("默认模型", defaultModelCombo, "未选择时完全使用配置默认值。"))
            add(Box.createVerticalStrut(8))
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(
                        JPanel().apply {
                            layout = BoxLayout(this, BoxLayout.X_AXIS)
                            isOpaque = false
                            add(Box.createHorizontalGlue())
                            add(refreshModelsButton)
                        },
                        BorderLayout.SOUTH
                    )
                }
            )
            add(Box.createVerticalStrut(12))
            add(createFieldStack("默认智能体", defaultAgentCombo, "选择后会覆盖当前工程默认 assistant。"))
            add(Box.createVerticalStrut(8))
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(
                        JPanel().apply {
                            layout = BoxLayout(this, BoxLayout.X_AXIS)
                            isOpaque = false
                            add(Box.createHorizontalGlue())
                            add(refreshAgentsButton)
                        },
                        BorderLayout.SOUTH
                    )
                }
            )
        }
    }

    private fun createServerCard(): JComponent {
        return createCard(
            title = "本地服务",
            description = "控制 GUI 连接的本地服务端口与自动启动行为。"
        ) {
            add(createFieldStack("Server Port", serverPortField, "填 0 表示自动分配端口。"))
            add(Box.createVerticalStrut(12))
            add(autoStartCheckbox)
        }
    }

    private fun createCard(title: String, description: String, build: JPanel.() -> Unit): JComponent {
        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        return createSurfacePanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(16, 16, 16, 16)
            add(
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    isOpaque = false
                    add(createSectionTitle(title))
                    add(Box.createVerticalStrut(4))
                    add(createSectionDescription(description))
                    add(Box.createVerticalStrut(14))
                },
                BorderLayout.NORTH
            )
            add(contentPanel, BorderLayout.CENTER)
        }
            .also { contentPanel.build() }
    }

    private fun createFieldStack(labelText: String, component: JComponent, helperText: String): JComponent {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(createFieldLabel(labelText))
            add(Box.createVerticalStrut(6))
            add(component)
            add(Box.createVerticalStrut(4))
            add(createSectionDescription(helperText))
        }
    }

    private fun createInfoRow(labelText: String, valueLabel: JLabel): JComponent {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(createFieldLabel(labelText), BorderLayout.WEST)
            add(valueLabel, BorderLayout.CENTER)
            border = JBUI.Borders.empty(8, 10)
            background = OpenCodeTheme.surfaceMuted
        }.also {
            it.isOpaque = true
            it.border = JBUI.Borders.compound(
                RoundedBorder(
                    radius = OpenCodeTheme.Radius.SM,
                    borderColor = OpenCodeTheme.border,
                    backgroundColor = OpenCodeTheme.surfaceMuted
                ),
                JBUI.Borders.empty(8, 10)
            )
        }
    }

    private fun bindActions() {
        saveButton.addActionListener { saveToSettings() }
        resetButton.addActionListener {
            loadFromSettings()
            refreshResolvedConfiguration()
        }
        testButton.addActionListener { testConfiguration() }
        refreshModelsButton.addActionListener { refreshModels() }
        refreshAgentsButton.addActionListener { refreshAgents() }
    }

    private fun currentConfig(): OpenCodeCliConfig {
        return OpenCodeCliConfig(
            executablePath = opencodePathField.text.trim(),
            configDir = configDirField.text.trim(),
            configFile = configFileField.text.trim(),
            anthropicApiKey = settings.anthropicApiKey,
            openaiApiKey = settings.openaiApiKey,
            googleApiKey = settings.googleApiKey
        )
    }

    private fun loadFromSettings() {
        opencodePathField.text = settings.opencodePath
        configDirField.text = settings.opencodeConfigDir
        configFileField.text = settings.opencodeConfigFile
        selectModel(settings.defaultModel)
        serverPortField.text = settings.serverPort.toString()
        autoStartCheckbox.isSelected = settings.autoStartServer
        selectAgent(settings.defaultAgent)
        updateStatus("已加载已保存配置", FlatTone.SUCCESS)
        onStateChanged()
    }

    private fun saveToSettings() {
        settings.opencodePath = opencodePathField.text.trim()
        settings.opencodeConfigDir = configDirField.text.trim()
        settings.opencodeConfigFile = configFileField.text.trim()
        settings.defaultModel = (defaultModelCombo.selectedItem as? String).orEmpty().trim()
        settings.defaultAgent = (defaultAgentCombo.selectedItem as? String).orEmpty()
        settings.serverPort = serverPortField.text.trim().toIntOrNull() ?: 0
        settings.autoStartServer = autoStartCheckbox.isSelected
        refreshResolvedConfiguration()
        updateStatus("配置已保存", FlatTone.SUCCESS)
        onStateChanged()
        Messages.showInfoMessage(project, "OpenCode configuration saved.", "OpenCode")
    }

    private fun testConfiguration() {
        val config = currentConfig()
        testButton.isEnabled = false
        updateStatus("正在测试配置", FlatTone.DEFAULT)

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = service.testCliConfiguration(config)
            ApplicationManager.getApplication().invokeLater {
                testButton.isEnabled = true
                result.fold(
                    onSuccess = { diagnostic ->
                        updateStatus("测试通过: ${diagnostic.version}", FlatTone.SUCCESS)
                        applyResolvedConfiguration(diagnostic)
                        val details = buildList {
                            add("Version: ${diagnostic.version}")
                            add("Executable: ${diagnostic.executable}")
                            diagnostic.configDirectory?.takeIf { it.isNotBlank() }?.let { add("Config Directory: $it") }
                            diagnostic.configFile?.takeIf { it.isNotBlank() }?.let { add("Config File: $it") }
                            diagnostic.ohMyOpencodeConfigFile?.takeIf { it.isNotBlank() }?.let {
                                add("oh-my-opencode Config: $it")
                            }
                            add("oh-my-opencode Enabled: ${if (diagnostic.ohMyOpencodeEnabled) "yes" else "no"}")
                        }.joinToString("\n")
                        Messages.showInfoMessage(project, details, "OpenCode Test Passed")
                    },
                    onFailure = { error ->
                        updateStatus("测试失败", FlatTone.DANGER)
                        resolvedConfigLabel.text = "Unavailable"
                        ohMyConfigLabel.text = "Unavailable"
                        Messages.showErrorDialog(
                            project,
                            error.message ?: "Unknown error while testing OpenCode configuration.",
                            "OpenCode Test Failed"
                        )
                    }
                )
            }
        }
    }

    private fun refreshResolvedConfiguration() {
        resolvedConfigLabel.text = "Resolving..."
        ohMyConfigLabel.text = "Resolving..."

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = service.testCliConfiguration(currentConfig())
            ApplicationManager.getApplication().invokeLater {
                result.onSuccess(::applyResolvedConfiguration)
                    .onFailure {
                        resolvedConfigLabel.text = "Unavailable"
                        ohMyConfigLabel.text = "Unavailable"
                    }
            }
        }
    }

    private fun applyResolvedConfiguration(diagnostic: OpenCodeCliDiagnostic) {
        resolvedConfigLabel.text = diagnostic.configFile ?: diagnostic.configDirectory ?: "Use OpenCode default"
        ohMyConfigLabel.text = diagnostic.ohMyOpencodeConfigFile
            ?: if (diagnostic.ohMyOpencodeEnabled) "Enabled via config" else "Not detected"
    }

    private fun refreshAgents() {
        refreshAgentsButton.isEnabled = false
        updateStatus("正在刷新智能体", FlatTone.DEFAULT)
        val desiredAgent = (defaultAgentCombo.selectedItem as? String)?.takeIf { it.isNotBlank() } ?: settings.defaultAgent

        ApplicationManager.getApplication().executeOnPooledThread {
            val agents = service.listAssistants(currentConfig())
            ApplicationManager.getApplication().invokeLater {
                refreshAgentsButton.isEnabled = true
                defaultAgentCombo.removeAllItems()
                defaultAgentCombo.addItem("")
                agents.forEach(defaultAgentCombo::addItem)
                selectAgent(desiredAgent)
                updateStatus("已加载 ${agents.size} 个智能体", FlatTone.SUCCESS)
            }
        }
    }

    private fun refreshModels() {
        refreshModelsButton.isEnabled = false
        updateStatus("正在刷新模型", FlatTone.DEFAULT)
        val desiredModel = (defaultModelCombo.selectedItem as? String)?.takeIf { it.isNotBlank() } ?: settings.defaultModel

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runBlocking { service.listModels(config = currentConfig()) }
            ApplicationManager.getApplication().invokeLater {
                refreshModelsButton.isEnabled = true
                result.fold(
                    onSuccess = { models ->
                        defaultModelCombo.removeAllItems()
                        defaultModelCombo.addItem("")
                        models.forEach { defaultModelCombo.addItem(it.id) }
                        selectModel(desiredModel)
                        updateStatus("已加载 ${models.size} 个模型", FlatTone.SUCCESS)
                    },
                    onFailure = {
                        selectModel(desiredModel)
                        updateStatus("模型刷新失败", FlatTone.WARNING)
                    }
                )
            }
        }
    }

    private fun selectAgent(agent: String?) {
        val value = agent?.trim().orEmpty()
        if (value.isBlank()) {
            ensureBlankOption(defaultAgentCombo)
            defaultAgentCombo.selectedItem = ""
            return
        }

        val existing = (0 until defaultAgentCombo.itemCount)
            .map { defaultAgentCombo.getItemAt(it) }
            .any { it == value }

        if (!existing) {
            defaultAgentCombo.addItem(value)
        }
        defaultAgentCombo.selectedItem = value
    }

    private fun selectModel(model: String?) {
        val value = model?.trim().orEmpty()
        if (value.isBlank()) {
            ensureBlankOption(defaultModelCombo)
            defaultModelCombo.selectedItem = ""
            return
        }

        val existing = (0 until defaultModelCombo.itemCount)
            .map { defaultModelCombo.getItemAt(it) }
            .any { it == value }

        if (!existing) {
            defaultModelCombo.addItem(value)
        }
        defaultModelCombo.selectedItem = value
    }

    private fun ensureBlankOption(comboBox: ComboBox<String>) {
        val hasBlank = (0 until comboBox.itemCount).any { comboBox.getItemAt(it).isBlank() }
        if (!hasBlank) {
            comboBox.insertItemAt("", 0)
        }
    }

    private fun updateStatus(text: String, tone: FlatTone) {
        statusLabel.text = text
        applyChipStyle(statusLabel, tone)
    }

    private fun createOverrideRenderer(): DefaultListCellRenderer {
        return object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? String)?.takeIf { it.isNotBlank() } ?: "使用配置默认值"
                border = JBUI.Borders.empty(6, 8)
                return this
            }
        }
    }
}
