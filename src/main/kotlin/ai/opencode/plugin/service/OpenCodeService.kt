package ai.opencode.plugin.service

import ai.opencode.plugin.settings.OpenCodeSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale

/**
 * Service for managing OpenCode CLI process and communication
 */
@Service(Service.Level.APP)
class OpenCodeService {
    
    private val logger = Logger.getInstance(OpenCodeService::class.java)
    
    private var serverProcess: Process? = null
    private var serverPort: Int = 0
    private var isRunning = false

    private fun currentCliConfig(): OpenCodeCliConfig {
        val settings = OpenCodeSettings.INSTANCE
        return OpenCodeCliConfig(
            executablePath = settings.opencodePath,
            configDir = settings.opencodeConfigDir,
            configFile = settings.opencodeConfigFile,
            anthropicApiKey = settings.anthropicApiKey,
            openaiApiKey = settings.openaiApiKey,
            googleApiKey = settings.googleApiKey
        )
    }

    private fun resolveExecutable(config: OpenCodeCliConfig = currentCliConfig()): String {
        val configuredPath = config.executablePath.trim()
        return configuredPath.ifEmpty { "opencode" }
    }

    private fun createProcessBuilder(
        arguments: List<String>,
        workingDirectory: File? = null,
        config: OpenCodeCliConfig = currentCliConfig()
    ): ProcessBuilder {
        val command = buildList {
            add(resolveExecutable(config))
            addAll(arguments)
        }

        return ProcessBuilder(command)
            .redirectErrorStream(true)
            .apply {
                workingDirectory?.let { directory(it) }
                applyProviderEnvironment(environment(), config)
            }
    }

    private fun applyProviderEnvironment(
        environment: MutableMap<String, String>,
        config: OpenCodeCliConfig
    ) {
        if (config.configDir.isNotBlank()) {
            environment["OPENCODE_CONFIG_DIR"] = config.configDir.trim()
        }
        if (config.configFile.isNotBlank()) {
            environment["OPENCODE_CONFIG"] = config.configFile.trim()
        }
        if (config.anthropicApiKey.isNotBlank()) {
            environment["ANTHROPIC_API_KEY"] = config.anthropicApiKey
        }
        if (config.openaiApiKey.isNotBlank()) {
            environment["OPENAI_API_KEY"] = config.openaiApiKey
        }
        if (config.googleApiKey.isNotBlank()) {
            environment["GOOGLE_API_KEY"] = config.googleApiKey
        }
    }
    
    /**
     * Check if OpenCode CLI is available
     */
    fun isCLIAvailable(config: OpenCodeCliConfig = currentCliConfig()): Boolean {
        return try {
            val process = createProcessBuilder(listOf("--version"), config = config).start()
            process.waitFor() == 0
        } catch (e: Exception) {
            logger.warn("OpenCode CLI not found at '${resolveExecutable(config)}': ${e.message}")
            false
        }
    }
    
    /**
     * Get OpenCode version
     */
    fun getVersion(config: OpenCodeCliConfig = currentCliConfig()): String? {
        return try {
            val process = createProcessBuilder(listOf("--version"), config = config).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output.lines().firstOrNull()?.trim()
        } catch (e: Exception) {
            null
        }
    }

    fun testCliConfiguration(config: OpenCodeCliConfig = currentCliConfig()): Result<OpenCodeCliDiagnostic> {
        return try {
            val configDir = resolveConfigDirectory(config)
            val configFile = resolveConfigFile(config)
            val ohMyOpencodeConfig = resolveOhMyOpencodeConfig(configDir)

            if (configDir != null && (!configDir.exists() || !configDir.isDirectory)) {
                return Result.failure(
                    IllegalArgumentException("Config Directory does not exist or is not a directory: ${configDir.path}")
                )
            }

            if (configFile != null && (!configFile.exists() || !configFile.isFile)) {
                return Result.failure(
                    IllegalArgumentException("Config File does not exist or is not a file: ${configFile.path}")
                )
            }

            val version = getVersion(config)
                ?: return Result.failure(
                    IllegalStateException("OpenCode CLI did not return a version. Check the executable path and configuration.")
                )

            Result.success(
                OpenCodeCliDiagnostic(
                    executable = resolveExecutable(config),
                    version = version,
                    configDirectory = configDir?.path,
                    configFile = configFile?.path,
                    ohMyOpencodeConfigFile = ohMyOpencodeConfig?.path,
                    ohMyOpencodeEnabled = configFile?.takeIf(File::exists)?.readText().orEmpty().contains("oh-my-opencode")
                )
            )
        } catch (e: Exception) {
            logger.warn("Failed to test OpenCode CLI configuration", e)
            Result.failure(e)
        }
    }

    private fun resolveConfigDirectory(config: OpenCodeCliConfig): File? {
        val configured = config.configDir.trim()
        if (configured.isNotEmpty()) return File(configured)
        return File(System.getProperty("user.home"), ".config/opencode")
            .takeIf(File::exists)
    }

    private fun resolveConfigFile(config: OpenCodeCliConfig): File? {
        val configured = config.configFile.trim()
        if (configured.isNotEmpty()) return File(configured)

        val configDirectory = resolveConfigDirectory(config) ?: return null
        return sequenceOf("opencode.jsonc", "opencode.json")
            .map { File(configDirectory, it) }
            .firstOrNull(File::exists)
    }

    private fun resolveOhMyOpencodeConfig(configDirectory: File?): File? {
        if (configDirectory == null) return null
        return sequenceOf("oh-my-opencode.jsonc", "oh-my-opencode.json")
            .map { File(configDirectory, it) }
            .firstOrNull(File::exists)
    }
    
    /**
     * Start OpenCode server in headless mode
     */
    suspend fun startServer(port: Int = 0): Result<Int> = withContext(Dispatchers.IO) {
        if (isRunning) {
            return@withContext Result.success(serverPort)
        }
        
        try {
            val command = mutableListOf("serve", "--print-logs")
            if (port > 0) {
                command.add("--port")
                command.add(port.toString())
            }

            val processBuilder = createProcessBuilder(
                arguments = command,
                workingDirectory = File(System.getProperty("user.home"))
            )

            val process = processBuilder.start()
            serverProcess = process
            val startupOutput = StringBuffer()
            
            // Wait for server to start and capture port
            Thread {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    startupOutput.appendLine(currentLine)
                    logger.info("OpenCode: $currentLine")
                    // Parse port from output like "Server started on port 12345"
                    if (currentLine.contains("port") || currentLine.contains("listening")) {
                        val portMatch = Regex("\\b(\\d{4,5})\\b").find(currentLine)
                        if (portMatch != null) {
                            serverPort = portMatch.groupValues[1].toInt()
                            isRunning = true
                        }
                    }
                }
            }.start()
            
            if (isRunning) {
                Result.success(serverPort)
            } else {
                repeat(20) {
                    Thread.sleep(250)
                    if (isRunning) {
                        return@withContext Result.success(serverPort)
                    }
                    if (!process.isAlive) {
                        val errorMessage = startupOutput.toString().trim().ifBlank {
                            "OpenCode server exited during startup without diagnostic output."
                        }
                        return@withContext Result.failure(Exception(errorMessage))
                    }
                }

                if (process.isAlive) {
                    isRunning = true
                    if (serverPort == 0 && port > 0) {
                        serverPort = port
                    }
                    Result.success(serverPort)
                } else {
                    val errorMessage = startupOutput.toString().trim().ifBlank {
                        "OpenCode server exited during startup without diagnostic output."
                    }
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to start OpenCode server", e)
            Result.failure(e)
        }
    }
    
    /**
     * Stop OpenCode server
     */
    fun stopServer() {
        serverProcess?.destroy()
        serverProcess = null
        isRunning = false
        serverPort = 0
    }
    
    /**
     * Check if server is running
     */
    fun isServerRunning(): Boolean = isRunning && serverProcess?.isAlive == true
    
    /**
     * Get server port
     */
    fun getServerPort(): Int = serverPort
    
    /**
     * Run opencode with a message (non-interactive)
     */
    suspend fun runWithMessage(
        projectPath: String,
        message: String,
        model: String? = null,
        agent: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val command = mutableListOf("run", message)
            
            model?.let {
                command.add("-m")
                command.add(it)
            }
            
            agent?.let {
                command.add("--agent")
                command.add(it)
            }

            val process = createProcessBuilder(
                arguments = command,
                workingDirectory = File(projectPath)
            ).start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                Result.success(output)
            } else {
                Result.failure(Exception("OpenCode exited with code $exitCode: $output"))
            }
        } catch (e: Exception) {
            logger.error("Failed to run OpenCode", e)
            Result.failure(e)
        }
    }
    
    /**
     * List MCP servers
     */
    suspend fun listMCPServers(): Result<List<MCPServerInfo>> = withContext(Dispatchers.IO) {
        try {
            val process = createProcessBuilder(listOf("mcp", "list")).start()
            
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            // Parse output
            val servers = mutableListOf<MCPServerInfo>()
            output.lines().forEach { line ->
                // Parse lines like "✓ context7 connected"
                val match = Regex("[✓✗]\\s+(\\S+)\\s+(connected|disconnected)").find(line)
                if (match != null) {
                    servers.add(MCPServerInfo(
                        name = match.groupValues[1],
                        connected = match.groupValues[2] == "connected"
                    ))
                }
            }
            
            Result.success(servers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * List available models
     */
    suspend fun listModels(provider: String? = null): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val command = if (provider != null) {
                listOf("models", provider)
            } else {
                listOf("models")
            }
            
            val process = createProcessBuilder(command).start()
            
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            val models = mutableListOf<ModelInfo>()
            output.lines().forEach { line ->
                // Parse model lines
                if (line.contains("/") && !line.startsWith("#")) {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.isNotEmpty()) {
                        models.add(ModelInfo(
                            id = parts[0],
                            provider = parts[0].split("/").getOrNull(0) ?: "unknown",
                            name = parts.getOrNull(1) ?: parts[0]
                        ))
                    }
                }
            }
            
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * List sessions
     */
    suspend fun listSessions(): Result<List<SessionInfo>> = withContext(Dispatchers.IO) {
        try {
            val process = createProcessBuilder(listOf("session", "list")).start()
            
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            val sessionList = mutableListOf<SessionInfo>()
            output.lines().forEach { line ->
                // Parse session lines
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("Session") && !trimmed.startsWith("-")) {
                    sessionList.add(SessionInfo(
                        id = trimmed.split(Regex("\\s+")).firstOrNull() ?: "",
                        project = trimmed.split(Regex("\\s+")).getOrNull(1) ?: "",
                        createdAt = ""
                    ))
                }
            }
            
            Result.success(sessionList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Export session
     */
    suspend fun exportSession(sessionId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val process = createProcessBuilder(listOf("export", sessionId)).start()
            
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            Result.success(output)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listLocalSkills(): List<String> {
        val home = System.getProperty("user.home")
        val directories = listOf(
            File(home, ".agents/skills"),
            File(home, ".codex/skills")
        )

        return directories
            .asSequence()
            .filter(File::exists)
            .flatMap { root ->
                root.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
                    .orEmpty()
                    .asSequence()
            }
            .map(File::getName)
            .distinct()
            .sorted()
            .toList()
    }

    fun listAgents(config: OpenCodeCliConfig = currentCliConfig()): List<String> {
        return try {
            val process = createProcessBuilder(listOf("agent", "list"), config = config).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                logger.warn("Failed to list agents, exit code=$exitCode")
                listOf("build", "plan")
            } else {
                output.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("[") && !it.startsWith("{") }
                    .map { line -> line.substringBefore(" (").trim() }
                    .filter { line ->
                        line.matches(Regex("[a-z0-9][a-z0-9-]*", RegexOption.IGNORE_CASE)) &&
                            line.lowercase(Locale.getDefault()) != "permission"
                    }
                    .distinct()
                    .sorted()
                    .toList()
                    .ifEmpty { listOf("build", "plan") }
            }
        } catch (e: Exception) {
            logger.warn("Failed to list agents", e)
            listOf("build", "plan")
        }
    }
}

/**
 * MCP Server info
 */
data class MCPServerInfo(
    val name: String,
    val connected: Boolean
)

data class OpenCodeCliConfig(
    val executablePath: String = "",
    val configDir: String = "",
    val configFile: String = "",
    val anthropicApiKey: String = "",
    val openaiApiKey: String = "",
    val googleApiKey: String = ""
)

data class OpenCodeCliDiagnostic(
    val executable: String,
    val version: String,
    val configDirectory: String?,
    val configFile: String?,
    val ohMyOpencodeConfigFile: String?,
    val ohMyOpencodeEnabled: Boolean
)

/**
 * Model info
 */
data class ModelInfo(
    val id: String,
    val provider: String,
    val name: String
)

/**
 * Session info
 */
data class SessionInfo(
    val id: String,
    val project: String,
    val createdAt: String
)
