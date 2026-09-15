package com.bbh.me262

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.McpServer
import com.bbh.me262.mcp.ToolRegistry
import com.bbh.me262.tools.GetProxyHistoryTool
import com.bbh.me262.tools.SendHttpRequestTool
import com.bbh.me262.tools.StartActiveScanTool

/**
 * Burp-MCP-Me262 — our own Montoya-based MCP server for Burp Suite Pro.
 *
 * Discovered by Burp via META-INF/services/burp.api.montoya.BurpExtension.
 * Host/port overridable with -Dme262.host / -Dme262.port.
 */
class Me262Extension : BurpExtension {

    private var server: McpServer? = null

    override fun initialize(api: MontoyaApi) {
        api.extension().setName("Burp-MCP-Me262")
        val log = api.logging()
        log.logToOutput("Burp-MCP-Me262 v0.1.0 loading...")

        val host = System.getProperty("me262.host") ?: "127.0.0.1"
        val port = (System.getProperty("me262.port") ?: "9262").toIntOrNull() ?: 9262

        val registry = ToolRegistry()
            .register(SendHttpRequestTool(api))
            .register(GetProxyHistoryTool(api))
            .register(StartActiveScanTool(api))

        val mcp = McpServer(host, port, registry, log)
        mcp.start()
        server = mcp

        api.extension().registerUnloadingHandler {
            log.logToOutput("Burp-MCP-Me262 unloading...")
            server?.stop()
        }

        log.logToOutput("Burp-MCP-Me262 ready -> http://$host:$port/  (${registry.size()} tools)")
    }
}
