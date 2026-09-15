package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Read recent Proxy WebSocket messages. */
class GetWebsocketHistoryTool(private val api: MontoyaApi) : Tool {
    override val name = "get_websocket_history"
    override val description =
        "Return recent Proxy WebSocket messages (direction, ws id, time, payload preview). " +
        "Optional 'contains' payload filter and 'limit' (default 50)."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("contains") { put("type", "string") }
            putJsonObject("limit") { put("type", "integer") }
        }
    }

    override fun execute(arguments: JsonObject): String {
        val contains = arguments["contains"]?.jsonPrimitive?.contentOrNull
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull ?: 50
        val sb = StringBuilder()
        var n = 0
        for (msg in api.proxy().webSocketHistory().asReversed()) {
            val payload = msg.payload().toString()
            if (contains != null && !payload.contains(contains)) continue
            sb.append("[${msg.direction()}] ws#${msg.webSocketId()} @${msg.time()}  ${payload.take(200)}\n")
            if (++n >= limit) break
        }
        return if (n == 0) "No matching WebSocket messages." else "WebSocket history ($n shown):\n$sb"
    }
}
