package com.bbh.me262.util

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.message.requests.HttpRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Build an [HttpRequest] from tool arguments.
 *
 * Base request: either `url`, or `raw` + host/port/tls. On top of that base the
 * caller may layer structured fields that are far more robust than hand-crafting
 * a raw request — this is what lets long session cookies and mismatched bodies go
 * through Burp cleanly instead of coming back as an "HTTP 0 / no response":
 *
 *   method       override the request method
 *   path         override the request path
 *   headers      array of "Name: Value" strings, or an object {Name: value}
 *   cookies      "a=b; c=d" string, or an object {a: b, c: d} -> set as one Cookie header
 *   cookie_file  path to a file holding a Cookie header value (or name=value lines)
 *   body         request body (Content-Length is recomputed automatically)
 *   fix_content_length  recompute Content-Length to match the body (default true)
 *   use_cookie_jar       merge Burp's cookie jar for the request host (needs api)
 *
 * The two long-standing footguns this fixes:
 *  - raw requests pasted with lone LF line endings (Burp expects CRLF framing);
 *    [normalizeRaw] rewrites them so a long Cookie header is never mis-parsed.
 *  - a stale Content-Length left behind after editing the body; [build] re-sets
 *    the body so Montoya recomputes it (opt out with fix_content_length=false for
 *    request-smuggling / desync tests).
 */
object Requests {

    /** Build the request without touching Burp's cookie jar (no api needed). */
    fun build(arguments: JsonObject): HttpRequest = assemble(arguments, api = null)

    /** Build the request, optionally merging the Burp cookie jar when use_cookie_jar=true. */
    fun build(arguments: JsonObject, api: MontoyaApi): HttpRequest = assemble(arguments, api)

    private fun assemble(arguments: JsonObject, api: MontoyaApi?): HttpRequest {
        val url = arguments["url"]?.jsonPrimitive?.contentOrNull
        val raw = arguments["raw"]?.jsonPrimitive?.contentOrNull

        var request: HttpRequest = when {
            raw != null -> {
                val host = arguments["host"]?.jsonPrimitive?.contentOrNull ?: error("'raw' requires 'host'")
                val port = arguments["port"]?.jsonPrimitive?.intOrNull ?: error("'raw' requires 'port'")
                val tls = arguments["tls"]?.jsonPrimitive?.booleanOrNull ?: (port == 443)
                HttpRequest.httpRequest(HttpService.httpService(host, port, tls), normalizeRaw(raw))
            }
            url != null -> HttpRequest.httpRequestFromUrl(url)
            else -> error("provide 'url' or 'raw' (+host/port/tls)")
        }

        arguments["method"]?.jsonPrimitive?.contentOrNull?.let { request = request.withMethod(it) }
        arguments["path"]?.jsonPrimitive?.contentOrNull?.let { request = request.withPath(it) }

        for ((name, value) in parseHeaders(arguments["headers"])) {
            request = request.withUpdatedHeader(name, value)
        }

        val cookiePairs = LinkedHashMap<String, String>()
        parseCookies(arguments["cookies"]).forEach { (k, v) -> cookiePairs[k] = v }
        arguments["cookie_file"]?.jsonPrimitive?.contentOrNull?.let { path ->
            readCookieFile(path).forEach { (k, v) -> cookiePairs[k] = v }
        }
        if (api != null && (arguments["use_cookie_jar"]?.jsonPrimitive?.booleanOrNull == true)) {
            cookieJarPairs(api, request).forEach { (k, v) -> cookiePairs.putIfAbsent(k, v) }
        }
        if (cookiePairs.isNotEmpty()) {
            request = request.withUpdatedHeader("Cookie", cookieHeaderValue(cookiePairs))
        }

        val explicitBody = arguments["body"]?.jsonPrimitive?.contentOrNull
        val fixLen = arguments["fix_content_length"]?.jsonPrimitive?.booleanOrNull ?: true
        request = when {
            explicitBody != null -> request.withBody(explicitBody)
            fixLen && request.bodyToString().isNotEmpty() -> request.withBody(request.bodyToString())
            else -> request
        }
        return request
    }

    /** Rewrite any lone CR or LF to canonical CRLF so Burp frames headers correctly. */
    fun normalizeRaw(raw: String): String =
        raw.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\r\n")

    /** Parse `headers` as an array of "Name: Value" strings or an object {Name: value}. */
    fun parseHeaders(element: Any?): List<Pair<String, String>> = when (element) {
        is JsonArray -> element.mapNotNull { it.jsonPrimitive.contentOrNull }.mapNotNull { splitHeader(it) }
        is JsonObject -> element.entries.map { (k, v) -> k to (v.jsonPrimitive.contentOrNull ?: "") }
        else -> emptyList()
    }

    private fun splitHeader(line: String): Pair<String, String>? {
        val i = line.indexOf(':')
        if (i <= 0) return null
        return line.substring(0, i).trim() to line.substring(i + 1).trim()
    }

    /** Parse `cookies` as "a=b; c=d" or an object {a: b}. Preserves order, last wins. */
    fun parseCookies(element: Any?): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        when (element) {
            is JsonPrimitive -> parseCookieString(element.contentOrNull ?: "", out)
            is JsonObject -> element.forEach { (k, v) -> out[k] = v.jsonPrimitive.contentOrNull ?: "" }
            else -> {}
        }
        return out
    }

    private fun parseCookieString(s: String, out: LinkedHashMap<String, String>) {
        for (part in s.split(';')) {
            val t = part.trim()
            if (t.isEmpty()) continue
            val eq = t.indexOf('=')
            if (eq < 0) out[t] = "" else out[t.substring(0, eq).trim()] = t.substring(eq + 1).trim()
        }
    }

    private fun readCookieFile(path: String): LinkedHashMap<String, String> {
        val p = Paths.get(path)
        require(Files.isRegularFile(p)) { "cookie_file not found: $path" }
        val out = LinkedHashMap<String, String>()
        for (line in Files.readAllLines(p)) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            // Accept either a full "Cookie:" header line, a "k=v; k2=v2" blob, or one k=v per line.
            val v = if (t.startsWith("Cookie:", ignoreCase = true)) t.substringAfter(':').trim() else t
            parseCookieString(v, out)
        }
        return out
    }

    private fun cookieJarPairs(api: MontoyaApi, request: HttpRequest): List<Pair<String, String>> {
        val host = runCatching { request.httpService().host() }.getOrNull()?.lowercase() ?: return emptyList()
        return runCatching {
            api.http().cookieJar().cookies()
                .filter { c ->
                    // Domain match is case-insensitive; a leading-dot domain (".example.com") matches too.
                    val d = c.domain().removePrefix(".").lowercase()
                    host == d || host.endsWith(".$d")
                }
                .map { it.name() to it.value() }
        }.getOrDefault(emptyList())
    }

    fun cookieHeaderValue(pairs: Map<String, String>): String =
        pairs.entries.joinToString("; ") { (k, v) -> "$k=$v" }
}
