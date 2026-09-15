package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.scanner.ReportFormat
import com.bbh.me262.mcp.Tool
import com.bbh.me262.scan.ScanRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Paths

/** Generate a Pro Scanner report (HTML/XML) for a scan or the whole site map. PRO. */
class GenerateReportTool(
    private val api: MontoyaApi,
    private val scans: ScanRegistry,
) : Tool {
    override val name = "generate_report"
    override val description =
        "Write a Burp Scanner report to a file. Args: 'path' (output file), " +
        "optional 'format' (HTML|XML, default HTML), optional 'scan_id' (else site map). PRO ONLY."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "absolute output path, e.g. /tmp/report.html")
            }
            putJsonObject("format") { put("type", "string"); put("description", "HTML or XML") }
            putJsonObject("scan_id") { put("type", "string") }
        }
        putJsonArray("required") { add("path") }
    }

    override fun execute(arguments: JsonObject): String {
        val path = arguments["path"]?.jsonPrimitive?.contentOrNull ?: error("'path' is required")
        val fmtName = arguments["format"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "HTML"
        val format = runCatching { ReportFormat.valueOf(fmtName) }.getOrElse { error("format must be HTML or XML") }
        val scanId = arguments["scan_id"]?.jsonPrimitive?.contentOrNull

        val issues = if (scanId != null) {
            scans.audit(scanId)?.issues() ?: error("unknown scan id: $scanId")
        } else {
            api.siteMap().issues()
        }
        if (issues.isEmpty()) return "No issues to report for ${scanId ?: "site map"}."

        api.scanner().generateReport(issues, format, Paths.get(path))
        return "Wrote $format report with ${issues.size} issues to $path"
    }
}
