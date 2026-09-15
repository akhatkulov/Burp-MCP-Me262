package com.bbh.me262

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import com.bbh.me262.collab.CollaboratorHolder
import com.bbh.me262.mcp.McpServer
import com.bbh.me262.mcp.ToolRegistry
import com.bbh.me262.safety.RoeGuard
import com.bbh.me262.scan.ScanRegistry
import com.bbh.me262.ui.Me262Tab
import com.bbh.me262.tools.CollaboratorInteractionsTool
import com.bbh.me262.tools.CollaboratorPayloadTool
import com.bbh.me262.tools.FuzzTool
import com.bbh.me262.tools.GetWebsocketHistoryTool
import com.bbh.me262.tools.SendToComparerTool
import com.bbh.me262.tools.ExportBurpConfigTool
import com.bbh.me262.tools.ImportBurpConfigTool
import com.bbh.me262.tools.RandomStringTool
import com.bbh.me262.tools.SendToIntruderTool
import com.bbh.me262.tools.SendToOrganizerTool
import com.bbh.me262.tools.SendToRepeaterTool
import com.bbh.me262.tools.SetTaskEngineTool
import com.bbh.me262.tools.TransformTool
import com.bbh.me262.tools.GenerateReportTool
import com.bbh.me262.tools.GetProxyHistoryTool
import com.bbh.me262.tools.GetScannerIssuesTool
import com.bbh.me262.tools.ImportBCheckTool
import com.bbh.me262.tools.ScanStatusTool
import com.bbh.me262.tools.ScopeAddTool
import com.bbh.me262.tools.ScopeCheckTool
import com.bbh.me262.tools.ScopeRemoveTool
import com.bbh.me262.tools.SendHttpRequestTool
import com.bbh.me262.tools.SetInterceptTool
import com.bbh.me262.tools.SitemapQueryTool
import com.bbh.me262.tools.StartActiveScanTool
import com.bbh.me262.tools.StartCrawlTool

/**
 * Burp-MCP-Me262 — our own Montoya-based MCP server for Burp Suite Pro.
 *
 * Discovered by Burp via META-INF/services/burp.api.montoya.BurpExtension.
 * System properties:
 *   -Dme262.host / -Dme262.port      bind address (default 127.0.0.1:9262)
 *   -Dme262.token                    require Authorization: Bearer <token>
 *   -Dme262.allowOutOfScope=true     disable the ROE scope guard (lab only)
 */
class Me262Extension : BurpExtension {

    private var server: McpServer? = null

    override fun initialize(api: MontoyaApi) {
        api.extension().setName("Burp-MCP-Me262")
        val log = api.logging()
        log.logToOutput("Burp-MCP-Me262 v0.8.0 loading...")

        val host = System.getProperty("me262.host") ?: "127.0.0.1"
        val port = (System.getProperty("me262.port") ?: "9262").toIntOrNull() ?: 9262
        val token = System.getProperty("me262.token")?.takeIf { it.isNotBlank() }
        val allowOutOfScope = System.getProperty("me262.allowOutOfScope")?.toBoolean() ?: false

        val scans = ScanRegistry()
        val roe = RoeGuard(api)
        val collab = CollaboratorHolder(api, log)

        val registry = ToolRegistry()
            // v0.1 core
            .register(SendHttpRequestTool(api))
            .register(GetProxyHistoryTool(api))
            // v0.3 native fuzzer (ROE-guarded)
            .register(FuzzTool(api, roe))
            // v0.2 scanner loop (active ones ROE-guarded)
            .register(StartActiveScanTool(api, scans, roe))
            .register(StartCrawlTool(api, scans, roe))
            .register(ScanStatusTool(scans))
            .register(GetScannerIssuesTool(api, scans))
            .register(GenerateReportTool(api, scans))
            // v0.4 visibility & control
            .register(ScopeCheckTool(api))
            .register(ScopeAddTool(api))
            .register(SitemapQueryTool(api))
            .register(CollaboratorPayloadTool(collab))
            .register(CollaboratorInteractionsTool(collab))
            // v0.5 control + bchecks
            .register(ScopeRemoveTool(api))
            .register(SetInterceptTool(api))
            .register(ImportBCheckTool(api))
            // v0.6 send-to, utilities, config, task engine
            .register(SendToRepeaterTool(api))
            .register(SendToIntruderTool(api))
            .register(SendToOrganizerTool(api))
            .register(TransformTool(api))
            .register(RandomStringTool(api))
            .register(SetTaskEngineTool(api))
            .register(ExportBurpConfigTool(api))
            .register(ImportBurpConfigTool(api))
            // v0.7 niche
            .register(GetWebsocketHistoryTool(api))
            .register(SendToComparerTool(api))

        val mcp = McpServer(host, port, registry, log, serverVersion = "0.8.0", authToken = token)
        mcp.start()
        server = mcp

        val url = "http://$host:$port/"
        val auth = if (token != null) "token-protected" else "no-auth (loopback)"
        runCatching {
            api.userInterface().registerSuiteTab("Me262", Me262Tab.build(url, auth, allowOutOfScope, registry))
        }.onFailure { log.logToError("[Me262] suite tab unavailable: ${it.message}") }

        api.extension().registerUnloadingHandler {
            log.logToOutput("Burp-MCP-Me262 unloading...")
            server?.stop()
        }

        log.logToOutput("Burp-MCP-Me262 ready -> $url  (${registry.size()} tools, $auth)")
    }
}
