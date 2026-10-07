package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.Tool
import com.bbh.me262.util.HistoryFilters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Query Burp's site map (discovered content), with filtering. */
class SitemapQueryTool(private val api: MontoyaApi) : Tool {
    override val name = "sitemap_query"
    override val description =
        "List entries from Burp's site map as 'method url -> status (len)'. Filters: 'contains' (URL substring), " +
        "'regex' (URL), 'method', 'status' (int or array), 'mime_type' (e.g. JSON, HTML), " +
        "'min_length'/'max_length' (response body bytes). 'limit' caps rows; omit it (or 0) for ALL matching entries."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("contains") { put("type", "string") }
            putJsonObject("regex") { put("type", "string") }
            putJsonObject("method") { put("type", "string") }
            putJsonObject("status") { put("type", "array"); putJsonObject("items") { put("type", "integer") } }
            putJsonObject("mime_type") { put("type", "string") }
            putJsonObject("min_length") { put("type", "integer") }
            putJsonObject("max_length") { put("type", "integer") }
            putJsonObject("limit") { put("type", "integer") }
        }
    }

    override fun execute(arguments: JsonObject): String {
        // No 'limit' (or <= 0) means "return everything that matches"; filters do the narrowing.
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: Int.MAX_VALUE
        val filters = HistoryFilters.from(arguments)
        val sb = StringBuilder()
        var n = 0
        for (rr in api.siteMap().requestResponses()) {
            val req = rr.request()
            val resp = rr.response()
            val status = resp?.statusCode()?.toInt()
            val mimeName = resp?.let { runCatching { it.mimeType().name }.getOrNull() }
            val bodyLen = resp?.body()?.length() ?: 0
            if (!filters.matches(req.method(), req.url(), status, mimeName, bodyLen)) continue
            sb.append("${req.method()} ${req.url()} -> ${status ?: "-"} (${bodyLen}b)\n")
            if (++n >= limit) break
        }
        return if (n == 0) "No matching site map entries." else "Site map ($n shown):\n$sb"
    }
}
