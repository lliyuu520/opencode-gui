package ai.opencode.plugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent settings for OpenCode plugin
 */
@State(
    name = "ai.opencode.plugin.settings.OpenCodeSettings",
    storages = [Storage("OpenCodeSettings.xml")]
)
class OpenCodeSettings : PersistentStateComponent<OpenCodeSettings> {
    
    // OpenCode CLI path (auto-detect if empty)
    var opencodePath: String = ""

    // OpenCode config directory
    var opencodeConfigDir: String = ""

    // OpenCode config file
    var opencodeConfigFile: String = ""
    
    // Default model
    var defaultModel: String = ""
    
    // Default agent
    var defaultAgent: String = "build"
    
    // Server port
    var serverPort: Int = 0
    
    // Auto-start server
    var autoStartServer: Boolean = false
    
    // Theme
    var theme: String = "dark"
    
    // Font size
    var fontSize: Int = 14
    
    // API Keys (for different providers)
    var anthropicApiKey: String = ""
    var openaiApiKey: String = ""
    var googleApiKey: String = ""
    
    companion object {
        val INSTANCE: OpenCodeSettings
            get() = ApplicationManager.getApplication().getService(OpenCodeSettings::class.java)
    }
    
    override fun getState(): OpenCodeSettings = this
    
    override fun loadState(state: OpenCodeSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
