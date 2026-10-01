package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Return the full request and response for one Proxy history entry by its '#index'
 * (as shown by get_proxy_history). This is the "show me the body" companion:
 * get_proxy_history returns metadata; this returns the actual bytes.
 */
class GetProxyEntryTool(private val api: MontoyaApi) : Tool {
    override val name = "get_proxy_entry"
    override val description =
        "Return the full request and response for a Proxy history entry by '#index' (from get_proxy_history). " +
        "Optional 'max_body' caps each body (default 20000; 0 = headers only), 'request_only'/'response_only' " +
        "to return just one side."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("index") { put("type", "integer"); put("description", "chronological index shown as #index by get_proxy_history") }
            putJsonObject("max_body") { put("type", "integer"); put("description", "max chars per body (default 20000; 0 = headers only)") }
            putJsonObject("request_only") { put("type", "boolean") }
            putJsonObject("response_only") { put("type", "boolean") }
        }
        putJsonArray("required") { add("index") }
    }

    override fun execute(arguments: JsonObject): String {
        val index = arguments["index"]?.jsonPrimitive?.intOrNull ?: error("'index' is required")
        val maxBody = arguments["max_body"]?.jsonPrimitive?.intOrNull ?: 20000
        val requestOnly = arguments["request_only"]?.jsonPrimitive?.booleanOrNull ?: false
        val responseOnly = arguments["response_only"]?.jsonPrimitive?.booleanOrNull ?: false

        val history = api.proxy().history()
        if (index < 0 || index >= history.size) {
            return "No proxy history entry at index $index (history holds ${history.size} entries, valid 0..${history.size - 1})."
        }
        val item = history[index]
        val req = item.finalRequest()
        val resp = item.response()

        val sb = StringBuilder("#$index ${req.method()} ${req.url()}\n")
        if (!responseOnly) {
            sb.append("\n===== REQUEST =====\n")
            sb.append(render(req.headers().joinToString("\n") { "${it.name()}: ${it.value()}" }, req.bodyToString(), maxBody, "${req.method()} ${req.path()}"))
        }
        if (!requestOnly) {
            sb.append("\n===== RESPONSE =====\n")
            if (resp == null) {
                sb.append("(no response captured)\n")
            } else {
                val reason = runCatching { resp.reasonPhrase() }.getOrDefault("")
                sb.append(render(resp.headers().joinToString("\n") { "${it.name()}: ${it.value()}" }, resp.bodyToString(), maxBody, "HTTP ${resp.statusCode()} $reason"))
            }
        }
        return sb.toString()
    }

    private fun render(headers: String, body: String, maxBody: Int, startLine: String): String {
        val sb = StringBuilder("$startLine\n$headers\n")
        if (maxBody > 0 && body.isNotEmpty()) {
            sb.append("\n")
            sb.append(if (body.length > maxBody) body.take(maxBody) + "\n…[truncated, ${body.length} total chars]" else body)
            sb.append("\n")
        }
        return sb.toString()
    }
}
