package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.RequestOptions
import com.bbh.me262.mcp.Tool
import com.bbh.me262.safety.RoeGuard
import com.bbh.me262.util.Requests
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Send an arbitrary HTTP request through Burp's own HTTP stack (ROE-guarded by scope). */
class SendHttpRequestTool(private val api: MontoyaApi, private val roe: RoeGuard) : Tool {
    override val name = "send_http_request"
    override val description =
        "Send an HTTP request through Burp (its HTTP stack, upstream proxy and session rules apply). " +
        "Base: 'url' for a simple GET, or 'raw' request text + 'host'/'port'/'tls'. " +
        "Layer on top: 'method', 'path', 'headers' (array of \"Name: Value\" or object), " +
        "'cookies' (\"a=b; c=d\" or object), 'cookie_file' (path), 'use_cookie_jar' (bool, pull Burp's " +
        "cookie jar for the host), 'body'. Prefer 'cookies'/'headers' over pasting a long cookie into 'raw' " +
        "— they set the header cleanly and Content-Length is recomputed, which avoids the empty 'HTTP 0' response. " +
        "Redirects are NOT followed by default (the 3xx comes back as-is); 'follow_redirects' follows only " +
        "hops that stay inside Burp's Target scope. Out-of-scope targets are refused."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "Full URL for a simple request, e.g. https://example.com/path")
            }
            putJsonObject("raw") {
                put("type", "string")
                put("description", "Raw HTTP request text (overrides url). Requires host/port/tls.")
            }
            putJsonObject("host") { put("type", "string") }
            putJsonObject("port") { put("type", "integer") }
            putJsonObject("tls") { put("type", "boolean") }
            putJsonObject("method") { put("type", "string"); put("description", "override method, e.g. POST") }
            putJsonObject("path") { put("type", "string"); put("description", "override request path") }
            putJsonObject("headers") {
                put("type", "array")
                put("description", "headers as \"Name: Value\" strings (an object {Name: value} also works)")
                putJsonObject("items") { put("type", "string") }
            }
            putJsonObject("cookies") {
                put("type", "string")
                put("description", "\"a=b; c=d\" cookie string (an object {a: b} also works); set as one Cookie header")
            }
            putJsonObject("cookie_file") {
                put("type", "string")
                put("description", "path to a file with a Cookie header value, or name=value lines")
            }
            putJsonObject("use_cookie_jar") {
                put("type", "boolean")
                put("description", "merge cookies from Burp's cookie jar for the request host")
            }
            putJsonObject("body") { put("type", "string"); put("description", "request body; Content-Length is recomputed") }
            putJsonObject("fix_content_length") {
                put("type", "boolean")
                put("description", "recompute Content-Length to match the body (default true)")
            }
            putJsonObject("follow_redirects") {
                put("type", "boolean")
                put("description", "follow redirects, but only while they stay in Burp's Target scope (default false)")
            }
            putJsonObject("max_body") {
                put("type", "integer")
                put("description", "max response body chars to return (default 8000; 0 = headers only)")
            }
        }
    }

    override fun execute(arguments: JsonObject): String {
        val request = Requests.build(arguments, api)
        roe.requireInScope(request.url())
        val maxBody = arguments["max_body"]?.jsonPrimitive?.intOrNull ?: 8000
        val followRedirects = arguments["follow_redirects"]?.jsonPrimitive?.booleanOrNull ?: false

        val rr = if (followRedirects) {
            api.http().sendRequest(request, RequestOptions.requestOptions().withRedirectionMode(roe.redirectionMode()))
        } else {
            api.http().sendRequest(request)
        }
        val resp = rr.response()
            ?: return "No response from ${request.method()} ${request.url()} " +
                "(connection failed, reset, or timed out — Burp reports status 0). " +
                "The request as sent carried ${request.toByteArray().length()} bytes; " +
                "this is a network/target issue, not a cookie-size limit."

        val reason = runCatching { resp.reasonPhrase() }.getOrDefault("")
        val headers = resp.headers().joinToString("\n") { "${it.name()}: ${it.value()}" }
        val ms = rr.timingData().map { it.timeBetweenRequestSentAndStartOfResponse().toMillis() }.orElse(-1)
        val bodyLen = resp.body().length()
        val head = "HTTP ${resp.statusCode()} $reason  (${bodyLen} body bytes${if (ms >= 0) ", ${ms}ms" else ""})"
        if (maxBody <= 0) return "$head\n$headers"
        val bodyPreview = resp.bodyToString().let { if (it.length > maxBody) it.take(maxBody) + "\n…[truncated]" else it }
        return "$head\n$headers\n\n$bodyPreview"
    }
}
