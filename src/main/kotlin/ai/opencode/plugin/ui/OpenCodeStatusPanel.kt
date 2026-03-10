package ai.opencode.plugin.ui

import ai.opencode.plugin.service.OpenCodeCliConfig
import ai.opencode.plugin.service.OpenCodeService
import ai.opencode.plugin.settings.OpenCodeSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class OpenCodeStatusPanel(
    private val project: Project,
    private val onOpenSettings: () -> Unit = {}
) : JPanel(BorderLayout()) {

    private val service = ApplicationManager.getApplication().getService(OpenCodeService::class.java)
    private val settings = OpenCodeSettings.INSTANCE

    private val titleLabel = createSectionTitle("OpenCode").apply {
        font = font.deriveFont(15f)
    }
    private val subtitleLabel = createSectionDescription("CLI 状态、配置来源和当前覆盖项")

    private val cliLabel = JLabel("CLI")
    private val configLabel = JLabel("Config")
    private val pluginLabel = JLabel("Plugin")
    private val modelLabel = JLabel("Overrides")
    private val serverLabel = JLabel("Server")

    private val settingsButton = JButton(AllIcons.General.GearPlain).apply {
        toolTipText = "打开 OpenCode 设置"
        applyFlatButtonStyle(this, compact = true)
    }
    private val refreshButton = JButton("刷新").apply {
        applyFlatButtonStyle(this, compact = true)
    }

    init {
        background = OpenCodeTheme.background
        border = JBUI.Borders.empty(10, 12, 2, 12)

        val leftPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(titleLabel, BorderLayout.NORTH)
                    add(subtitleLabel, BorderLayout.SOUTH)
                },
                BorderLayout.NORTH
            )
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 6, 10)).apply {
                    isOpaque = false
                    listOf(cliLabel, configLabel, pluginLabel, modelLabel, serverLabel).forEach(::add)
                },
                BorderLayout.CENTER
            )
        }

        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
            isOpaque = false
            add(Box.createHorizontalStrut(4))
            add(settingsButton)
            add(refreshButton)
        }

        add(leftPanel, BorderLayout.CENTER)
        add(rightPanel, BorderLayout.EAST)

        settingsButton.addActionListener { onOpenSettings() }
        refreshButton.addActionListener { refreshStatus() }

        setSettingsVisible(false)
        refreshStatus()
    }

    fun setSettingsVisible(visible: Boolean) {
        settingsButton.icon = if (visible) AllIcons.Actions.Back else AllIcons.General.GearPlain
        settingsButton.toolTipText = if (visible) "返回聊天" else "打开 OpenCode 设置"
        applyFlatButtonStyle(
            settingsButton,
            tone = if (visible) FlatTone.PRIMARY else FlatTone.DEFAULT,
            compact = true
        )
    }

    fun refreshStatus() {
        refreshButton.isEnabled = false
        updateChip(cliLabel, "CLI 检查中", FlatTone.DEFAULT)
        updateChip(configLabel, "配置解析中", FlatTone.DEFAULT)
        updateChip(pluginLabel, "oh-my-opencode 检查中", FlatTone.DEFAULT)
        updateChip(modelLabel, "覆盖项 ${describeOverrides()}", FlatTone.PRIMARY)
        updateChip(serverLabel, "服务 ${describeServer()}", FlatTone.DEFAULT)

        val config = OpenCodeCliConfig(
            executablePath = settings.opencodePath,
            configDir = settings.opencodeConfigDir,
            configFile = settings.opencodeConfigFile,
            anthropicApiKey = settings.anthropicApiKey,
            openaiApiKey = settings.openaiApiKey,
            googleApiKey = settings.googleApiKey
        )

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = service.testCliConfiguration(config)
            ApplicationManager.getApplication().invokeLater {
                refreshButton.isEnabled = true
                result.fold(
                    onSuccess = { diagnostic ->
                        updateChip(cliLabel, "CLI ${diagnostic.version}", FlatTone.SUCCESS)
                        updateChip(
                            configLabel,
                            diagnostic.configFile ?: diagnostic.configDirectory ?: "使用默认配置",
                            FlatTone.DEFAULT
                        )
                        updateChip(
                            pluginLabel,
                            if (diagnostic.ohMyOpencodeEnabled) {
                                "oh-my-opencode 已启用"
                            } else {
                                "oh-my-opencode 未启用"
                            },
                            if (diagnostic.ohMyOpencodeEnabled) FlatTone.SUCCESS else FlatTone.WARNING
                        )
                        updateChip(modelLabel, "覆盖项 ${describeOverrides()}", FlatTone.PRIMARY)
                        updateChip(
                            serverLabel,
                            "服务 ${describeServer()}",
                            if (service.isServerRunning()) FlatTone.SUCCESS else FlatTone.DEFAULT
                        )
                        refreshButton.toolTipText = "刷新状态"
                    },
                    onFailure = { error ->
                        updateChip(cliLabel, "CLI 不可用", FlatTone.DANGER)
                        updateChip(
                            configLabel,
                            settings.opencodeConfigFile.ifBlank {
                                settings.opencodeConfigDir.ifBlank { "使用默认配置" }
                            },
                            FlatTone.DEFAULT
                        )
                        updateChip(pluginLabel, "oh-my-opencode 未知", FlatTone.WARNING)
                        updateChip(modelLabel, "覆盖项 ${describeOverrides()}", FlatTone.PRIMARY)
                        updateChip(serverLabel, "服务 ${describeServer()}", FlatTone.DEFAULT)
                        refreshButton.toolTipText = error.message
                    }
                )
            }
        }
    }

    private fun updateChip(label: JLabel, text: String, tone: FlatTone) {
        label.text = text
        applyChipStyle(label, tone)
    }

    private fun describeServer(): String {
        if (!service.isServerRunning()) return "空闲"
        val port = service.getServerPort()
        return if (port > 0) "运行中:$port" else "运行中"
    }

    private fun describeOverrides(): String {
        val model = settings.defaultModel.trim()
        val agent = settings.defaultAgent.trim()
        if (model.isBlank() && agent.isBlank()) {
            return "配置默认值"
        }

        return buildList {
            if (model.isNotBlank()) add("model=$model")
            if (agent.isNotBlank()) add("assistant=${OpenCodeAssistantPresentation.compactName(agent)}")
        }.joinToString(" · ")
    }
}
