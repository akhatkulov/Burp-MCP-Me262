package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.scanner.bchecks.BCheckImportResult
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
 * Import a BCheck definition. Once imported it becomes part of Burp's scan
 * checks and runs during audits started via start_active_scan. PRO ONLY.
 */
class ImportBCheckTool(private val api: MontoyaApi) : Tool {
    override val name = "import_bcheck"
    override val description =
        "Import a BCheck script so it participates in active scans. Provide 'script' (inline text) " +
        "or 'path' (file). PRO ONLY."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("script") { put("type", "string"); put("description", "BCheck source text") }
            putJsonObject("path") { put("type", "string"); put("description", "path to a .bcheck file") }
        }
    }

    override fun execute(arguments: JsonObject): String {
        val inline = arguments["script"]?.jsonPrimitive?.contentOrNull
        val path = arguments["path"]?.jsonPrimitive?.contentOrNull
        val script = when {
            inline != null -> inline
            path != null -> {
                val p = Paths.get(path)
                require(Files.isRegularFile(p)) { "bcheck file not found: $path" }
                Files.readString(p)
            }
            else -> error("provide 'script' or 'path'")
        }
        val result = api.scanner().bChecks().importBCheck(script)
        val errors = result.importErrors()
        val head = if (result.status() == BCheckImportResult.Status.LOADED_WITHOUT_ERRORS) {
            "BCheck imported (LOADED_WITHOUT_ERRORS)."
        } else {
            "BCheck imported with errors (LOADED_WITH_ERRORS)."
        }
        return if (errors.isEmpty()) head else "$head\n" + errors.joinToString("\n") { "  - $it" }
    }
}
