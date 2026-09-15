package com.bbh.me262

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.McpServer
import com.bbh.me262.mcp.ToolRegistry
import com.bbh.me262.scan.ScanRegistry
import com.bbh.me262.tools.GenerateReportTool
import com.bbh.me262.tools.GetProxyHistoryTool
import com.bbh.me262.tools.GetScannerIssuesTool
import com.bbh.me262.tools.ScanStatusTool
import com.bbh.me262.tools.SendHttpRequestTool
import com.bbh.me262.tools.StartActiveScanTool
import com.bbh.me262.tools.StartCrawlTool

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
        log.logToOutput("Burp-MCP-Me262 v0.2.0 loading...")

        val host = System.getProperty("me262.host") ?: "127.0.0.1"
        val port = (System.getProperty("me262.port") ?: "9262").toIntOrNull() ?: 9262

        val scans = ScanRegistry()
        val registry = ToolRegistry()
            // v0.1 core
            .register(SendHttpRequestTool(api))
            .register(GetProxyHistoryTool(api))
            // v0.2 scanner loop
            .register(StartActiveScanTool(api, scans))
            .register(StartCrawlTool(api, scans))
            .register(ScanStatusTool(scans))
            .register(GetScannerIssuesTool(api, scans))
            .register(GenerateReportTool(api, scans))

        val mcp = McpServer(host, port, registry, log, serverVersion = "0.2.0")
        mcp.start()
        server = mcp

        api.extension().registerUnloadingHandler {
            log.logToOutput("Burp-MCP-Me262 unloading...")
            server?.stop()
        }

        log.logToOutput("Burp-MCP-Me262 ready -> http://$host:$port/  (${registry.size()} tools)")
    }
}
