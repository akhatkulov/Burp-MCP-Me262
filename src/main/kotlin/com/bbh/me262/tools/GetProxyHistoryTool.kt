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

/** Read recent Proxy HTTP history entries. */
class GetProxyHistoryTool(private val api: MontoyaApi) : Tool {
    override val name = "get_proxy_history"
    override val description =
        "Return recent Proxy HTTP history (method, URL, status). Optional 'limit' (default 50) " +
        "and 'contains' substring filter on the URL."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "max entries, default 50")
            }
            putJsonObject("contains") {
                put("type", "string")
                put("description", "only URLs containing this substring")
            }
        }
    }

    override fun execute(arguments: JsonObject): String {
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull ?: 50
        val contains = arguments["contains"]?.jsonPrimitive?.contentOrNull

        val sb = StringBuilder()
        var n = 0
        for (item in api.proxy().history().asReversed()) {
            val req = item.finalRequest()
            val url = req.url()
            if (contains != null && !url.contains(contains)) continue
            val status = item.response()?.statusCode()?.toString() ?: "-"
            sb.append("${req.method()} $url -> $status\n")
            if (++n >= limit) break
        }
        return if (n == 0) "No matching proxy history entries." else "Showing $n entries:\n$sb"
    }
}
