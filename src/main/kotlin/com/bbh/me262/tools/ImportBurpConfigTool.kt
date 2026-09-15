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

/**
 * Import Burp project/user options from JSON. Guarded: only runs when Burp was
 * started with -Dme262.allowConfigEdits=true, since it mutates Burp settings.
 */
class ImportBurpConfigTool(private val api: MontoyaApi) : Tool {
    private val allowed = System.getProperty("me262.allowConfigEdits")?.toBoolean() ?: false

    override val name = "import_burp_config"
    override val description =
        "Import Burp options from JSON. 'scope' = project (default) or user; 'json' inline or 'path' to a file. " +
        "Disabled unless Burp was started with -Dme262.allowConfigEdits=true."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("scope") { put("type", "string") }
            putJsonObject("json") { put("type", "string") }
            putJsonObject("path") { put("type", "string") }
        }
    }

    override fun execute(arguments: JsonObject): String {
        if (!allowed) error("config edits disabled. Start Burp with -Dme262.allowConfigEdits=true to enable.")
        val scope = arguments["scope"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "project"
        val inline = arguments["json"]?.jsonPrimitive?.contentOrNull
        val path = arguments["path"]?.jsonPrimitive?.contentOrNull
        val json = when {
            inline != null -> inline
            path != null -> {
                val p = Paths.get(path)
                require(Files.isRegularFile(p)) { "file not found: $path" }
                Files.readString(p)
            }
            else -> error("provide 'json' or 'path'")
        }
        when (scope) {
            "project" -> api.burpSuite().importProjectOptionsFromJson(json)
            "user" -> api.burpSuite().importUserOptionsFromJson(json)
            else -> error("scope must be 'project' or 'user'")
        }
        return "Imported $scope options (${json.length} bytes)."
    }
}
