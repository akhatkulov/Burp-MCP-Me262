package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Query Burp's site map (discovered content), filtered by URL substring. */
class SitemapQueryTool(private val api: MontoyaApi) : Tool {
    override val name = "sitemap_query"
    override val description =
        "List entries from Burp's site map (method, URL, status). Optional 'contains' URL filter " +
        "and 'limit' (default 100)."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("contains") { put("type", "string") }
            putJsonObject("limit") { put("type", "integer") }
        }
    }

    override fun execute(arguments: JsonObject): String {
        val contains = arguments["contains"]?.jsonPrimitive?.contentOrNull
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull ?: 100
        val sb = StringBuilder()
        var n = 0
        for (rr in api.siteMap().requestResponses()) {
            val req = rr.request()
            val url = req.url()
            if (contains != null && !url.contains(contains)) continue
            val status = rr.response()?.statusCode()?.toString() ?: "-"
            sb.append("${req.method()} $url -> $status\n")
            if (++n >= limit) break
        }
        return if (n == 0) "No matching site map entries." else "Site map ($n shown):\n$sb"
    }
}
