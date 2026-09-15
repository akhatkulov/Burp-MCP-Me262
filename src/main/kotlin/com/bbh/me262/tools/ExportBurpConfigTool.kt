package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Files
import java.nio.file.Paths

/** Export Burp project or user options as JSON. */
class ExportBurpConfigTool(private val api: MontoyaApi) : Tool {
    override val name = "export_burp_config"
    override val description =
        "Export Burp options as JSON. 'scope' = project (default) or user. Optional 'path' to write " +
        "to a file (else JSON is returned, truncated). Optional 'keys' dotted path to export a subtree."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("scope") { put("type", "string") }
            putJsonObject("path") { put("type", "string") }
            putJsonObject("keys") { put("type", "string") }
        }
    }

    override fun execute(arguments: JsonObject): String {
        val scope = arguments["scope"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "project"
        val keys = arguments["keys"]?.jsonPrimitive?.contentOrNull
        val json = when (scope) {
            "project" -> if (keys != null) api.burpSuite().exportProjectOptionsAsJson(keys) else api.burpSuite().exportProjectOptionsAsJson()
            "user" -> if (keys != null) api.burpSuite().exportUserOptionsAsJson(keys) else api.burpSuite().exportUserOptionsAsJson()
            else -> error("scope must be 'project' or 'user'")
        }
        val path = arguments["path"]?.jsonPrimitive?.contentOrNull
        return if (path != null) {
            Files.writeString(Paths.get(path), json)
            "Wrote $scope options (${json.length} bytes) to $path"
        } else {
            "$scope options (${json.length} bytes):\n${json.take(8000)}"
        }
    }
}
