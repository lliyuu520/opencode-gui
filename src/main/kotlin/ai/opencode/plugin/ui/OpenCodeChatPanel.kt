package ai.opencode.plugin.ui

import ai.opencode.plugin.service.OpenCodeService
import ai.opencode.plugin.settings.OpenCodeSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.io.File
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/**
 * Chat panel for OpenCode plugin
 * Contains: Message history, input area, file upload
 */
class OpenCodeChatPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val service = ApplicationManager.getApplication().getService(OpenCodeService::class.java)
    private val messageHistory = MessageHistoryPanel()
    private val inputPanel = ChatInputPanel(::sendMessage)

    init {
        background = OpenCodeTheme.background

        add(createChatHeader(), BorderLayout.NORTH)
        add(createSurfacePanel(BorderLayout()).apply {
            border = JBUI.Borders.empty()
            add(createFlatScrollPane(messageHistory), BorderLayout.CENTER)
        }, BorderLayout.CENTER)
        add(inputPanel, BorderLayout.SOUTH)

        border = JBUI.Borders.empty(8)
    }

    private fun createChatHeader(): JPanel {
        val shortcutChip = JLabel("Ctrl/Cmd + Enter 发送").apply {
            applyChipStyle(this, FlatTone.PRIMARY)
        }
        val filesChip = JLabel("支持文件与图片").apply {
            applyChipStyle(this, FlatTone.DEFAULT)
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 4, 10, 4)
            add(
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    isOpaque = false
                    add(createSectionTitle("对话"))
                    add(Box.createVerticalStrut(4))
                    add(createSectionDescription("直接与 OpenCode 代理协作，消息、附件和返回结果都在同一条工作流里。"))
                },
                BorderLayout.WEST
            )
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                    isOpaque = false
                    add(shortcutChip)
                    add(filesChip)
                },
                BorderLayout.EAST
            )
        }
    }
    
    private fun sendMessage(text: String, attachments: List<File>) {
        if (text.isBlank() && attachments.isEmpty()) return

        messageHistory.addUserMessage(text, attachments.map { it.name })
        inputPanel.setBusy(true, "Checking...")

        CoroutineScope(Dispatchers.IO).launch {
            val result = if (!service.isCLIAvailable()) {
                Result.failure(
                    IllegalStateException(
                        "OpenCode CLI is not available. Configure it in the OpenCode panel."
                    )
                )
            } else {
                val settings = OpenCodeSettings.INSTANCE
                val agent = settings.defaultAgent.ifBlank { null }
                val model = settings.defaultModel.ifBlank { null }

                SwingUtilities.invokeLater {
                    inputPanel.setBusy(
                        true,
                        buildString {
                            append("Sending")
                            append(": ")
                            append(
                                if (agent == null && model == null) {
                                    "config default"
                                } else {
                                    buildList {
                                        model?.let { add("model=$it") }
                                        agent?.let {
                                            add("assistant=${OpenCodeAssistantPresentation.displayName(it)}")
                                        }
                                    }.joinToString(", ")
                                }
                            )
                        }
                    )
                }

                service.runWithMessage(
                    projectPath = project.basePath ?: project.projectFilePath?.let { File(it).parent } ?: ".",
                    message = text,
                    attachments = attachments,
                    model = model,
                    agent = agent
                )
            }

            SwingUtilities.invokeLater {
                inputPanel.setBusy(false)
                result.fold(
                    onSuccess = { output ->
                        messageHistory.addAssistantMessage(output.trim().ifBlank {
                            "OpenCode finished without returning visible output."
                        })
                    },
                    onFailure = { error ->
                        messageHistory.addAssistantMessage(
                            "Request failed: ${error.message ?: error::class.java.simpleName}"
                        )
                    }
                )
            }
        }
    }
}

/**
 * Message history display panel
 */
class MessageHistoryPanel : JPanel() {

    private val messagesPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = LEFT_ALIGNMENT
        isOpaque = false
        border = JBUI.Borders.empty(12, 12, 12, 12)
    }

    private val scrollPane: JScrollPane

    init {
        layout = BorderLayout()
        background = OpenCodeTheme.background
        isOpaque = true

        scrollPane = createFlatScrollPane(messagesPanel)

        add(scrollPane, BorderLayout.CENTER)

        addAssistantMessage("""
            <b>OpenCode</b> 已准备就绪。
            <br><br>
            这里是面向 IntelliJ 的图形界面，保留了代理工作流，同时把常用配置和状态信息放进了同一个工作区。
            <br><br>
            当前支持：
            <ul>
                <li>与 OpenCode 助手对话</li>
                <li>附加文件和图片</li>
                <li>查看会话与 MCP 状态</li>
                <li>覆盖模型和智能体配置</li>
            </ul>
            <br>
            在下方输入内容，或先进入“工作区”检查 CLI、模型与智能体配置。
        """.trimIndent(), isHtml = true)
    }

    fun addUserMessage(text: String, attachments: List<String> = emptyList()) {
        val attachmentInfo = if (attachments.isNotEmpty()) {
            val safeAttachments = attachments.joinToString(", ") { escapeHtml(it) }
            "<br><br><i>Attachments:</i> $safeAttachments"
        } else ""

        addMessage("You", "${escapeHtml(text)}$attachmentInfo", isUser = true, isHtml = true)
    }

    fun addAssistantMessage(text: String, isHtml: Boolean = false) {
        addMessage("OpenCode", text, isUser = false, isHtml = isHtml)
    }

    private fun addMessage(sender: String, text: String, isUser: Boolean, isHtml: Boolean) {
        val messagePanel = MessageBubble(sender, formatMessage(text, isHtml), isUser)
        messagePanel.alignmentX = if (isUser) Component.RIGHT_ALIGNMENT else Component.LEFT_ALIGNMENT
        messagesPanel.add(messagePanel)
        messagesPanel.add(Box.createVerticalStrut(8))

        SwingUtilities.invokeLater {
            val vertical = scrollPane.verticalScrollBar
            vertical.value = vertical.maximum
        }

        revalidate()
        repaint()
    }

    private fun formatMessage(text: String, isHtml: Boolean): String {
        return if (isHtml) {
            text
        } else {
            escapeHtml(text)
                .replace("\n", "<br>")
        }
    }

    private fun escapeHtml(text: String): String = StringUtil.escapeXmlEntities(text)
}

/**
 * Individual message bubble - using modern styled component
 */
class MessageBubble(
    sender: String,
    text: String,
    isUser: Boolean
) : ModernMessageBubble(sender, text, isUser)

/**
 * Chat input panel with file upload - using modern styled component
 */
class ChatInputPanel(
    private val onSend: (String, List<File>) -> Unit
) : ModernInputArea(onSend)
