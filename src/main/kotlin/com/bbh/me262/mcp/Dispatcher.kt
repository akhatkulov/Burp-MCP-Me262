package com.bbh.me262.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Pure MCP JSON-RPC handling — no HTTP, no threads, no Burp deps, so it is
 * fully unit-testable. McpServer wraps it with the SSE transport.
 */
class Dispatcher(
    private val registry: ToolRegistry,
    private val serverName: String = "burp-mcp-me262",
    private val serverVersion: String = "0.1.0",
    private val protocolVersion: String = "2024-11-05",
    private val logError: (String) -> Unit = {},
) {
    /** Returns the response, or null for a notification (no reply expected). */
    fun dispatch(req: JsonRpcRequest): JsonRpcResponse? = when (req.method) {
        "initialize" -> ok(req.id, buildJsonObject {
            put("protocolVersion", protocolVersion)
            putJsonObject("capabilities") { putJsonObject("tools") {} }
            putJsonObject("serverInfo") {
                put("name", serverName)
                put("version", serverVersion)
            }
        })
        "notifications/initialized" -> null
        "ping" -> ok(req.id, buildJsonObject {})
        "tools/list" -> ok(req.id, buildJsonObject {
            putJsonArray("tools") {
                for (t in registry.list()) add(buildJsonObject {
                    put("name", t.name)
                    put("description", t.description)
                    put("inputSchema", t.inputSchema)
                })
            }
        })
        "tools/call" -> handleToolCall(req)
        else -> err(req.id, -32601, "Method not found: ${req.method}")
    }

    private fun handleToolCall(req: JsonRpcRequest): JsonRpcResponse {
        val params = req.params as? JsonObject ?: return err(req.id, -32602, "invalid params")
        val name = params["name"]?.jsonPrimitive?.contentOrNull
            ?: return err(req.id, -32602, "missing tool name")
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val tool = registry.get(name) ?: return err(req.id, -32602, "unknown tool: $name")
        return runCatching { tool.execute(args) }.fold(
            onSuccess = { text -> toolResult(req.id, text, isError = false) },
            onFailure = { t ->
                logError("tool '$name' failed: ${t.message}")
                toolResult(req.id, "ERROR: ${t.message}", isError = true)
            },
        )
    }

    private fun toolResult(id: JsonElement?, text: String, isError: Boolean) = ok(id, buildJsonObject {
        putJsonArray("content") {
            add(buildJsonObject {
                put("type", "text")
                put("text", text)
            })
        }
        put("isError", isError)
    })

    private fun ok(id: JsonElement?, result: JsonElement) = JsonRpcResponse(id = id, result = result)
    private fun err(id: JsonElement?, code: Int, message: String) =
        JsonRpcResponse(id = id, error = JsonRpcError(code, message))
}
