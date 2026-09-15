package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.message.requests.HttpRequest
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Send an arbitrary HTTP request through Burp's own HTTP stack. */
class SendHttpRequestTool(private val api: MontoyaApi) : Tool {
    override val name = "send_http_request"
    override val description =
        "Send an HTTP request through Burp (its HTTP stack, upstream proxy and session-handling rules apply). " +
        "Provide 'url' for a simple GET, or 'raw' request text plus 'host'/'port'/'tls'."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "Full URL for a simple GET, e.g. https://example.com/path")
            }
            putJsonObject("raw") {
                put("type", "string")
                put("description", "Raw HTTP request text (overrides url). Requires host/port/tls.")
            }
            putJsonObject("host") { put("type", "string") }
            putJsonObject("port") { put("type", "integer") }
            putJsonObject("tls") { put("type", "boolean") }
        }
    }

    override fun execute(arguments: JsonObject): String {
        val url = arguments["url"]?.jsonPrimitive?.contentOrNull
        val raw = arguments["raw"]?.jsonPrimitive?.contentOrNull

        val request: HttpRequest = when {
            raw != null -> {
                val host = arguments["host"]?.jsonPrimitive?.contentOrNull
                    ?: error("'raw' requires 'host'")
                val port = arguments["port"]?.jsonPrimitive?.intOrNull
                    ?: error("'raw' requires 'port'")
                val tls = arguments["tls"]?.jsonPrimitive?.booleanOrNull ?: (port == 443)
                HttpRequest.httpRequest(HttpService.httpService(host, port, tls), raw)
            }
            url != null -> HttpRequest.httpRequestFromUrl(url)
            else -> error("provide 'url' or 'raw'")
        }

        val rr = api.http().sendRequest(request)
        val resp = rr.response() ?: return "No response (request failed or timed out)."
        val reason = runCatching { resp.reasonPhrase() }.getOrDefault("")
        val headers = resp.headers().joinToString("\n") { "${it.name()}: ${it.value()}" }
        val bodyPreview = resp.bodyToString().take(8000)
        return "HTTP ${resp.statusCode()} $reason\n$headers\n\n$bodyPreview"
    }
}
