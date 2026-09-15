package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Add a URL/prefix to Burp's Target scope. */
class ScopeAddTool(private val api: MontoyaApi) : Tool {
    override val name = "scope_add"
    override val description = "Add a URL (prefix) to Burp's Target scope, so ROE-guarded tools may run against it."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") { put("type", "string"); put("description", "e.g. https://example.com/") }
        }
        putJsonArray("required") { add("url") }
    }

    override fun execute(arguments: JsonObject): String {
        val url = arguments["url"]?.jsonPrimitive?.contentOrNull ?: error("'url' is required")
        api.scope().includeInScope(url)
        return "Added to scope: $url"
    }
}
