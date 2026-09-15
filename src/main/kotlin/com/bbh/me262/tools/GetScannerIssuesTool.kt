package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.Tool
import com.bbh.me262.scan.Issues
import com.bbh.me262.scan.ScanRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Read Scanner audit issues, from a specific scan or the whole site map. PRO. */
class GetScannerIssuesTool(
    private val api: MontoyaApi,
    private val scans: ScanRegistry,
) : Tool {
    override val name = "get_scanner_issues"
    override val description =
        "List Burp Scanner issues. Optional 'scan_id' (else the whole site map), " +
        "'min_severity' (HIGH|MEDIUM|LOW|INFORMATION), 'contains' URL filter, " +
        "'limit' (default 100), and 'detail' (include issue detail text). PRO ONLY."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("scan_id") { put("type", "string") }
            putJsonObject("min_severity") { put("type", "string") }
            putJsonObject("contains") { put("type", "string") }
            putJsonObject("limit") { put("type", "integer") }
            putJsonObject("detail") { put("type", "boolean") }
        }
    }

    override fun execute(arguments: JsonObject): String {
        val scanId = arguments["scan_id"]?.jsonPrimitive?.contentOrNull
        val minSev = Issues.parseMinSeverity(arguments["min_severity"]?.jsonPrimitive?.contentOrNull)
        val contains = arguments["contains"]?.jsonPrimitive?.contentOrNull
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull ?: 100
        val withDetail = arguments["detail"]?.jsonPrimitive?.booleanOrNull ?: false

        val issues = if (scanId != null) {
            scans.audit(scanId)?.issues() ?: error("unknown scan id: $scanId")
        } else {
            api.siteMap().issues()
        }

        val sb = StringBuilder()
        var n = 0
        for (issue in issues.sortedByDescending { Issues.rank(it.severity()) }) {
            if (Issues.rank(issue.severity()) < minSev) continue
            if (contains != null && !issue.baseUrl().contains(contains)) continue
            sb.append("- ").append(Issues.line(issue))
            if (withDetail) sb.append(Issues.detail(issue))
            sb.append('\n')
            if (++n >= limit) break
        }
        val src = scanId ?: "site map"
        return if (n == 0) "No issues in $src (matching filters)." else "Issues from $src ($n shown):\n$sb"
    }
}
