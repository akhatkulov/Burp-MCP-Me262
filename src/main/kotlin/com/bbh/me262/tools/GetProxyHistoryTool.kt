package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.Tool
import com.bbh.me262.util.HistoryFilters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Read recent Proxy HTTP history entries, with filtering. */
class GetProxyHistoryTool(private val api: MontoyaApi) : Tool {
    override val name = "get_proxy_history"
    override val description =
        "Return Proxy HTTP history (newest first) as '#index method url -> status (len)'. " +
        "Filters: 'contains' (URL substring), 'regex' (URL), 'method', 'status' (int or array), " +
        "'mime_type' (e.g. JSON, HTML), 'min_length'/'max_length' (response body bytes). " +
        "'limit' caps the number of rows; omit it (or 0) to return ALL matching entries — " +
        "prefer a filter when the history is large, or the output gets huge. " +
        "'include_body' adds a short preview. Use the '#index' with " +
        "get_proxy_entry to pull the full request/response."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("limit") { put("type", "integer"); put("description", "max entries; omit or 0 = all matching entries") }
            putJsonObject("contains") { put("type", "string"); put("description", "only URLs containing this substring") }
            putJsonObject("regex") { put("type", "string"); put("description", "only URLs matching this regex") }
            putJsonObject("method") { put("type", "string"); put("description", "only this HTTP method") }
            putJsonObject("status") { put("type", "array"); putJsonObject("items") { put("type", "integer") }; put("description", "status code(s) to keep (int or array)") }
            putJsonObject("mime_type") { put("type", "string"); put("description", "response MIME contains, e.g. JSON, HTML, SCRIPT") }
            putJsonObject("min_length") { put("type", "integer"); put("description", "min response body bytes") }
            putJsonObject("max_length") { put("type", "integer"); put("description", "max response body bytes") }
            putJsonObject("include_body") { put("type", "boolean"); put("description", "include a short response body preview") }
        }
    }

    override fun execute(arguments: JsonObject): String {
        // No 'limit' (or <= 0) means "return everything that matches"; filters do the narrowing.
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: Int.MAX_VALUE
        val includeBody = arguments["include_body"]?.jsonPrimitive?.booleanOrNull ?: false
        val filters = HistoryFilters.from(arguments)

        val history = api.proxy().history()
        val sb = StringBuilder()
        var n = 0
        // Chronological index is stable (history only appends), so get_proxy_entry(index) maps back.
        for (idx in history.indices.reversed()) {
            val item = history[idx]
            val req = item.finalRequest()
            val resp = item.response()
            val status = resp?.statusCode()?.toInt()
            val mimeName = resp?.let { runCatching { it.mimeType().name }.getOrNull() }
            val bodyLen = resp?.body()?.length() ?: 0
            if (!filters.matches(req.method(), req.url(), status, mimeName, bodyLen)) continue
            sb.append("#$idx ${req.method()} ${req.url()} -> ${status ?: "-"} (${bodyLen}b)\n")
            if (includeBody && resp != null) {
                sb.append("   ${resp.bodyToString().take(300).replace("\n", " ")}\n")
            }
            if (++n >= limit) break
        }
        return if (n == 0) "No matching proxy history entries." else "Showing $n entries (newest first):\n$sb"
    }
}
