package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.message.requests.HttpRequest
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Native single-insertion-point (sniper) fuzzer. Substitutes `marker` in a raw
 * request `template` with each payload and sends it through Burp's HTTP stack,
 * concurrently. This is our Intruder replacement: Montoya cannot drive an
 * Intruder attack + collect results, so we do it ourselves and hand the AI the
 * status/length/match table directly.
 *
 * Sends MANY requests — only against authorised targets (obey SCOPE.md).
 */
class FuzzTool(private val api: MontoyaApi) : Tool {
    override val name = "fuzz"
    override val description =
        "Sniper-fuzz one insertion point. Replaces 'marker' (default FUZZ) in a raw 'template' " +
        "request with each payload and sends it through Burp. Payloads from 'payloads' (array) or " +
        "'wordlist' (file path). Filters: 'match' (body substring), 'filter_status' (keep these codes). " +
        "'extract' (regex) pulls a value from each response. Caps: 'concurrency' (default 10, max 30), " +
        "'max' (default 500). Sends many requests — AUTHORISED TARGETS ONLY."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("host") { put("type", "string") }
            putJsonObject("port") { put("type", "integer") }
            putJsonObject("tls") { put("type", "boolean") }
            putJsonObject("template") {
                put("type", "string")
                put("description", "raw HTTP request text containing the marker")
            }
            putJsonObject("marker") { put("type", "string"); put("description", "placeholder, default FUZZ") }
            putJsonObject("payloads") {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
            }
            putJsonObject("wordlist") { put("type", "string"); put("description", "file path, one payload per line") }
            putJsonObject("concurrency") { put("type", "integer") }
            putJsonObject("max") { put("type", "integer") }
            putJsonObject("match") { put("type", "string") }
            putJsonObject("filter_status") {
                put("type", "array")
                putJsonObject("items") { put("type", "integer") }
            }
            putJsonObject("extract") { put("type", "string"); put("description", "regex; first group or whole match") }
        }
        putJsonArray("required") { add("host"); add("port"); add("template") }
    }

    private data class Result(
        val payload: String,
        val status: Int,
        val length: Int,
        val millis: Long,
        val extracted: String?,
        val matched: Boolean,
        val error: String?,
    )

    override fun execute(arguments: JsonObject): String {
        val host = arguments["host"]?.jsonPrimitive?.contentOrNull ?: error("'host' is required")
        val port = arguments["port"]?.jsonPrimitive?.intOrNull ?: error("'port' is required")
        val tls = arguments["tls"]?.jsonPrimitive?.booleanOrNull ?: (port == 443)
        val template = arguments["template"]?.jsonPrimitive?.contentOrNull ?: error("'template' is required")
        val marker = arguments["marker"]?.jsonPrimitive?.contentOrNull ?: "FUZZ"
        require(template.contains(marker)) { "template does not contain marker '$marker'" }

        val concurrency = (arguments["concurrency"]?.jsonPrimitive?.intOrNull ?: 10).coerceIn(1, 30)
        val max = (arguments["max"]?.jsonPrimitive?.intOrNull ?: 500).coerceIn(1, 5000)
        val match = arguments["match"]?.jsonPrimitive?.contentOrNull
        val extractRe = arguments["extract"]?.jsonPrimitive?.contentOrNull?.let { Regex(it) }
        val keepStatuses = (arguments["filter_status"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.intOrNull }?.toSet()

        val payloads = resolvePayloads(arguments).take(max)
        require(payloads.isNotEmpty()) { "no payloads (provide 'payloads' array or 'wordlist' path)" }

        val service = HttpService.httpService(host, port, tls)
        val pool = Executors.newFixedThreadPool(concurrency)
        val results = try {
            payloads.map { payload ->
                pool.submit(Callable { runOne(service, template, marker, payload, match, extractRe) })
            }.map { future ->
                runCatching { future.get(60, TimeUnit.SECONDS) }
                    .getOrElse { Result("?", -1, 0, 0, null, false, it.message ?: "timeout") }
            }
        } finally {
            pool.shutdownNow()
        }

        return format(results, keepStatuses, match != null)
    }

    private fun runOne(
        service: HttpService,
        template: String,
        marker: String,
        payload: String,
        match: String?,
        extractRe: Regex?,
    ): Result {
        val raw = template.replace(marker, payload)
        val request = HttpRequest.httpRequest(service, raw)
        val t0 = System.nanoTime()
        return try {
            val rr = api.http().sendRequest(request)
            val ms = (System.nanoTime() - t0) / 1_000_000
            val resp = rr.response()
                ?: return Result(payload, -1, 0, ms, null, false, "no response")
            val body = resp.bodyToString()
            val extracted = extractRe?.find(body)?.let { it.groupValues.getOrNull(1)?.ifBlank { null } ?: it.value }
            val matched = match != null && body.contains(match)
            Result(payload, resp.statusCode().toInt(), body.length, ms, extracted, matched, null)
        } catch (t: Throwable) {
            Result(payload, -1, 0, (System.nanoTime() - t0) / 1_000_000, null, false, t.message ?: "error")
        }
    }

    private fun resolvePayloads(arguments: JsonObject): List<String> {
        (arguments["payloads"] as? JsonArray)?.let { arr ->
            return arr.mapNotNull { it.jsonPrimitive.contentOrNull }
        }
        arguments["wordlist"]?.jsonPrimitive?.contentOrNull?.let { path ->
            val p = Paths.get(path)
            require(Files.isRegularFile(p)) { "wordlist not found: $path" }
            return Files.readAllLines(p).map { it.trim() }.filter { it.isNotEmpty() }
        }
        return emptyList()
    }

    private fun format(results: List<Result>, keepStatuses: Set<Int>?, hasMatch: Boolean): String {
        var shown = results
        if (keepStatuses != null) shown = shown.filter { it.status in keepStatuses }
        if (hasMatch) shown = shown.filter { it.matched || it.error != null }

        // status distribution over ALL results (pre-filter) to spot anomalies
        val dist = results.groupingBy { it.status }.eachCount().toSortedMap()
        val distStr = dist.entries.joinToString(" ") { (s, c) -> "${if (s < 0) "ERR" else s}:$c" }

        val sb = StringBuilder()
        sb.append("Fuzzed ${results.size} payloads. Status distribution: $distStr\n")
        if (shown.isEmpty()) {
            sb.append("(no rows after filters)\n")
            return sb.toString()
        }
        sb.append("payload | status | len | ms | match | extract\n")
        for (r in shown.sortedWith(compareBy({ it.status }, { it.length })).take(300)) {
            val st = if (r.error != null) "ERR(${r.error.take(40)})" else r.status.toString()
            sb.append("${r.payload.take(60)} | $st | ${r.length} | ${r.millis} | ${if (r.matched) "YES" else "-"} | ${r.extracted ?: "-"}\n")
        }
        if (shown.size > 300) sb.append("... ${shown.size - 300} more rows omitted\n")
        return sb.toString()
    }
}
