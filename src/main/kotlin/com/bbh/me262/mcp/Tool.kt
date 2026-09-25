package com.bbh.me262.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A tool result: human-readable text plus an optional machine-readable mirror. */
data class ToolOutput(val text: String, val structured: JsonElement? = null)

/**
 * One MCP tool. Adding a capability to Me262 = implement this and register it
 * in Me262Extension. `execute` returns the text shown to the AI; throw to signal
 * a tool error (the server wraps it as an MCP isError result).
 *
 * Tools that also want to emit MCP `structuredContent` override [run]; the
 * default wraps [execute] as text-only, so existing tools need no change.
 */
interface Tool {
    val name: String
    val description: String
    /** JSON Schema (object) describing `arguments` for tools/list. */
    val inputSchema: JsonObject
    fun execute(arguments: JsonObject): String

    /** Called by the dispatcher. Default = text-only from [execute]; no double run. */
    fun run(arguments: JsonObject): ToolOutput = ToolOutput(execute(arguments))
}
