package com.bbh.me262.tools

import com.bbh.me262.mcp.Tool
import com.bbh.me262.scan.ScanRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Report progress of scans launched via Me262. */
class ScanStatusTool(private val scans: ScanRegistry) : Tool {
    override val name = "scan_status"
    override val description =
        "Show progress of scans started via Me262. With 'scan_id' shows that scan; " +
        "without, lists all tracked scans."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("scan_id") { put("type", "string") }
        }
    }

    override fun execute(arguments: JsonObject): String {
        val scanId = arguments["scan_id"]?.jsonPrimitive?.contentOrNull
        if (scanId != null) return one(scanId)

        val all = scans.auditIds() + scans.crawlIds()
        if (all.isEmpty()) return "No scans started yet."
        return all.joinToString("\n") { one(it) }
    }

    private fun one(id: String): String {
        scans.audit(id)?.let { a ->
            return "$id [audit]  requests=${a.requestCount()} insertionPoints=${a.insertionPointCount()} " +
                "issues=${a.issues().size}  status=\"${runCatching { a.statusMessage() }.getOrDefault("")}\""
        }
        scans.crawl(id)?.let { c ->
            return "$id [crawl]  requests=${c.requestCount()}  status=\"${runCatching { c.statusMessage() }.getOrDefault("")}\""
        }
        return "$id: unknown scan id"
    }
}
