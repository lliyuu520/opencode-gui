package ai.opencode.plugin.settings

import ai.opencode.plugin.service.OpenCodeCliConfig
import ai.opencode.plugin.service.OpenCodeService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.*

/**
 * Settings configurable for OpenCode plugin
 */
class OpenCodeSettingsConfigurable : Configurable {

    private val service = ApplicationManager.getApplication().getService(OpenCodeService::class.java)
    
    private var opencodePathField = JBTextField().apply {
        preferredSize = Dimension(400, preferredSize.height)
    }

    private var opencodeConfigDirField = JBTextField().apply {
        preferredSize = Dimension(400, preferredSize.height)
        toolTipText = "Optional. Sets OPENCODE_CONFIG_DIR for all OpenCode CLI calls. Default is ~/.config/opencode."
    }

    private var opencodeConfigFileField = JBTextField().apply {
        preferredSize = Dimension(400, preferredSize.height)
        toolTipText = "Optional. Sets OPENCODE_CONFIG for all OpenCode CLI calls. Supports .json and .jsonc."
    }
    
    private var defaultModelField = JBTextField().apply {
        preferredSize = Dimension(300, preferredSize.height)
    }
    
    private var defaultAgentCombo = ComboBox(arrayOf("build", "plan")).apply {
        preferredSize = Dimension(150, preferredSize.height)
    }
    
    private var serverPortField = JBTextField().apply {
        preferredSize = Dimension(100, preferredSize.height)
    }
    
    private var autoStartCheckbox = JCheckBox("Auto-start OpenCode server on project open")

    private var testConfigButton = JButton("Test OpenCode")

    private var testStatusLabel = JLabel("Not tested")
    
    private var fontSizeSpinner = JSpinner(SpinnerNumberModel(14, 10, 24, 1))
    
    private var anthropicKeyField = JBPasswordField().apply {
        preferredSize = Dimension(400, preferredSize.height)
    }
    
    private var openaiKeyField = JBPasswordField().apply {
        preferredSize = Dimension(400, preferredSize.height)
    }
    
    private var googleKeyField = JBPasswordField().apply {
        preferredSize = Dimension(400, preferredSize.height)
    }
    
    private var settings: OpenCodeSettings? = null
    
    override fun getDisplayName(): String = "OpenCode"
    
    override fun createComponent(): JComponent {
        settings = OpenCodeSettings.INSTANCE

        testConfigButton.addActionListener { testCurrentConfiguration() }

        val testPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(testConfigButton)
            add(Box.createHorizontalStrut(10))
            add(testStatusLabel)
        }

        return FormBuilder.createFormBuilder()
            .addSeparator()
            .addComponent(JLabel("OpenCode CLI Configuration").apply { font = font.deriveFont(java.awt.Font.BOLD) })
            .addLabeledComponent("OpenCode Path:", opencodePathField)
            .addLabeledComponent("Config Directory:", opencodeConfigDirField)
            .addLabeledComponent("Config File:", opencodeConfigFileField)
            .addComponent(testPanel)
            .addLabeledComponent("Default Model:", defaultModelField)
            .addLabeledComponent("Default Agent:", defaultAgentCombo)
            .addLabeledComponent("Server Port (0=auto):", serverPortField)
            .addComponent(autoStartCheckbox)
            .addSeparator()
            .addComponent(JLabel("Display Settings").apply { font = font.deriveFont(java.awt.Font.BOLD) })
            .addLabeledComponent("Font Size:", fontSizeSpinner)
            .addSeparator()
            .addComponent(JLabel("API Keys (optional, use OpenCode auth instead)").apply { font = font.deriveFont(java.awt.Font.BOLD) })
            .addLabeledComponent("Anthropic API Key:", anthropicKeyField)
            .addLabeledComponent("OpenAI API Key:", openaiKeyField)
            .addLabeledComponent("Google API Key:", googleKeyField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .apply { border = JBUI.Borders.empty(10) }
    }

    private fun testCurrentConfiguration() {
        val config = OpenCodeCliConfig(
            executablePath = opencodePathField.text.trim(),
            configDir = opencodeConfigDirField.text.trim(),
            configFile = opencodeConfigFileField.text.trim(),
            anthropicApiKey = String(anthropicKeyField.password),
            openaiApiKey = String(openaiKeyField.password),
            googleApiKey = String(googleKeyField.password)
        )

        testConfigButton.isEnabled = false
        testStatusLabel.text = "Testing..."

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = service.testCliConfiguration(config)
            ApplicationManager.getApplication().invokeLater {
                testConfigButton.isEnabled = true
                result.fold(
                    onSuccess = { diagnostic ->
                        testStatusLabel.text = "OK: ${diagnostic.version}"
                        val configInfo = buildList {
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
                        Messages.showInfoMessage(configInfo, "OpenCode Test Passed")
                    },
                    onFailure = { error ->
                        testStatusLabel.text = "Failed"
                        Messages.showErrorDialog(
                            error.message ?: "Unknown error while testing OpenCode configuration.",
                            "OpenCode Test Failed"
                        )
                    }
                )
            }
        }
    }
    
    override fun isModified(): Boolean {
        val s = settings ?: return false
        return opencodePathField.text != s.opencodePath ||
               opencodeConfigDirField.text != s.opencodeConfigDir ||
               opencodeConfigFileField.text != s.opencodeConfigFile ||
               defaultModelField.text != s.defaultModel ||
               defaultAgentCombo.selectedItem != s.defaultAgent ||
               serverPortField.text != s.serverPort.toString() ||
               autoStartCheckbox.isSelected != s.autoStartServer ||
               fontSizeSpinner.value != s.fontSize ||
               String(anthropicKeyField.password) != s.anthropicApiKey ||
               String(openaiKeyField.password) != s.openaiApiKey ||
               String(googleKeyField.password) != s.googleApiKey
    }
    
    override fun apply() {
        val s = settings ?: return
        s.opencodePath = opencodePathField.text
        s.opencodeConfigDir = opencodeConfigDirField.text
        s.opencodeConfigFile = opencodeConfigFileField.text
        s.defaultModel = defaultModelField.text
        s.defaultAgent = defaultAgentCombo.selectedItem as String
        s.serverPort = serverPortField.text.toIntOrNull() ?: 0
        s.autoStartServer = autoStartCheckbox.isSelected
        s.fontSize = fontSizeSpinner.value as Int
        s.anthropicApiKey = String(anthropicKeyField.password)
        s.openaiApiKey = String(openaiKeyField.password)
        s.googleApiKey = String(googleKeyField.password)
    }
    
    override fun reset() {
        val s = settings ?: return
        opencodePathField.text = s.opencodePath
        opencodeConfigDirField.text = s.opencodeConfigDir
        opencodeConfigFileField.text = s.opencodeConfigFile
        defaultModelField.text = s.defaultModel
        defaultAgentCombo.selectedItem = s.defaultAgent
        serverPortField.text = s.serverPort.toString()
        autoStartCheckbox.isSelected = s.autoStartServer
        fontSizeSpinner.value = s.fontSize
        anthropicKeyField.text = s.anthropicApiKey
        openaiKeyField.text = s.openaiApiKey
        googleKeyField.text = s.googleApiKey
    }
}
