package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.Tool
import com.bbh.me262.util.Requests
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Send a request to Burp Repeater for manual work. */
class SendToRepeaterTool(private val api: MontoyaApi) : Tool {
    override val name = "send_to_repeater"
    override val description =
        "Send a request to Burp Repeater. Provide 'url' or 'raw'+host/port/tls, optional 'tab' name."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") { put("type", "string") }
            putJsonObject("raw") { put("type", "string") }
            putJsonObject("host") { put("type", "string") }
            putJsonObject("port") { put("type", "integer") }
            putJsonObject("tls") { put("type", "boolean") }
            putJsonObject("tab") { put("type", "string") }
        }
    }

    override fun execute(arguments: JsonObject): String {
        val request = Requests.build(arguments, api)
        val tab = arguments["tab"]?.jsonPrimitive?.contentOrNull
        if (tab != null) api.repeater().sendToRepeater(request, tab) else api.repeater().sendToRepeater(request)
        return "Sent to Repeater${if (tab != null) " (tab: $tab)" else ""}: ${request.method()} ${request.url()}"
    }
}
