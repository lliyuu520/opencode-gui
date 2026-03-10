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
import java.util.concurrent.TimeUnit

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

    private fun startProcess(
        arguments: List<String>,
        workingDirectory: File? = null,
        config: OpenCodeCliConfig = currentCliConfig()
    ): Process {
        val process = createProcessBuilder(
            arguments = arguments,
            workingDirectory = workingDirectory,
            config = config
        ).start()

        // OpenCode CLI may wait for stdin EOF in non-interactive mode.
        process.outputStream.close()
        return process
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
            val process = startProcess(listOf("--version"), config = config)
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
            val process = startProcess(listOf("--version"), config = config)
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

            val process = processBuilder.start().also {
                it.outputStream.close()
            }
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
        attachments: List<File> = emptyList(),
        model: String? = null,
        agent: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val command = mutableListOf("run", "--format", "json")

            if (message.isNotBlank()) {
                command.add(message)
            }

            model?.let {
                command.add("-m")
                command.add(it)
            }

            agent?.let {
                command.add("--agent")
                command.add(it)
            }

            attachments.forEach { file ->
                command.add("-f")
                command.add(file.absolutePath)
            }

            val missingAttachments = attachments.filterNot(File::exists)
            if (missingAttachments.isNotEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "Attachment not found: ${missingAttachments.joinToString(", ") { it.name }}"
                    )
                )
            }

            logger.info(
                "Running OpenCode with agent='${agent ?: ""}', model='${model ?: ""}', attachments=${attachments.size}"
            )

            val process = startProcess(
                arguments = command,
                workingDirectory = File(projectPath)
            )

            val outputLines = mutableListOf<String>()
            val readerThread = Thread {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        outputLines.add(line)
                        logger.info("OpenCode run: $line")
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }

            val finished = process.waitFor(90, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                readerThread.join(2000)
                val partialOutput = summarizeProcessOutput(outputLines)
                return@withContext Result.failure(
                    IllegalStateException(
                        buildString {
                            append("OpenCode run timed out after 90 seconds.")
                            if (partialOutput.isNotBlank()) {
                                append(" Partial output: ")
                                append(partialOutput)
                            }
                        }
                    )
                )
            }

            readerThread.join(2000)
            val exitCode = process.exitValue()
            val parsedOutput = parseRunOutput(outputLines)

            if (exitCode == 0) {
                Result.success(
                    parsedOutput.response.ifBlank {
                        parsedOutput.diagnostics.ifBlank {
                            "OpenCode finished without returning visible output."
                        }
                    }
                )
            } else {
                Result.failure(
                    Exception(
                        "OpenCode exited with code $exitCode: ${
                            parsedOutput.diagnostics.ifBlank { "No diagnostic output." }
                        }"
                    )
                )
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
            val process = startProcess(listOf("mcp", "list"))
            
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
    suspend fun listModels(
        provider: String? = null,
        config: OpenCodeCliConfig = currentCliConfig()
    ): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val command = if (provider != null) {
                listOf("models", provider)
            } else {
                listOf("models")
            }
            
            val process = startProcess(command, config = config)
            
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
            
            Result.success(
                models
                    .distinctBy(ModelInfo::id)
                    .sortedBy(ModelInfo::id)
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * List sessions
     */
    suspend fun listSessions(): Result<List<SessionInfo>> = withContext(Dispatchers.IO) {
        try {
            val process = startProcess(listOf("session", "list"))
            
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
            val process = startProcess(listOf("export", sessionId))
            
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
            val process = startProcess(listOf("agent", "list"), config = config)
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

    private fun parseRunOutput(lines: List<String>): OpenCodeRunOutput {
        val responseParts = mutableListOf<String>()
        val diagnostics = mutableListOf<String>()

        lines.forEach { rawLine ->
            val line = stripAnsi(rawLine).trim()
            if (line.isBlank()) return@forEach

            if (!line.startsWith("{")) {
                if (!isBenignRuntimeMessage(line)) {
                    diagnostics.add(line)
                }
                return@forEach
            }

            extractTextPayload(line)?.let(responseParts::add)

            if (extractJsonStringField(line, "type") == "error") {
                extractErrorMessage(line)?.let(diagnostics::add)
            }
        }

        return OpenCodeRunOutput(
            response = responseParts.joinToString("\n\n").trim(),
            diagnostics = diagnostics.joinToString(" | ").trim()
        )
    }

    private fun summarizeProcessOutput(lines: List<String>): String {
        val normalizedLines = lines.asSequence()
            .map(::stripAnsi)
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot(::isBenignRuntimeMessage)
            .toList()

        return normalizedLines
            .takeLast(minOf(5, normalizedLines.size))
            .joinToString(" | ")
    }

    private fun extractTextPayload(jsonLine: String): String? {
        val textEventPattern = Regex(
            """"type"\s*:\s*"text".*?"text"\s*:\s*"((?:\\.|[^"\\])*)""""
        )
        return textEventPattern.find(jsonLine)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::decodeJsonString)
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    private fun extractErrorMessage(jsonLine: String): String? {
        return extractJsonStringField(jsonLine, "message")
            ?.let(::decodeJsonString)
            ?.takeIf(String::isNotBlank)
            ?: extractJsonStringField(jsonLine, "text")
                ?.let(::decodeJsonString)
                ?.takeIf(String::isNotBlank)
    }

    private fun extractJsonStringField(jsonLine: String, field: String): String? {
        val pattern = Regex(""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""")
        return pattern.find(jsonLine)?.groupValues?.get(1)
    }

    private fun decodeJsonString(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current != '\\' || index == value.lastIndex) {
                result.append(current)
                index++
                continue
            }

            when (val escaped = value[index + 1]) {
                '\\' -> result.append('\\')
                '"' -> result.append('"')
                '/' -> result.append('/')
                'b' -> result.append('\b')
                'f' -> result.append('\u000C')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'u' -> {
                    if (index + 5 < value.length) {
                        val hex = value.substring(index + 2, index + 6)
                        hex.toIntOrNull(16)?.let { result.append(it.toChar()) }
                        index += 4
                    } else {
                        result.append("\\u")
                    }
                }

                else -> result.append(escaped)
            }
            index += 2
        }
        return result.toString()
    }

    private fun stripAnsi(value: String): String {
        return value.replace(Regex("\\u001B\\[[;\\d]*m"), "")
    }

    private fun isBenignRuntimeMessage(line: String): Boolean {
        return line.startsWith("agent \"") ||
            line.startsWith("! agent \"") ||
            line.startsWith("[config-context]") ||
            line.startsWith("bun install ") ||
            line.startsWith("Checked ") ||
            line.startsWith("Bun ") ||
            line.startsWith("Resolving dependencies")
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

data class OpenCodeRunOutput(
    val response: String,
    val diagnostics: String
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
