package ai.opencode.plugin.ui

import ai.opencode.plugin.service.OpenCodeService
import ai.opencode.plugin.settings.OpenCodeSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.*
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.*
import javax.swing.text.html.HTMLEditorKit

/**
 * Chat panel for OpenCode plugin
 * Contains: Message history, input area, file upload
 */
class OpenCodeChatPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val service = ApplicationManager.getApplication().getService(OpenCodeService::class.java)
    private val messageHistory = MessageHistoryPanel()
    private val inputPanel = ChatInputPanel(::sendMessage)

    init {
        add(JBScrollPane(messageHistory).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }, BorderLayout.CENTER)
        
        add(inputPanel, BorderLayout.SOUTH)
        
        border = JBUI.Borders.empty(5)
    }
    
    private fun sendMessage(text: String, attachments: List<File>) {
        if (text.isBlank() && attachments.isEmpty()) return

        messageHistory.addUserMessage(text, attachments.map { it.name })
        inputPanel.setBusy(true, "Checking...")

        CoroutineScope(Dispatchers.IO).launch {
            val result = if (!service.isCLIAvailable()) {
                Result.failure(
                    IllegalStateException(
                        "OpenCode CLI is not available. Configure the executable path in Settings > Tools > OpenCode."
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
                            if (agent != null || model != null) {
                                append(": ")
                                append(agent ?: "-")
                                append(" / ")
                                append(model ?: "-")
                            }
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
    }

    private val scrollPane: JScrollPane

    init {
        layout = BorderLayout()

        scrollPane = JScrollPane(messagesPanel).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            border = null
        }

        add(scrollPane, BorderLayout.CENTER)

        addAssistantMessage("""
            Welcome to <b>OpenCode</b>! 🚀
            <br><br>
            This is the GUI interface for the OpenCode AI coding agent.
            <br><br>
            Features:
            <ul>
                <li>Chat with AI assistant</li>
                <li>Upload files and images</li>
                <li>Manage sessions</li>
                <li>Configure MCP servers</li>
                <li>Select skills and agents</li>
            </ul>
            <br>
            Start typing your message below!
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
 * Individual message bubble
 */
class MessageBubble(
    sender: String,
    text: String,
    isUser: Boolean
) : JPanel(BorderLayout()) {

    init {
        val bgColor = if (isUser) {
            JBColor(Color(66, 133, 244), Color(33, 102, 204))
        } else {
            JBColor(Color(240, 240, 240), Color(50, 50, 50))
        }

        val textColor = if (isUser) Color.WHITE else JBColor.BLACK

        val contentPanel = JPanel(BorderLayout()).apply {
            background = bgColor
            border = JBUI.Borders.empty(10, 15)
        }

        val senderLabel = JLabel(sender).apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = if (isUser) Color(200, 200, 200) else JBColor.GRAY
            border = JBUI.Borders.emptyBottom(5)
        }

        val textPane = JEditorPane("text/html", "").apply {
            editorKit = HTMLEditorKit().apply {
                styleSheet.addRule("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; font-size: 13px; }")
                styleSheet.addRule("p { margin: 0; }")
                styleSheet.addRule("ul { margin: 5px 0; }")
                styleSheet.addRule("pre { white-space: pre-wrap; font-family: Consolas, monospace; }")
            }
            this.text = "<html><body>$text</body></html>"
            isEditable = false
            background = bgColor
            foreground = textColor
            border = null
        }

        contentPanel.add(senderLabel, BorderLayout.NORTH)
        contentPanel.add(textPane, BorderLayout.CENTER)

        add(contentPanel, BorderLayout.CENTER)

        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        border = JBUI.Borders.empty(0, isUser.compareTo(false) * 50, 0, isUser.compareTo(true) * 50)
    }
}

/**
 * Chat input panel with file upload
 */
class ChatInputPanel(
    private val onSend: (String, List<File>) -> Unit
) : JPanel(BorderLayout()) {

    private val attachments = mutableListOf<File>()
    private val attachmentsPanel = JPanel().apply {
        layout = FlowLayout(FlowLayout.LEFT, 5, 2)
        isOpaque = false
    }

    private val statusLabel = JLabel("Ready").apply {
        foreground = JBColor.GRAY
        border = JBUI.Borders.emptyLeft(5)
    }

    private val inputArea = JTextArea(3, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        font = font.deriveFont(14f)
        border = JBUI.Borders.empty(10)

        addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    e.consume()
                    submitMessage()
                }
            }
        })
    }

    private val attachFileButton = JButton("📎 File").apply {
        toolTipText = "Attach file"
        addActionListener { attachFile() }
    }

    private val attachImageButton = JButton("🖼 Image").apply {
        toolTipText = "Attach image"
        addActionListener { attachImage() }
    }

    private val sendButton = JButton("Send").apply {
        toolTipText = "Send message (Enter)"
        addActionListener { submitMessage() }
    }

    init {
        add(attachmentsPanel, BorderLayout.NORTH)

        add(JBScrollPane(inputArea).apply {
            border = JBUI.Borders.customLine(JBColor.GRAY, 1)
            preferredSize = Dimension(preferredSize.width, 80)
        }, BorderLayout.CENTER)

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5)).apply {
            add(attachFileButton)
            add(attachImageButton)
            add(Box.createHorizontalStrut(20))
            add(sendButton)
            add(Box.createHorizontalStrut(12))
            add(statusLabel)
        }

        add(toolbar, BorderLayout.SOUTH)

        border = JBUI.Borders.emptyTop(10)
    }

    fun setBusy(busy: Boolean, status: String = "Ready") {
        inputArea.isEnabled = !busy
        attachFileButton.isEnabled = !busy
        attachImageButton.isEnabled = !busy
        sendButton.isEnabled = !busy
        statusLabel.text = status
    }

    private fun attachFile() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Select file to attach"
            isMultiSelectionEnabled = true
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFiles.forEach { file ->
                attachments.add(file)
                addAttachmentBadge(file.name)
            }
        }
    }

    private fun attachImage() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Select image to attach"
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "Images", "jpg", "jpeg", "png", "gif", "bmp", "webp"
            )
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            attachments.add(file)
            addAttachmentBadge(file.name)
        }
    }

    private fun addAttachmentBadge(name: String) {
        lateinit var badge: JPanel
        badge = JPanel(FlowLayout(FlowLayout.LEFT, 3, 0)).apply {
            background = JBColor(Color(230, 240, 255), Color(50, 60, 80))
            border = JBUI.Borders.empty(3, 8)
            add(JLabel(if (name.length > 20) "${name.take(17)}..." else name))
            add(JButton("×").apply {
                isBorderPainted = false
                isContentAreaFilled = false
                font = font.deriveFont(Font.BOLD, 14f)
                toolTipText = "Remove"
                addActionListener {
                    attachments.removeIf { it.name == name }
                    attachmentsPanel.remove(badge)
                    attachmentsPanel.revalidate()
                    attachmentsPanel.repaint()
                }
            })
        }
        attachmentsPanel.add(badge)
        attachmentsPanel.revalidate()
        attachmentsPanel.repaint()
    }

    private fun submitMessage() {
        val text = inputArea.text.trim()
        if (text.isBlank() && attachments.isEmpty()) return

        onSend(text, attachments.toList())
        inputArea.text = ""
        attachments.clear()
        attachmentsPanel.removeAll()
        attachmentsPanel.revalidate()
        attachmentsPanel.repaint()
    }
}
