package com.bbh.me262.tools

import com.bbh.me262.collab.CollaboratorHolder
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Poll Collaborator interactions received so far (Pro). */
class CollaboratorInteractionsTool(private val holder: CollaboratorHolder) : Tool {
    override val name = "get_collaborator_interactions"
    override val description = "Return Burp Collaborator interactions (DNS/HTTP/SMTP hits) received so far. PRO ONLY."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
    }

    override fun execute(arguments: JsonObject): String {
        val client = holder.client ?: error("Collaborator unavailable (Pro only).")
        val interactions = client.allInteractions
        if (interactions.isEmpty()) return "No Collaborator interactions yet."
        val sb = StringBuilder("Interactions (${interactions.size}):\n")
        for (i in interactions) {
            sb.append("- ${i.type()} from ${i.clientIp().hostAddress} at ${i.timeStamp()}  id=${i.id()}\n")
        }
        return sb.toString()
    }
}
