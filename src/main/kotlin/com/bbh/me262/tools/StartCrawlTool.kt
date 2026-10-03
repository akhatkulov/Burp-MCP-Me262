package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.scanner.CrawlConfiguration
import com.bbh.me262.mcp.Tool
import com.bbh.me262.safety.RoeGuard
import com.bbh.me262.scan.ScanRegistry
import com.bbh.me262.scan.Scans
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Start a Burp crawl from one or more seed URLs. PRO ONLY. */
class StartCrawlTool(
    private val api: MontoyaApi,
    private val scans: ScanRegistry,
    private val roe: RoeGuard,
) : Tool {
    override val name = "start_crawl"
    override val description =
        "Start a Burp crawl from a seed URL to discover content. Returns a scan id; " +
        "poll with scan_status. The seed must be in Burp's Target scope; links found during the crawl " +
        "are followed by Burp's crawler under its own crawl settings, not re-checked by Me262. PRO ONLY."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "seed URL to crawl, e.g. https://example.com/")
            }
        }
        putJsonArray("required") { add("url") }
    }

    override fun execute(arguments: JsonObject): String {
        val url = arguments["url"]?.jsonPrimitive?.contentOrNull ?: error("'url' is required")
        roe.requireInScope(url)
        val crawl = Scans.startCrawl(api, CrawlConfiguration.crawlConfiguration(url))
        val id = scans.addCrawl(crawl)
        return "Crawl started from $url -> scan id: $id\nUse scan_status(scan_id=$id)."
    }
}
