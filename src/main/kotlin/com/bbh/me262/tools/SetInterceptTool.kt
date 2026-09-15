package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Turn Burp Proxy intercept on or off. */
class SetInterceptTool(private val api: MontoyaApi) : Tool {
    override val name = "set_intercept"
    override val description = "Enable or disable Burp Proxy intercept. Arg 'enabled' (boolean)."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("enabled") { put("type", "boolean") }
        }
        putJsonArray("required") { add("enabled") }
    }

    override fun execute(arguments: JsonObject): String {
        val enabled = arguments["enabled"]?.jsonPrimitive?.booleanOrNull ?: error("'enabled' is required")
        if (enabled) api.proxy().enableIntercept() else api.proxy().disableIntercept()
        return "Proxy intercept is now ${if (api.proxy().isInterceptEnabled) "ON" else "OFF"}."
    }
}
