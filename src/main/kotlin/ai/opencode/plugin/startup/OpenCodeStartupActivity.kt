package ai.opencode.plugin.startup

import ai.opencode.plugin.service.OpenCodeService
import ai.opencode.plugin.settings.OpenCodeSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Startup activity for OpenCode plugin
 * Handles auto-start of OpenCode server if configured
 */
class OpenCodeStartupActivity : ProjectActivity {

    private val logger = Logger.getInstance(OpenCodeStartupActivity::class.java)
    
    override suspend fun execute(project: Project) {
        val settings = OpenCodeSettings.INSTANCE
        
        if (settings.autoStartServer) {
            val service = ApplicationManager.getApplication().getService(OpenCodeService::class.java)
            
            // Launch server in background
            CoroutineScope(Dispatchers.Default).launch {
                val result = service.startServer(settings.serverPort)
                result.fold(
                    onSuccess = { port ->
                        // Server started successfully
                    },
                    onFailure = { error ->
                        logger.warn("Failed to auto-start OpenCode server: ${error.message}", error)
                    }
                )
            }
        }
    }
}
