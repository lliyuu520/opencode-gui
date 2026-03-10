package ai.opencode.plugin.ui

import ai.opencode.plugin.service.OpenCodeCliConfig
import ai.opencode.plugin.service.OpenCodeService
import ai.opencode.plugin.settings.OpenCodeSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
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
    }

    private val configDirField = JBTextField().apply {
        preferredSize = Dimension(400, preferredSize.height)
        toolTipText = "Optional. Defaults to ~/.config/opencode."
    }

    private val configFileField = JBTextField().apply {
        preferredSize = Dimension(400, preferredSize.height)
        toolTipText = "Optional. Supports opencode.json and opencode.jsonc."
    }

    private val defaultModelCombo = ComboBox(arrayOf(settings.defaultModel.ifBlank { "" })).apply {
        preferredSize = Dimension(320, preferredSize.height)
    }

    private val defaultAgentCombo = ComboBox(arrayOf(settings.defaultAgent.ifBlank { "build" })).apply {
        preferredSize = Dimension(180, preferredSize.height)
    }

    private val serverPortField = JBTextField().apply {
        preferredSize = Dimension(100, preferredSize.height)
    }

    private val autoStartCheckbox = JCheckBox("Auto-start server on project open")

    private val saveButton = JButton("Save")
    private val resetButton = JButton("Reset")
    private val testButton = JButton("Test")
    private val refreshModelsButton = JButton("Refresh Models")
    private val refreshAgentsButton = JButton("Refresh Agents")

    private val statusLabel = JLabel("Ready")

    init {
        add(buildContent(), BorderLayout.CENTER)
        border = JBUI.Borders.empty(8)

        bindActions()
        loadFromSettings()
        refreshModels()
        refreshAgents()
    }

    private fun buildContent(): JComponent {
        val actionsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(saveButton)
            add(Box.createHorizontalStrut(8))
            add(resetButton)
            add(Box.createHorizontalStrut(8))
            add(testButton)
            add(Box.createHorizontalStrut(8))
            add(refreshModelsButton)
            add(Box.createHorizontalStrut(8))
            add(refreshAgentsButton)
            add(Box.createHorizontalStrut(12))
            add(statusLabel)
        }

        return FormBuilder.createFormBuilder()
            .addComponent(JLabel("OpenCode Configuration").apply {
                font = font.deriveFont(java.awt.Font.BOLD)
            })
            .addLabeledComponent("OpenCode Path:", opencodePathField)
            .addLabeledComponent("Config Directory:", configDirField)
            .addLabeledComponent("Config File:", configFileField)
            .addLabeledComponent("Default Model:", defaultModelCombo)
            .addLabeledComponent("Default Agent:", defaultAgentCombo)
            .addLabeledComponent("Server Port (0=auto):", serverPortField)
            .addComponent(autoStartCheckbox)
            .addComponent(actionsPanel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun bindActions() {
        saveButton.addActionListener { saveToSettings() }
        resetButton.addActionListener { loadFromSettings() }
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
        statusLabel.text = "Loaded saved settings"
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
        statusLabel.text = "Saved"
        onStateChanged()
        Messages.showInfoMessage(project, "OpenCode configuration saved.", "OpenCode")
    }

    private fun testConfiguration() {
        val config = currentConfig()
        testButton.isEnabled = false
        statusLabel.text = "Testing..."

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = service.testCliConfiguration(config)
            ApplicationManager.getApplication().invokeLater {
                testButton.isEnabled = true
                result.fold(
                    onSuccess = { diagnostic ->
                        statusLabel.text = "OK: ${diagnostic.version}"
                        val details = buildList {
                            add("Version: ${diagnostic.version}")
                            add("Executable: ${diagnostic.executable}")
                            diagnostic.configDirectory?.takeIf { it.isNotBlank() }?.let {
                                add("Config Directory: $it")
                            }
                            diagnostic.configFile?.takeIf { it.isNotBlank() }?.let {
                                add("Config File: $it")
                            }
                            diagnostic.ohMyOpencodeConfigFile?.takeIf { it.isNotBlank() }?.let {
                                add("oh-my-opencode Config: $it")
                            }
                            add("oh-my-opencode Enabled: ${if (diagnostic.ohMyOpencodeEnabled) "yes" else "no"}")
                        }.joinToString("\n")
                        Messages.showInfoMessage(project, details, "OpenCode Test Passed")
                    },
                    onFailure = { error ->
                        statusLabel.text = "Test failed"
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

    private fun refreshAgents() {
        refreshAgentsButton.isEnabled = false
        statusLabel.text = "Refreshing agents..."
        val desiredAgent = (defaultAgentCombo.selectedItem as? String)?.takeIf { it.isNotBlank() }
            ?: settings.defaultAgent

        ApplicationManager.getApplication().executeOnPooledThread {
            val agents = service.listAgents(currentConfig())
            ApplicationManager.getApplication().invokeLater {
                refreshAgentsButton.isEnabled = true
                defaultAgentCombo.removeAllItems()
                agents.forEach(defaultAgentCombo::addItem)
                selectAgent(desiredAgent)
                statusLabel.text = "Loaded ${agents.size} agent(s)"
            }
        }
    }

    private fun refreshModels() {
        refreshModelsButton.isEnabled = false
        statusLabel.text = "Refreshing models..."
        val desiredModel = (defaultModelCombo.selectedItem as? String)?.takeIf { it.isNotBlank() }
            ?: settings.defaultModel

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runBlocking { service.listModels(config = currentConfig()) }
            ApplicationManager.getApplication().invokeLater {
                refreshModelsButton.isEnabled = true
                result.fold(
                    onSuccess = { models ->
                        defaultModelCombo.removeAllItems()
                        models.forEach { defaultModelCombo.addItem(it.id) }
                        selectModel(desiredModel)
                        statusLabel.text = "Loaded ${models.size} model(s)"
                    },
                    onFailure = {
                        selectModel(desiredModel)
                        statusLabel.text = "Failed to load models"
                    }
                )
            }
        }
    }

    private fun selectAgent(agent: String?) {
        val value = agent?.trim().orEmpty()
        if (value.isBlank()) {
            if (defaultAgentCombo.itemCount > 0) {
                defaultAgentCombo.selectedIndex = 0
            }
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
            if (defaultModelCombo.itemCount > 0) {
                defaultModelCombo.selectedIndex = 0
            }
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
}
