package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.scanner.audit.issues.AuditIssue
import com.bbh.me262.mcp.Tool
import com.bbh.me262.mcp.ToolOutput
import com.bbh.me262.scan.Issues
import com.bbh.me262.scan.ScanRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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

    private data class Selection(val source: String, val issues: List<AuditIssue>, val withDetail: Boolean)

    private fun select(arguments: JsonObject): Selection {
        val scanId = arguments["scan_id"]?.jsonPrimitive?.contentOrNull
        val minSev = Issues.parseMinSeverity(arguments["min_severity"]?.jsonPrimitive?.contentOrNull)
        val contains = arguments["contains"]?.jsonPrimitive?.contentOrNull
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull ?: 100
        val withDetail = arguments["detail"]?.jsonPrimitive?.booleanOrNull ?: false

        val all = if (scanId != null) {
            scans.audit(scanId)?.issues() ?: error("unknown scan id: $scanId")
        } else {
            api.siteMap().issues()
        }
        val picked = all.asSequence()
            .sortedByDescending { Issues.rank(it.severity()) }
            .filter { Issues.rank(it.severity()) >= minSev }
            .filter { contains == null || it.baseUrl().contains(contains) }
            .take(limit)
            .toList()
        return Selection(scanId ?: "site map", picked, withDetail)
    }

    override fun execute(arguments: JsonObject): String = format(select(arguments))

    override fun run(arguments: JsonObject): ToolOutput {
        val sel = select(arguments)
        val structured = buildJsonObject {
            put("source", sel.source)
            put("count", sel.issues.size)
            putJsonArray("issues") {
                for (issue in sel.issues) add(buildJsonObject {
                    put("name", issue.name())
                    put("severity", issue.severity().name)
                    put("confidence", issue.confidence().name)
                    put("url", issue.baseUrl())
                    if (sel.withDetail) {
                        runCatching { issue.detail() }.getOrNull()?.takeIf { it.isNotBlank() }
                            ?.let { put("detail", it.take(1500)) }
                    }
                })
            }
        }
        return ToolOutput(format(sel), structured)
    }

    private fun format(sel: Selection): String {
        if (sel.issues.isEmpty()) return "No issues in ${sel.source} (matching filters)."
        val sb = StringBuilder()
        for (issue in sel.issues) {
            sb.append("- ").append(Issues.line(issue))
            if (sel.withDetail) sb.append(Issues.detail(issue))
            sb.append('\n')
        }
        return "Issues from ${sel.source} (${sel.issues.size} shown):\n$sb"
    }
}
