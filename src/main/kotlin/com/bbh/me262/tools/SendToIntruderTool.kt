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

/** Send a request to Burp Intruder (for manual configuration; use 'fuzz' to actually attack). */
class SendToIntruderTool(private val api: MontoyaApi) : Tool {
    override val name = "send_to_intruder"
    override val description =
        "Place a request in Burp Intruder. Provide 'url' or 'raw'+host/port/tls, optional 'tab'. " +
        "Note: to run an automated attack with results, use the 'fuzz' tool instead."
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
        val request = Requests.build(arguments)
        val tab = arguments["tab"]?.jsonPrimitive?.contentOrNull
        if (tab != null) api.intruder().sendToIntruder(request, tab) else api.intruder().sendToIntruder(request)
        return "Sent to Intruder${if (tab != null) " (tab: $tab)" else ""}: ${request.method()} ${request.url()}"
    }
}
