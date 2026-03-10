package ai.opencode.plugin.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

object OpenCodeAssistantPresentation {
    private val descriptors = mapOf(
        "atlas" to AssistantDescriptor("阿特拉斯", "计划执行"),
        "build" to AssistantDescriptor("构建", "实施执行"),
        "compaction" to AssistantDescriptor("压缩器", "上下文压缩"),
        "explore" to AssistantDescriptor("探索", "代码探索"),
        "general" to AssistantDescriptor("通用", "通用助手"),
        "hephaestus" to AssistantDescriptor("赫菲斯托斯", "深度代理"),
        "librarian" to AssistantDescriptor("图书管理员", "知识检索"),
        "metis" to AssistantDescriptor("墨提斯", "策略引擎"),
        "momus" to AssistantDescriptor("摩墨斯", "审查批评"),
        "multimodal-looker" to AssistantDescriptor("多模态观察者", "视觉分析"),
        "oracle" to AssistantDescriptor("神谕", "决策顾问"),
        "plan" to AssistantDescriptor("规划", "规划助手"),
        "prometheus" to AssistantDescriptor("普罗米修斯", "计划构建"),
        "sisyphus" to AssistantDescriptor("西西弗斯", "超级执行"),
        "summary" to AssistantDescriptor("总结", "摘要整理"),
        "title" to AssistantDescriptor("标题", "标题生成")
    )

    fun compactName(id: String?): String {
        val descriptor = descriptorOf(id) ?: return "使用配置默认值"
        return descriptor.name
    }

    fun displayName(id: String?): String {
        val descriptor = descriptorOf(id) ?: return "使用配置默认值"
        return "${descriptor.name}（${descriptor.role}）"
    }

    fun createRenderer(): ListCellRenderer<in String> = AssistantRenderer()

    private fun descriptorOf(id: String?): AssistantDescriptor? {
        val value = id?.trim().orEmpty()
        if (value.isBlank()) return null

        return descriptors[value] ?: AssistantDescriptor(
            name = value.split('-')
                .filter { it.isNotBlank() }
                .joinToString("") { token ->
                    token.replaceFirstChar { char ->
                        if (char.isLowerCase()) char.titlecase() else char.toString()
                    }
                }
                .ifBlank { value },
            role = value
        )
    }

    private data class AssistantDescriptor(
        val name: String,
        val role: String
    )

    private class AssistantRenderer : JPanel(BorderLayout()), ListCellRenderer<String> {
        private val titleLabel = JLabel()
        private val subtitleLabel = JLabel().apply {
            foreground = OpenCodeTheme.textSecondary
            font = OpenCodeTheme.Typography.fontPrimary.deriveFont(11f)
        }

        init {
            isOpaque = true
            border = BorderFactory.createEmptyBorder()
            titleLabel.font = OpenCodeTheme.Typography.fontPrimary.deriveFont(12f)

            add(titleLabel, BorderLayout.NORTH)
            add(subtitleLabel, BorderLayout.SOUTH)
        }

        override fun getListCellRendererComponent(
            list: JList<out String>?,
            value: String?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val descriptor = descriptorOf(value)
            val selectedBackground = OpenCodeTheme.primaryLight
            val selectedForeground = OpenCodeTheme.textPrimary
            val backgroundColor = if (isSelected) selectedBackground else list?.background ?: OpenCodeTheme.surface
            val foregroundColor = if (isSelected) selectedForeground else list?.foreground ?: OpenCodeTheme.textPrimary

            background = backgroundColor
            titleLabel.foreground = foregroundColor
            subtitleLabel.foreground = if (isSelected) selectedForeground else OpenCodeTheme.textSecondary

            if (descriptor == null) {
                titleLabel.text = "使用配置默认值"
                subtitleLabel.text = "完全遵循 opencode / oh-my-opencode 配置"
            } else {
                titleLabel.text = descriptor.name
                subtitleLabel.text = descriptor.role
            }

            border = if (index >= 0) {
                JBUI.Borders.empty(8, 10, 8, 10)
            } else {
                JBUI.Borders.empty(4, 8, 4, 8)
            }

            subtitleLabel.isVisible = index >= 0
            return this
        }
    }
}
