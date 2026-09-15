package com.bbh.me262.ui

import com.bbh.me262.mcp.ToolRegistry
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

/** Minimal status tab shown inside Burp. */
object Me262Tab {
    fun build(url: String, auth: String, allowOutOfScope: Boolean, registry: ToolRegistry): Component {
        val panel = JPanel(BorderLayout())
        val header = JLabel(
            "<html><h2>Burp-MCP-Me262</h2>" +
                "MCP SSE endpoint: <b>$url</b><br>" +
                "Auth: $auth &nbsp;&nbsp; ROE out-of-scope override: <b>$allowOutOfScope</b><br>" +
                "Tools: <b>${registry.size()}</b></html>",
        )
        header.border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        panel.add(header, BorderLayout.NORTH)

        val area = JTextArea(registry.list().joinToString("\n") { "- ${it.name}:  ${it.description}" })
        area.isEditable = false
        area.lineWrap = true
        area.wrapStyleWord = true
        area.border = BorderFactory.createEmptyBorder(8, 12, 12, 12)
        panel.add(JScrollPane(area), BorderLayout.CENTER)
        return panel
    }
}
