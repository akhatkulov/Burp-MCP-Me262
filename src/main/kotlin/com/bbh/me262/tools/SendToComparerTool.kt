package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.ByteArray
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Send two items to Burp Comparer for a visual diff. */
class SendToComparerTool(private val api: MontoyaApi) : Tool {
    override val name = "send_to_comparer"
    override val description = "Send two strings ('a' and 'b') to Burp Comparer for a visual diff."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("a") { put("type", "string") }
            putJsonObject("b") { put("type", "string") }
        }
        putJsonArray("required") { add("a"); add("b") }
    }

    override fun execute(arguments: JsonObject): String {
        val a = arguments["a"]?.jsonPrimitive?.contentOrNull ?: error("'a' is required")
        val b = arguments["b"]?.jsonPrimitive?.contentOrNull ?: error("'b' is required")
        api.comparer().sendToComparer(ByteArray.byteArray(a), ByteArray.byteArray(b))
        return "Sent 2 items to Comparer (${a.length} and ${b.length} chars)."
    }
}
