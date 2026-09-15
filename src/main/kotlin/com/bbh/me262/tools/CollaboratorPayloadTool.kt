package com.bbh.me262.tools

import com.bbh.me262.collab.CollaboratorHolder
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Generate a Burp Collaborator payload (Pro). */
class CollaboratorPayloadTool(private val holder: CollaboratorHolder) : Tool {
    override val name = "generate_collaborator_payload"
    override val description = "Generate a Burp Collaborator payload domain for OOB testing. Poll hits with get_collaborator_interactions. PRO ONLY."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
    }

    override fun execute(arguments: JsonObject): String {
        val client = holder.client ?: error("Collaborator unavailable (Pro only).")
        return "Collaborator payload: ${client.generatePayload()}"
    }
}
