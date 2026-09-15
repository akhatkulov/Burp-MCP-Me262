package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.burpsuite.TaskExecutionEngine.TaskExecutionEngineState
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Pause or resume Burp's task execution engine (scans, etc.). */
class SetTaskEngineTool(private val api: MontoyaApi) : Tool {
    override val name = "set_task_engine"
    override val description = "Pause or resume Burp's task execution engine. 'state' is 'running' or 'paused'."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("state") { put("type", "string") }
        }
        putJsonArray("required") { add("state") }
    }

    override fun execute(arguments: JsonObject): String {
        val state = arguments["state"]?.jsonPrimitive?.contentOrNull ?: error("'state' is required")
        val target = when (state.lowercase()) {
            "running", "run", "resume" -> TaskExecutionEngineState.RUNNING
            "paused", "pause" -> TaskExecutionEngineState.PAUSED
            else -> error("state must be 'running' or 'paused'")
        }
        val engine = api.burpSuite().taskExecutionEngine()
        engine.state = target
        return "Task engine is now ${engine.state}."
    }
}
