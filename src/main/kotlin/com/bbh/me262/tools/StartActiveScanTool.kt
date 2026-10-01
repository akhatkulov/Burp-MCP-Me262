package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.scanner.AuditConfiguration
import burp.api.montoya.scanner.BuiltInAuditConfiguration
import com.bbh.me262.mcp.Tool
import com.bbh.me262.safety.RoeGuard
import com.bbh.me262.scan.ScanRegistry
import com.bbh.me262.scan.Scans
import com.bbh.me262.util.Requests
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Start a Burp Pro active audit. Exposes the Pro Scanner engine; every installed
 * extension that registers scan checks (plus BChecks) participates automatically,
 * so auditing a real request is how you get e.g. HTTP Request Smuggler or Active
 * Scan++ to run against it — far faster than probing by hand.
 *
 * Three ways to say what to audit:
 *   url    a simple GET against that URL (bare, few insertion points)
 *   raw    a specific crafted request (+ host/port/tls, + the usual
 *          method/path/headers/cookies/cookie_file/use_cookie_jar/body)
 *   index  a Proxy history entry by its #index (from get_proxy_history)
 *
 * Burp derives insertion points from the request automatically (params, headers,
 * body, framing), which is exactly what the smuggling/desync checks need.
 */
class StartActiveScanTool(
    private val api: MontoyaApi,
    private val scans: ScanRegistry,
    private val roe: RoeGuard,
) : Tool {
    override val name = "start_active_scan"
    override val description =
        "Start a Burp Pro active audit and return a scan id immediately; poll with scan_status and read " +
        "findings with get_scanner_issues. Every installed scan-check extension (Active Scan++, HTTP Request " +
        "Smuggler, BChecks, …) participates. Target it by 'url' (simple GET), by 'raw'+host/port/tls (a " +
        "specific crafted request, with the usual method/path/headers/cookies/cookie_file/use_cookie_jar/body), " +
        "or by 'index' (a Proxy history entry from get_proxy_history). PRO ONLY."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") { put("type", "string"); put("description", "target URL for a simple GET audit") }
            putJsonObject("raw") { put("type", "string"); put("description", "raw request text to audit (needs host/port/tls)") }
            putJsonObject("host") { put("type", "string") }
            putJsonObject("port") { put("type", "integer") }
            putJsonObject("tls") { put("type", "boolean") }
            putJsonObject("method") { put("type", "string") }
            putJsonObject("path") { put("type", "string") }
            putJsonObject("headers") { put("type", "array"); putJsonObject("items") { put("type", "string") } }
            putJsonObject("cookies") { put("type", "string") }
            putJsonObject("cookie_file") { put("type", "string") }
            putJsonObject("use_cookie_jar") { put("type", "boolean") }
            putJsonObject("body") { put("type", "string") }
            putJsonObject("index") {
                put("type", "integer")
                put("description", "Proxy history #index to audit (from get_proxy_history)")
            }
        }
    }

    override fun execute(arguments: JsonObject): String {
        val request = resolveRequest(arguments)
        roe.requireInScope(request.url())
        val config = AuditConfiguration.auditConfiguration(
            BuiltInAuditConfiguration.LEGACY_ACTIVE_AUDIT_CHECKS,
        )
        val audit = Scans.startAudit(api, config)
        audit.addRequest(request)
        val id = scans.addAudit(audit)
        return "Active audit started for ${request.method()} ${request.url()} -> scan id: $id\n" +
            "Installed scan-check extensions participate automatically. " +
            "Use scan_status(scan_id=$id) and get_scanner_issues(scan_id=$id)."
    }

    private fun resolveRequest(arguments: JsonObject): HttpRequest {
        val index = arguments["index"]?.jsonPrimitive?.intOrNull
        if (index != null) {
            val history = api.proxy().history()
            require(index in history.indices) {
                "no proxy history entry at index $index (history holds ${history.size}, valid 0..${history.size - 1})"
            }
            return history[index].finalRequest()
        }
        // url or raw(+host/port/tls) plus the structured fields, via the shared builder.
        return Requests.build(arguments, api)
    }
}
