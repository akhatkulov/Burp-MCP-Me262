package com.bbh.me262.ui

import com.bbh.me262.mcp.ToolRegistry
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JScrollPane
import javax.swing.JTextArea

/**
 * Status tab shown inside Burp. Uses plain text only — Burp's Look-and-Feel
 * disables HTML rendering in Swing components, so an <html> JLabel shows raw
 * markup. A monospaced JTextArea renders reliably.
 */
object Me262Tab {
    fun build(url: String, auth: String, allowOutOfScope: Boolean, registry: ToolRegistry): Component {
        val sb = StringBuilder()
        sb.append("Burp-MCP-Me262\n")
        sb.append("=".repeat(60)).append('\n')
        sb.append("MCP SSE endpoint : ").append(url).append('\n')
        sb.append("Auth             : ").append(auth).append('\n')
        sb.append("Out-of-scope ovr : ").append(allowOutOfScope).append('\n')
        sb.append("Tools            : ").append(registry.size()).append("\n\n")
        sb.append("Tools\n")
        sb.append("-".repeat(60)).append('\n')
        for (t in registry.list()) {
            sb.append("  ").append(t.name).append('\n')
            sb.append("      ").append(t.description).append("\n")
        }

        val area = JTextArea(sb.toString())
        area.isEditable = false
        area.lineWrap = true
        area.wrapStyleWord = true
        area.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        area.caretPosition = 0
        area.border = BorderFactory.createEmptyBorder(10, 12, 12, 12)

        val panel = javax.swing.JPanel(BorderLayout())
        panel.add(JScrollPane(area), BorderLayout.CENTER)
        return panel
    }
}
