package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Generate a random string via Burp utilities. */
class RandomStringTool(private val api: MontoyaApi) : Tool {
    override val name = "random_string"
    override val description = "Generate a random alphanumeric string of 'length' characters."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("length") { put("type", "integer") }
        }
        putJsonArray("required") { add("length") }
    }

    override fun execute(arguments: JsonObject): String {
        val length = arguments["length"]?.jsonPrimitive?.intOrNull ?: error("'length' is required")
        require(length in 1..4096) { "length must be 1..4096" }
        return api.utilities().randomUtils().randomString(length)
    }
}
