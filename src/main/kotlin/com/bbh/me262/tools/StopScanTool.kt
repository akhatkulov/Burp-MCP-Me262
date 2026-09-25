package com.bbh.me262.tools

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

/**
 * Stop (cancel) a scan started via Me262 and drop its handle. Both Audit and
 * Crawl extend Montoya's ScanTask, which exposes delete().
 */
class StopScanTool(private val scans: ScanRegistry) : Tool {
    override val name = "stop_scan"
    override val description =
        "Stop and delete a scan started via Me262 (audit or crawl). Required 'scan_id' " +
        "(as returned by start_active_scan/start_crawl and listed by scan_status)."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("scan_id") { put("type", "string") }
        }
        putJsonArray("required") { add("scan_id") }
    }

    override fun execute(arguments: JsonObject): String {
        val id = arguments["scan_id"]?.jsonPrimitive?.contentOrNull ?: error("'scan_id' is required")
        val task = scans.audit(id) ?: scans.crawl(id) ?: error("unknown scan id: $id")
        runCatching { task.delete() }
            .onFailure { error("failed to stop $id: ${it.message}") }
        scans.remove(id)
        return "Stopped and removed $id."
    }
}
