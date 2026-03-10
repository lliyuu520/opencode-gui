package ai.opencode.plugin.ui

import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.geom.RoundRectangle2D
import java.io.File
import javax.swing.AbstractAction
import javax.swing.AbstractButton
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.border.AbstractBorder
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

/**
 * Modern message bubble with a flatter and calmer style.
 */
open class ModernMessageBubble(
    private val sender: String,
    private val text: String,
    private val isUser: Boolean
) : JPanel(BorderLayout()) {

    init {
        isOpaque = false
        add(createContentPanel(), BorderLayout.CENTER)
        border = if (isUser) {
            JBUI.Borders.empty(4, 72, 4, 8)
        } else {
            JBUI.Borders.empty(4, 8, 4, 72)
        }
    }

    private fun createContentPanel(): JPanel {
        val backgroundColor = if (isUser) OpenCodeTheme.userMessageBg else OpenCodeTheme.assistantMessageBg
        val textColor = if (isUser) OpenCodeTheme.userMessageText else OpenCodeTheme.assistantMessageText
        val secondaryColor = if (isUser) OpenCodeTheme.userMessageSecondary else OpenCodeTheme.textSecondary
        val borderColor = if (isUser) OpenCodeTheme.primaryLight else OpenCodeTheme.assistantMessageBorder

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = RoundedBorder(
                radius = OpenCodeTheme.Radius.LG,
                borderColor = borderColor,
                backgroundColor = backgroundColor
            )

            val innerPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(10, 14)
            }

            val senderLabel = JLabel(sender).apply {
                font = OpenCodeTheme.Typography.fontPrimary.deriveFont(Font.BOLD, 11f)
                foreground = secondaryColor
                border = JBUI.Borders.emptyBottom(4)
            }

            val textPane = JEditorPane("text/html", "").apply {
                editorKit = createStyledEditorKit()
                putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
                font = OpenCodeTheme.Typography.fontPrimary.deriveFont(OpenCodeTheme.Typography.fontSizeMD.toFloat())
                this.text = "<html><body style='margin: 0; padding: 0;'>$text</body></html>"
                isEditable = false
                isOpaque = false
                foreground = textColor
                border = null

                addHyperlinkListener { event ->
                    if (event.eventType == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                        try {
                            java.awt.Desktop.getDesktop().browse(event.url.toURI())
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            innerPanel.add(senderLabel, BorderLayout.NORTH)
            innerPanel.add(textPane, BorderLayout.CENTER)
            add(innerPanel, BorderLayout.CENTER)
        }
    }

    private fun createStyledEditorKit(): HTMLEditorKit {
        return HTMLEditorKit().apply {
            styleSheet = StyleSheet().apply {
                addRule(
                    """
                    body {
                        font-family: Dialog, sans-serif;
                        font-size: ${OpenCodeTheme.Typography.fontSizeMD}px;
                        line-height: 1.55;
                        color: inherit;
                    }
                    """.trimIndent()
                )
                addRule("p { margin: 0 0 10px 0; }")
                addRule("p:last-child { margin-bottom: 0; }")
                addRule("ul, ol { margin: 8px 0; padding-left: 18px; }")
                addRule("li { margin: 4px 0; }")
                addRule(
                    """
                    code {
                        font-family: Monospaced, monospace;
                        font-size: 12px;
                        background-color: #F1F5F9;
                        padding: 2px 6px;
                    }
                    """.trimIndent()
                )
                addRule(
                    """
                    pre {
                        font-family: Monospaced, monospace;
                        font-size: 12px;
                        background-color: #1F2937;
                        color: #E2E8F0;
                        padding: 12px 14px;
                        margin: 8px 0;
                    }
                    """.trimIndent()
                )
                addRule("a { color: #2563EB; text-decoration: none; }")
                addRule("a:hover { text-decoration: underline; }")
                addRule("h1, h2, h3, h4 { margin: 16px 0 8px 0; font-weight: 600; }")
                addRule("h1 { font-size: 20px; }")
                addRule("h2 { font-size: 17px; }")
                addRule("h3 { font-size: 15px; }")
                addRule(
                    """
                    blockquote {
                        border-left: 3px solid #2563EB;
                        margin: 8px 0;
                        padding: 8px 14px;
                        background-color: #EFF6FF;
                    }
                    """.trimIndent()
                )
            }
        }
    }

    override fun getPreferredSize(): Dimension {
        val size = super.getPreferredSize()
        return Dimension(minOf(size.width, 680), size.height)
    }
}

/**
 * Custom rounded border with optional background color.
 */
class RoundedBorder(
    private val radius: Int,
    private val borderColor: Color,
    private val backgroundColor: Color,
    private val borderWidth: Int = 1
) : AbstractBorder() {

    override fun getBorderInsets(c: Component?): Insets {
        return Insets(radius / 2, radius / 2, radius / 2, radius / 2)
    }

    override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
        val g2 = g as? Graphics2D ?: return
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val roundRect = RoundRectangle2D.Float(
            x.toFloat(),
            y.toFloat(),
            (width - 1).toFloat(),
            (height - 1).toFloat(),
            radius.toFloat(),
            radius.toFloat()
        )

        g2.color = backgroundColor
        g2.fill(roundRect)

        if (borderWidth > 0) {
            g2.color = borderColor
            g2.stroke = BasicStroke(borderWidth.toFloat())
            g2.draw(roundRect)
        }
    }
}

/**
 * Modern input area panel with flatter styling.
 */
open class ModernInputArea(
    private val onSend: (String, List<File>) -> Unit
) : JPanel(BorderLayout()) {

    private val attachments = mutableListOf<File>()
    private val sendShortcutMask = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
    private val sendShortcutText = if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
        "Cmd+Enter"
    } else {
        "Ctrl+Enter"
    }

    private val attachmentsPanel = JPanel().apply {
        layout = FlowLayout(FlowLayout.LEFT, 6, 4)
        isOpaque = false
        border = JBUI.Borders.empty(8, 10, 0, 10)
    }

    private val statusLabel = JLabel("Ready to chat").apply {
        foreground = OpenCodeTheme.textMuted
        font = OpenCodeTheme.Typography.fontPrimary.deriveFont(Font.PLAIN, 11f)
    }

    private val inputArea = JTextArea(3, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        font = OpenCodeTheme.Typography.fontPrimary.deriveFont(14f)
        foreground = OpenCodeTheme.textPrimary
        background = OpenCodeTheme.inputBackground
        caretColor = OpenCodeTheme.primary
        border = JBUI.Borders.empty(12, 14)

        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, sendShortcutMask), "submit")
        getActionMap().put("submit", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) {
                submitMessage()
            }
        })
    }

    private val attachFileButton = createButton("文件", "Attach file") { attachFile() }
    private val attachImageButton = createButton("图片", "Attach image") { attachImage() }
    private val sendButton = createPrimaryButton("发送", "Send message ($sendShortcutText)") { submitMessage() }

    init {
        background = OpenCodeTheme.background
        border = JBUI.Borders.empty(OpenCodeTheme.Spacing.MD, OpenCodeTheme.Spacing.MD, OpenCodeTheme.Spacing.MD, OpenCodeTheme.Spacing.MD)

        val inputContainer = JPanel(BorderLayout()).apply {
            background = OpenCodeTheme.surface
            border = RoundedBorder(
                radius = OpenCodeTheme.Radius.LG,
                borderColor = OpenCodeTheme.inputBorder,
                backgroundColor = OpenCodeTheme.surface
            )
        }

        inputContainer.add(attachmentsPanel, BorderLayout.NORTH)
        inputContainer.add(
            JScrollPane(inputArea).apply {
                border = null
                viewport.border = null
                viewport.background = OpenCodeTheme.inputBackground
                background = OpenCodeTheme.inputBackground
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                preferredSize = Dimension(preferredSize.width, 112)
            },
            BorderLayout.CENTER
        )

        inputContainer.add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(0, 10, 10, 10)
                add(
                    JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                        isOpaque = false
                        add(attachFileButton)
                        add(attachImageButton)
                    },
                    BorderLayout.WEST
                )
                add(
                    JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                        isOpaque = false
                        add(statusLabel)
                        add(sendButton)
                    },
                    BorderLayout.EAST
                )
            },
            BorderLayout.SOUTH
        )

        add(inputContainer, BorderLayout.CENTER)
    }

    private fun createButton(text: String, tooltip: String, action: () -> Unit): JButton {
        return JButton(text).apply {
            toolTipText = tooltip
            applyFlatButtonStyle(this, compact = true)
            addActionListener { action() }
        }
    }

    private fun createPrimaryButton(text: String, tooltip: String, action: () -> Unit): JButton {
        return JButton(text).apply {
            toolTipText = tooltip
            applyFlatButtonStyle(this, tone = FlatTone.PRIMARY)
            addActionListener { action() }
        }
    }

    fun setBusy(busy: Boolean, status: String = "Ready") {
        inputArea.isEnabled = !busy
        attachFileButton.isEnabled = !busy
        attachImageButton.isEnabled = !busy
        sendButton.isEnabled = !busy
        statusLabel.text = status
        statusLabel.foreground = if (busy) OpenCodeTheme.primary else OpenCodeTheme.textMuted
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
                "Images",
                "jpg",
                "jpeg",
                "png",
                "gif",
                "bmp",
                "webp"
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
        badge = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            background = OpenCodeTheme.attachmentBg
            border = RoundedBorder(
                radius = OpenCodeTheme.Radius.SM,
                borderColor = OpenCodeTheme.border,
                backgroundColor = OpenCodeTheme.attachmentBg
            )

            add(JLabel(if (name.length > 25) "${name.take(22)}..." else name).apply {
                foreground = OpenCodeTheme.attachmentText
                font = OpenCodeTheme.Typography.fontPrimary.deriveFont(Font.PLAIN, 11f)
            })

            add(JLabel("×").apply {
                foreground = OpenCodeTheme.textSecondary
                font = OpenCodeTheme.Typography.fontPrimary.deriveFont(Font.BOLD, 13f)
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                toolTipText = "Remove"
                addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseClicked(e: java.awt.event.MouseEvent) {
                        attachments.removeAll { it.name == name }
                        attachmentsPanel.remove(badge)
                        attachmentsPanel.revalidate()
                        attachmentsPanel.repaint()
                    }
                })
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

internal enum class FlatTone {
    DEFAULT,
    PRIMARY,
    SUCCESS,
    WARNING,
    DANGER
}

internal fun createSurfacePanel(layout: LayoutManager = BorderLayout()): JPanel {
    return JPanel(layout).apply {
        background = OpenCodeTheme.surface
        border = RoundedBorder(
            radius = OpenCodeTheme.Radius.MD,
            borderColor = OpenCodeTheme.border,
            backgroundColor = OpenCodeTheme.surface
        )
    }
}

internal fun createSectionTitle(text: String): JLabel {
    return JLabel(text).apply {
        font = OpenCodeTheme.Typography.fontPrimary.deriveFont(Font.BOLD, 14f)
        foreground = OpenCodeTheme.textPrimary
    }
}

internal fun createSectionDescription(text: String): JLabel {
    return JLabel(text).apply {
        font = OpenCodeTheme.Typography.fontPrimary.deriveFont(Font.PLAIN, 11f)
        foreground = OpenCodeTheme.textSecondary
    }
}

internal fun createFieldLabel(text: String): JLabel {
    return JLabel(text).apply {
        font = OpenCodeTheme.Typography.fontPrimary.deriveFont(Font.BOLD, 12f)
        foreground = OpenCodeTheme.textPrimary
    }
}

internal fun createValueLabel(text: String): JLabel {
    return JLabel(text).apply {
        font = OpenCodeTheme.Typography.fontPrimary.deriveFont(Font.PLAIN, 12f)
        foreground = OpenCodeTheme.textSecondary
    }
}

internal fun createFlatScrollPane(component: JComponent): JScrollPane {
    return JScrollPane(component).apply {
        border = null
        viewport.border = null
        background = OpenCodeTheme.background
        viewport.background = OpenCodeTheme.background
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    }
}

internal fun applyFlatButtonStyle(
    button: AbstractButton,
    tone: FlatTone = FlatTone.DEFAULT,
    compact: Boolean = false
) {
    val background = when (tone) {
        FlatTone.PRIMARY -> OpenCodeTheme.buttonPrimary
        FlatTone.SUCCESS -> OpenCodeTheme.successSoft
        FlatTone.WARNING -> OpenCodeTheme.warningSoft
        FlatTone.DANGER -> OpenCodeTheme.errorSoft
        FlatTone.DEFAULT -> OpenCodeTheme.buttonSecondary
    }
    val foreground = when (tone) {
        FlatTone.PRIMARY -> OpenCodeTheme.buttonPrimaryText
        FlatTone.SUCCESS -> OpenCodeTheme.success
        FlatTone.WARNING -> OpenCodeTheme.warning
        FlatTone.DANGER -> OpenCodeTheme.error
        FlatTone.DEFAULT -> OpenCodeTheme.buttonSecondaryText
    }

    button.background = background
    button.foreground = foreground
    button.isOpaque = true
    button.isBorderPainted = false
    button.isContentAreaFilled = true
    button.isFocusPainted = false
    button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    button.font = OpenCodeTheme.Typography.fontPrimary.deriveFont(
        if (tone == FlatTone.PRIMARY) Font.BOLD else Font.PLAIN,
        12f
    )
    button.border = if (compact) {
        JBUI.Borders.empty(6, 10)
    } else {
        JBUI.Borders.empty(8, 14)
    }
}

internal fun applyChipStyle(label: JLabel, tone: FlatTone = FlatTone.DEFAULT) {
    label.isOpaque = true
    label.font = OpenCodeTheme.Typography.fontPrimary.deriveFont(Font.PLAIN, 11f)
    label.border = JBUI.Borders.empty(5, 8)
    when (tone) {
        FlatTone.PRIMARY -> {
            label.background = OpenCodeTheme.primaryLight
            label.foreground = OpenCodeTheme.primary
        }
        FlatTone.SUCCESS -> {
            label.background = OpenCodeTheme.successSoft
            label.foreground = OpenCodeTheme.success
        }
        FlatTone.WARNING -> {
            label.background = OpenCodeTheme.warningSoft
            label.foreground = OpenCodeTheme.warning
        }
        FlatTone.DANGER -> {
            label.background = OpenCodeTheme.errorSoft
            label.foreground = OpenCodeTheme.error
        }
        FlatTone.DEFAULT -> {
            label.background = OpenCodeTheme.surfaceMuted
            label.foreground = OpenCodeTheme.textSecondary
        }
    }
}

internal fun applyInputStyle(component: JComponent) {
    component.background = OpenCodeTheme.surface
    component.foreground = OpenCodeTheme.textPrimary
    component.font = OpenCodeTheme.Typography.fontPrimary.deriveFont(12f)
    component.border = JBUI.Borders.compound(
        RoundedBorder(
            radius = OpenCodeTheme.Radius.SM,
            borderColor = OpenCodeTheme.border,
            backgroundColor = OpenCodeTheme.surface
        ),
        JBUI.Borders.empty(6, 8)
    )
    if (component is JTextField) {
        component.caretColor = OpenCodeTheme.primary
    }
}
