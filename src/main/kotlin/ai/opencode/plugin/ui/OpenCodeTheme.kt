package ai.opencode.plugin.ui

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import javax.swing.UIManager

/**
 * OpenCode UI Theme - Modern Design System
 * 
 * Inspired by modern chat interfaces with support for both light and dark themes.
 * Uses JBColor for automatic theme switching.
 */
object OpenCodeTheme {
    
    // ===== Primary Colors =====
    val primary: JBColor
        get() = JBColor(Color(37, 99, 235), Color(86, 156, 214))
    
    val primaryHover: JBColor
        get() = JBColor(Color(29, 78, 216), Color(110, 167, 219))
    
    val primaryLight: JBColor
        get() = JBColor(Color(219, 234, 254), Color(38, 79, 120))
    
    // ===== Background Colors =====
    val background: JBColor
        get() = JBColor(Color(245, 247, 250), Color(30, 34, 39))
    
    val surface: JBColor
        get() = JBColor(Color(255, 255, 255), Color(43, 47, 54))
    
    val surfaceHover: JBColor
        get() = JBColor(Color(248, 250, 252), Color(50, 54, 61))

    val surfaceMuted: JBColor
        get() = JBColor(Color(240, 243, 247), Color(36, 40, 46))

    val surfaceAccent: JBColor
        get() = JBColor(Color(239, 246, 255), Color(32, 58, 92))
    
    val border: JBColor
        get() = JBColor(Color(214, 220, 228), Color(66, 72, 80))
    
    // ===== Text Colors =====
    val textPrimary: JBColor
        get() = JBColor(Color(31, 41, 55), Color(236, 240, 244))
    
    val textSecondary: JBColor
        get() = JBColor(Color(100, 116, 139), Color(160, 174, 192))
    
    val textMuted: JBColor
        get() = JBColor(Color(148, 163, 184), Color(124, 138, 156))
    
    // ===== User Message (Chat Bubble) =====
    val userMessageBg: JBColor
        get() = surfaceAccent
    
    val userMessageText: JBColor
        get() = textPrimary
    
    val userMessageSecondary: JBColor
        get() = primary
    
    // ===== Assistant Message (Chat Bubble) =====
    val assistantMessageBg: JBColor
        get() = surface
    
    val assistantMessageText: JBColor
        get() = textPrimary
    
    val assistantMessageBorder: JBColor
        get() = border
    
    // ===== Status Colors =====
    val success: JBColor
        get() = JBColor(Color(22, 163, 74), Color(95, 180, 117))
    
    val error: JBColor
        get() = JBColor(Color(220, 38, 38), Color(231, 111, 81))
    
    val warning: JBColor
        get() = JBColor(Color(217, 119, 6), Color(229, 179, 78))
    
    val info: JBColor
        get() = primary

    val successSoft: JBColor
        get() = JBColor(Color(236, 253, 245), Color(39, 61, 45))

    val warningSoft: JBColor
        get() = JBColor(Color(255, 247, 237), Color(72, 54, 35))

    val errorSoft: JBColor
        get() = JBColor(Color(254, 242, 242), Color(73, 40, 40))
    
    // ===== Code Block =====
    val codeBackground: JBColor
        get() = JBColor(Color(31, 41, 55), Color(24, 26, 31))
    
    val codeText: JBColor
        get() = JBColor(Color(226, 232, 240), Color(226, 232, 240))
    
    // ===== Spacing (in pixels) =====
    object Spacing {
        const val XS = 4
        const val SM = 8
        const val MD = 12
        const val LG = 16
        const val XL = 24
        const val XXL = 32
    }
    
    // ===== Border Radius =====
    object Radius {
        const val SM = 8
        const val MD = 12
        const val LG = 14
        const val XL = 18
        const val FULL = 999
    }
    
    // ===== Shadows (CSS-like, for reference) =====
    object Shadow {
        const val SM = "0 1px 2px 0 rgba(0, 0, 0, 0.05)"
        const val MD = "0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)"
        const val LG = "0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)"
    }
    
    // ===== Typography =====
    object Typography {
        val fontPrimary: Font
            get() = (UIManager.getFont("TextArea.font") ?: UIManager.getFont("Label.font") ?: Font(Font.DIALOG, Font.PLAIN, 13))
                .deriveFont(Font.PLAIN, 13f)
        
        val fontMonospace: Font
            get() = (UIManager.getFont("TextArea.font") ?: Font(Font.MONOSPACED, Font.PLAIN, 13))
                .deriveFont(Font.PLAIN, 13f)
        
        val fontSizeXS: Int = 10
        val fontSizeSM: Int = 11
        val fontSizeMD: Int = 13
        val fontSizeLG: Int = 15
        val fontSizeXL: Int = 18
        val fontSizeXXL: Int = 24
    }
    
    // ===== Attachment Badge Colors =====
    val attachmentBg: JBColor
        get() = surfaceMuted
    
    val attachmentText: JBColor
        get() = textSecondary
    
    // ===== Input Area =====
    val inputBackground: JBColor
        get() = surface
    
    val inputBorder: JBColor
        get() = border
    
    val inputBorderFocus: JBColor
        get() = primary
    
    // ===== Button Styles =====
    val buttonPrimary: JBColor
        get() = primary
    
    val buttonPrimaryText: JBColor
        get() = JBColor(Color(255, 255, 255), Color(255, 255, 255))
    
    val buttonSecondary: JBColor
        get() = surfaceMuted
    
    val buttonSecondaryText: JBColor
        get() = textPrimary
    
    // ===== Tab Colors =====
    val tabSelected: JBColor
        get() = surface
    
    val tabUnselected: JBColor
        get() = surfaceMuted
}
