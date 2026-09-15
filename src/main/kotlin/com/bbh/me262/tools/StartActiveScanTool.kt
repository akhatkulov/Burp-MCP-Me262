package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.scanner.AuditConfiguration
import burp.api.montoya.scanner.BuiltInAuditConfiguration
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Start a Burp Pro active audit. This is the key capability the stock MCP omits:
 * it exposes the Pro Scanner engine, and every installed extension that registers
 * scan checks (plus BChecks) participates automatically.
 */
class StartActiveScanTool(private val api: MontoyaApi) : Tool {
    override val name = "start_active_scan"
    override val description =
        "Start a Burp Pro active audit against a URL. Returns immediately; the scan runs inside Burp. " +
        "Read findings via the Burp Dashboard (get_scanner_issues is on the roadmap). PRO ONLY."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "target URL to audit, e.g. https://example.com/")
            }
        }
        putJsonArray("required") { add("url") }
    }

    override fun execute(arguments: JsonObject): String {
        val url = arguments["url"]?.jsonPrimitive?.contentOrNull ?: error("'url' is required")
        val config = AuditConfiguration.auditConfiguration(
            BuiltInAuditConfiguration.LEGACY_ACTIVE_AUDIT_CHECKS,
        )
        val audit = api.scanner().startAudit(config)
        audit.addRequest(HttpRequest.httpRequestFromUrl(url))
        return "Active audit started for $url (LEGACY_ACTIVE_AUDIT_CHECKS). Track it in Burp > Dashboard."
    }
}
