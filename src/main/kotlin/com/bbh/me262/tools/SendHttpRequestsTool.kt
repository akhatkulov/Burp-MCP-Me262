package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.requests.HttpRequest
import com.bbh.me262.mcp.Tool
import com.bbh.me262.safety.RoeGuard
import com.bbh.me262.util.Requests
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Send several HTTP requests through Burp in one call. Burp pipelines them, so
 * this is the quick way to compare responses side by side — e.g. replay the same
 * request as two different users for IDOR/access-control testing, where the tell
 * is "same status, same length" across identities that should differ.
 * Rows whose target is outside Burp's Target scope are refused and never sent.
 */
class SendHttpRequestsTool(private val api: MontoyaApi, private val roe: RoeGuard) : Tool {
    override val name = "send_http_requests"
    override val description =
        "Send a batch of HTTP requests through Burp and return a compact status/length table. " +
        "'requests' is an array; each item takes the same fields as send_http_request " +
        "(url OR raw+host/port/tls, plus method/path/headers/cookies/cookie_file/use_cookie_jar/body). " +
        "Optional 'match' substring is flagged per response, 'include_body' returns a short body preview. " +
        "Ideal for IDOR/access-control: same request, different session cookies. " +
        "Rows targeting anything outside Burp's Target scope are refused (not sent); redirects are not followed."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("requests") {
                put("type", "array")
                put("description", "array of request specs (same shape as send_http_request arguments)")
                putJsonObject("items") { put("type", "object") }
            }
            putJsonObject("match") { put("type", "string"); put("description", "substring flagged per response body") }
            putJsonObject("include_body") { put("type", "boolean"); put("description", "include a short body preview per row") }
        }
        putJsonArray("required") { add("requests") }
    }

    override fun execute(arguments: JsonObject): String {
        val specs = (arguments["requests"] as? JsonArray)?.filterIsInstance<JsonObject>()
            ?: error("'requests' must be an array of request objects")
        require(specs.isNotEmpty()) { "'requests' is empty" }
        require(specs.size <= 200) { "too many requests (${specs.size}); cap is 200 per call" }
        val match = arguments["match"]?.jsonPrimitive?.contentOrNull
        val includeBody = arguments["include_body"]?.jsonPrimitive?.booleanOrNull ?: false

        val built = specs.map { spec ->
            runCatching {
                Requests.build(spec, api).also { req ->
                    require(roe.isAllowed(req.url())) { "REFUSED by ROE guard: ${req.url()} is not in Burp's Target scope" }
                }
            }
        }
        val requests: List<HttpRequest> = built.mapNotNull { it.getOrNull() }
        val responses = if (requests.isNotEmpty()) api.http().sendRequests(requests) else emptyList()

        val sb = StringBuilder("Sent ${requests.size}/${specs.size} requests.\n")
        sb.append("# | method | url | status | len | match\n")
        var respIdx = 0
        for ((i, b) in built.withIndex()) {
            if (b.isFailure) {
                val msg = b.exceptionOrNull()?.message.orEmpty()
                val kind = if (msg.startsWith("REFUSED")) "(refused)" else "(build failed)"
                sb.append("$i | $kind | ${msg.take(120)}\n")
                continue
            }
            val rr = responses.getOrNull(respIdx++)
            val req = rr?.request() ?: requests[respIdx - 1]
            val resp = rr?.response()
            val status = resp?.statusCode()?.toString() ?: "ERR"
            val len = resp?.body()?.length() ?: 0
            val matched = if (match != null && resp != null) resp.bodyToString().contains(match) else false
            sb.append("$i | ${req.method()} | ${req.url()} | $status | $len | ${if (matched) "YES" else "-"}\n")
            if (includeBody && resp != null) {
                sb.append("   body: ${resp.bodyToString().take(300).replace("\n", " ")}\n")
            }
        }
        return sb.toString()
    }
}
