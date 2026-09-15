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

/** Encode/decode helpers using Burp's own utilities. */
class TransformTool(private val api: MontoyaApi) : Tool {
    override val name = "transform"
    override val description =
        "Encode/decode a string with Burp's utilities. 'op' is one of: url_encode, url_decode, " +
        "base64_encode, base64_decode, html_encode, html_decode. 'input' is the text."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("op") { put("type", "string") }
            putJsonObject("input") { put("type", "string") }
        }
        putJsonArray("required") { add("op"); add("input") }
    }

    override fun execute(arguments: JsonObject): String {
        val op = arguments["op"]?.jsonPrimitive?.contentOrNull ?: error("'op' is required")
        val input = arguments["input"]?.jsonPrimitive?.contentOrNull ?: error("'input' is required")
        val u = api.utilities()
        return when (op.lowercase()) {
            "url_encode" -> u.urlUtils().encode(input)
            "url_decode" -> u.urlUtils().decode(input)
            "base64_encode" -> u.base64Utils().encodeToString(input)
            "base64_decode" -> u.base64Utils().decode(input).toString()
            "html_encode" -> u.htmlUtils().encode(input)
            "html_decode" -> u.htmlUtils().decode(input)
            else -> error("unknown op '$op'")
        }
    }
}
