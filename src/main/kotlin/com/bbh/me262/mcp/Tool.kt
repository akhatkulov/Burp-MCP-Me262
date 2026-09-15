package com.bbh.me262.mcp

import kotlinx.serialization.json.JsonObject

/**
 * One MCP tool. Adding a capability to Me262 = implement this and register it
 * in Me262Extension. `execute` returns the text shown to the AI; throw to signal
 * a tool error (the server wraps it as an MCP isError result).
 */
interface Tool {
    val name: String
    val description: String
    /** JSON Schema (object) describing `arguments` for tools/list. */
    val inputSchema: JsonObject
    fun execute(arguments: JsonObject): String
}
