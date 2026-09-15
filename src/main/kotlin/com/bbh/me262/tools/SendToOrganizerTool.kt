package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.Tool
import com.bbh.me262.util.Requests
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Store a request in Burp Organizer for later. */
class SendToOrganizerTool(private val api: MontoyaApi) : Tool {
    override val name = "send_to_organizer"
    override val description = "Store a request in Burp Organizer. Provide 'url' or 'raw'+host/port/tls."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") { put("type", "string") }
            putJsonObject("raw") { put("type", "string") }
            putJsonObject("host") { put("type", "string") }
            putJsonObject("port") { put("type", "integer") }
            putJsonObject("tls") { put("type", "boolean") }
        }
    }

    override fun execute(arguments: JsonObject): String {
        val request = Requests.build(arguments)
        api.organizer().sendToOrganizer(request)
        return "Sent to Organizer: ${request.method()} ${request.url()}"
    }
}
