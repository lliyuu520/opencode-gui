package ai.opencode.plugin.ui

import ai.opencode.plugin.service.OpenCodeCliConfig
import ai.opencode.plugin.service.OpenCodeService
import ai.opencode.plugin.settings.OpenCodeSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class OpenCodeStatusPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val service = ApplicationManager.getApplication().getService(OpenCodeService::class.java)
    private val settings = OpenCodeSettings.INSTANCE

    private val cliLabel = JLabel("CLI: checking")
    private val configLabel = JLabel("Config: unresolved")
    private val pluginLabel = JLabel("oh-my-opencode: unknown")
    private val modelLabel = JLabel("Model: -")
    private val serverLabel = JLabel("Server: idle")
    private val refreshButton = JButton("Refresh")

    init {
        add(
            JPanel(FlowLayout(FlowLayout.LEFT, 10, 6)).apply {
                add(cliLabel)
                add(Box.createHorizontalStrut(4))
                add(configLabel)
                add(Box.createHorizontalStrut(4))
                add(pluginLabel)
                add(Box.createHorizontalStrut(4))
                add(modelLabel)
                add(Box.createHorizontalStrut(4))
                add(serverLabel)
            },
            BorderLayout.CENTER
        )

        add(
            JPanel(FlowLayout(FlowLayout.RIGHT, 0, 3)).apply {
                add(refreshButton)
            },
            BorderLayout.EAST
        )

        border = JBUI.Borders.compound(
            JBUI.Borders.customLineBottom(JBColor.border()),
            JBUI.Borders.empty(4, 8)
        )

        refreshButton.addActionListener { refreshStatus() }
        refreshStatus()
    }

    fun refreshStatus() {
        refreshButton.isEnabled = false
        cliLabel.text = "CLI: checking"
        configLabel.text = "Config: checking"
        pluginLabel.text = "oh-my-opencode: checking"
        modelLabel.text = "Model: ${settings.defaultModel.ifBlank { "-" }} / ${settings.defaultAgent.ifBlank { "-" }}"
        serverLabel.text = "Server: ${describeServer()}"

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
                        cliLabel.text = "CLI: ${diagnostic.version}"
                        configLabel.text = "Config: ${diagnostic.configFile ?: diagnostic.configDirectory ?: "default"}"
                        pluginLabel.text = buildString {
                            append("oh-my-opencode: ")
                            append(if (diagnostic.ohMyOpencodeEnabled) "enabled" else "disabled")
                            diagnostic.ohMyOpencodeConfigFile?.let { append(" (${it.substringAfterLast('\\')})") }
                        }
                        modelLabel.text = "Model: ${settings.defaultModel.ifBlank { "-" }} / ${settings.defaultAgent.ifBlank { "-" }}"
                        serverLabel.text = "Server: ${describeServer()}"
                    },
                    onFailure = { error ->
                        cliLabel.text = "CLI: unavailable"
                        configLabel.text = "Config: ${settings.opencodeConfigFile.ifBlank { settings.opencodeConfigDir.ifBlank { "default" } }}"
                        pluginLabel.text = "oh-my-opencode: unknown"
                        modelLabel.text = "Model: ${settings.defaultModel.ifBlank { "-" }} / ${settings.defaultAgent.ifBlank { "-" }}"
                        serverLabel.text = "Server: ${describeServer()}"
                        refreshButton.toolTipText = error.message
                    }
                )
            }
        }
    }

    private fun describeServer(): String {
        if (!service.isServerRunning()) return "idle"
        val port = service.getServerPort()
        return if (port > 0) "running:$port" else "running"
    }
}
