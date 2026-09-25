package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.scanner.AuditConfiguration
import burp.api.montoya.scanner.BuiltInAuditConfiguration
import com.bbh.me262.mcp.Tool
import com.bbh.me262.safety.RoeGuard
import com.bbh.me262.scan.ScanRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Start a Burp Pro passive audit against a URL. Passive checks analyse the
 * request/response without sending active attack payloads, so they are cheaper
 * and safer than start_active_scan while still driving every registered passive
 * scan check (and BChecks). Returns a scan id read via scan_status /
 * get_scanner_issues, like the active scan. PRO ONLY.
 */
class PassiveScanTool(
    private val api: MontoyaApi,
    private val scans: ScanRegistry,
    private val roe: RoeGuard,
) : Tool {
    override val name = "passive_scan"
    override val description =
        "Start a Burp Pro passive audit against a URL (no active attack payloads sent). " +
        "Returns a scan id; poll with scan_status and read findings with get_scanner_issues. PRO ONLY."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "target URL to passively audit, e.g. https://example.com/")
            }
        }
        putJsonArray("required") { add("url") }
    }

    override fun execute(arguments: JsonObject): String {
        val url = arguments["url"]?.jsonPrimitive?.contentOrNull ?: error("'url' is required")
        roe.requireInScope(url)
        val config = AuditConfiguration.auditConfiguration(
            BuiltInAuditConfiguration.LEGACY_PASSIVE_AUDIT_CHECKS,
        )
        val audit = api.scanner().startAudit(config)
        audit.addRequest(HttpRequest.httpRequestFromUrl(url))
        val id = scans.addAudit(audit)
        return "Passive audit started for $url -> scan id: $id\n" +
            "Use scan_status(scan_id=$id) and get_scanner_issues(scan_id=$id)."
    }
}
