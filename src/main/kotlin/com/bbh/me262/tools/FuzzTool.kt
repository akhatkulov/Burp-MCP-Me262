package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.message.requests.HttpRequest
import com.bbh.me262.mcp.Tool
import com.bbh.me262.safety.RoeGuard
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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
 * Native fuzzer — our Intruder replacement, since Montoya cannot run an Intruder
 * attack and return results. Sends requests through Burp's HTTP stack and hands
 * the AI the status/length/match table directly.
 *
 * Modes:
 *   sniper       one marker (default FUZZ), one payload list.
 *   clusterbomb  markers FUZZ1..FUZZn, one payload set each, cartesian product.
 *   pitchfork    markers FUZZ1..FUZZn, payload sets iterated in parallel (zip).
 *
 * Sends MANY requests — only against authorised targets (ROE-guarded by scope).
 */
class FuzzTool(private val api: MontoyaApi, private val roe: RoeGuard) : Tool {
    override val name = "fuzz"
    override val description =
        "Fuzz a request through Burp. mode=sniper (default) uses 'marker' (default FUZZ) + 'payloads' " +
        "(array) or 'wordlist' (file). mode=clusterbomb|pitchfork use 'markers' (default FUZZ1..n) + " +
        "'payload_sets' (array of arrays). Filters: 'match' (body substring), 'filter_status' (keep codes). " +
        "'extract' (regex). Caps: 'concurrency' (<=30), 'max' requests (<=5000). AUTHORISED TARGETS ONLY."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("host") { put("type", "string") }
            putJsonObject("port") { put("type", "integer") }
            putJsonObject("tls") { put("type", "boolean") }
            putJsonObject("template") { put("type", "string"); put("description", "raw request with marker(s)") }
            putJsonObject("mode") { put("type", "string"); put("description", "sniper|clusterbomb|pitchfork") }
            putJsonObject("marker") { put("type", "string"); put("description", "sniper marker, default FUZZ") }
            putJsonObject("payloads") { put("type", "array"); putJsonObject("items") { put("type", "string") } }
            putJsonObject("wordlist") { put("type", "string") }
            putJsonObject("markers") { put("type", "array"); putJsonObject("items") { put("type", "string") } }
            putJsonObject("payload_sets") {
                put("type", "array")
                putJsonObject("items") { put("type", "array"); putJsonObject("items") { put("type", "string") } }
            }
            putJsonObject("concurrency") { put("type", "integer") }
            putJsonObject("max") { put("type", "integer") }
            putJsonObject("match") { put("type", "string") }
            putJsonObject("filter_status") { put("type", "array"); putJsonObject("items") { put("type", "integer") } }
            putJsonObject("extract") { put("type", "string") }
        }
        putJsonArray("required") { add("host"); add("port"); add("template") }
    }

    private data class Result(
        val label: String,
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
        roe.requireInScope("${if (tls) "https" else "http"}://$host/")
        val template = arguments["template"]?.jsonPrimitive?.contentOrNull ?: error("'template' is required")
        val mode = arguments["mode"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "sniper"

        val concurrency = (arguments["concurrency"]?.jsonPrimitive?.intOrNull ?: 10).coerceIn(1, 30)
        val max = (arguments["max"]?.jsonPrimitive?.intOrNull ?: 500).coerceIn(1, 5000)
        val match = arguments["match"]?.jsonPrimitive?.contentOrNull
        val extractRe = arguments["extract"]?.jsonPrimitive?.contentOrNull?.let { Regex(it) }
        val keepStatuses = (arguments["filter_status"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.intOrNull }?.toSet()

        // Resolve markers + combinations per mode.
        val (markers, combos) = buildCombos(arguments, template, mode, max)
        require(combos.isNotEmpty()) { "no payloads produced" }

        val service = HttpService.httpService(host, port, tls)
        val pool = Executors.newFixedThreadPool(concurrency)
        val results = try {
            combos.map { combo ->
                pool.submit(Callable { runOne(service, template, markers, combo, match, extractRe) })
            }.map { f ->
                runCatching { f.get(60, TimeUnit.SECONDS) }
                    .getOrElse { Result("?", -1, 0, 0, null, false, it.message ?: "timeout") }
            }
        } finally {
            pool.shutdownNow()
        }
        return format(results, keepStatuses, match != null)
    }

    /** Returns (markers, list of payload combinations). Each combo aligns with markers by index. */
    private fun buildCombos(
        arguments: JsonObject,
        template: String,
        mode: String,
        max: Int,
    ): Pair<List<String>, List<List<String>>> = when (mode) {
        "sniper" -> {
            val marker = arguments["marker"]?.jsonPrimitive?.contentOrNull ?: "FUZZ"
            require(template.contains(marker)) { "template does not contain marker '$marker'" }
            val payloads = resolveSniperPayloads(arguments).take(max)
            listOf(marker) to payloads.map { listOf(it) }
        }
        "clusterbomb", "pitchfork" -> {
            val sets = resolvePayloadSets(arguments)
            require(sets.isNotEmpty()) { "'payload_sets' is required for $mode" }
            val markers = resolveMarkers(arguments, sets.size)
            markers.forEach { require(template.contains(it)) { "template does not contain marker '$it'" } }
            val combos = if (mode == "pitchfork") {
                val n = sets.minOf { it.size }
                (0 until n).map { i -> sets.map { it[i] } }
            } else {
                cartesian(sets).take(max).toList()
            }
            markers to combos.take(max)
        }
        else -> error("unknown mode '$mode' (sniper|clusterbomb|pitchfork)")
    }

    private fun cartesian(sets: List<List<String>>): Sequence<List<String>> {
        var acc: Sequence<List<String>> = sequenceOf(emptyList())
        for (set in sets) acc = acc.flatMap { prefix -> set.asSequence().map { prefix + it } }
        return acc
    }

    private fun resolveMarkers(arguments: JsonObject, count: Int): List<String> {
        (arguments["markers"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }?.let {
            require(it.size == count) { "markers count (${it.size}) must match payload_sets count ($count)" }
            return it
        }
        return (1..count).map { "FUZZ$it" }
    }

    private fun resolveSniperPayloads(arguments: JsonObject): List<String> {
        (arguments["payloads"] as? JsonArray)?.let { return it.mapNotNull { e -> e.jsonPrimitive.contentOrNull } }
        arguments["wordlist"]?.jsonPrimitive?.contentOrNull?.let { path ->
            val p = Paths.get(path)
            require(Files.isRegularFile(p)) { "wordlist not found: $path" }
            return Files.readAllLines(p).map { it.trim() }.filter { it.isNotEmpty() }
        }
        error("provide 'payloads' array or 'wordlist' path")
    }

    private fun resolvePayloadSets(arguments: JsonObject): List<List<String>> {
        val outer = arguments["payload_sets"] as? JsonArray ?: return emptyList()
        return outer.map { inner ->
            (inner as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: error("each payload_sets entry must be an array of strings")
        }
    }

    private fun runOne(
        service: HttpService,
        template: String,
        markers: List<String>,
        combo: List<String>,
        match: String?,
        extractRe: Regex?,
    ): Result {
        var raw = template
        for ((i, marker) in markers.withIndex()) raw = raw.replace(marker, combo[i])
        val label = combo.joinToString(" | ")
        val request = HttpRequest.httpRequest(service, raw)
        val t0 = System.nanoTime()
        return try {
            val rr = api.http().sendRequest(request)
            val ms = (System.nanoTime() - t0) / 1_000_000
            val resp = rr.response() ?: return Result(label, -1, 0, ms, null, false, "no response")
            val body = resp.bodyToString()
            val extracted = extractRe?.find(body)?.let { it.groupValues.getOrNull(1)?.ifBlank { null } ?: it.value }
            val matched = match != null && body.contains(match)
            Result(label, resp.statusCode().toInt(), body.length, ms, extracted, matched, null)
        } catch (t: Throwable) {
            Result(label, -1, 0, (System.nanoTime() - t0) / 1_000_000, null, false, t.message ?: "error")
        }
    }

    private fun format(results: List<Result>, keepStatuses: Set<Int>?, hasMatch: Boolean): String {
        var shown = results
        if (keepStatuses != null) shown = shown.filter { it.status in keepStatuses }
        if (hasMatch) shown = shown.filter { it.matched || it.error != null }

        val dist = results.groupingBy { it.status }.eachCount().toSortedMap()
        val distStr = dist.entries.joinToString(" ") { (s, c) -> "${if (s < 0) "ERR" else s}:$c" }

        val sb = StringBuilder("Fuzzed ${results.size} requests. Status distribution: $distStr\n")
        if (shown.isEmpty()) return sb.append("(no rows after filters)\n").toString()
        sb.append("payload | status | len | ms | match | extract\n")
        for (r in shown.sortedWith(compareBy({ it.status }, { it.length })).take(300)) {
            val st = if (r.error != null) "ERR(${r.error.take(40)})" else r.status.toString()
            sb.append("${r.label.take(80)} | $st | ${r.length} | ${r.millis} | ${if (r.matched) "YES" else "-"} | ${r.extracted ?: "-"}\n")
        }
        if (shown.size > 300) sb.append("... ${shown.size - 300} more rows omitted\n")
        return sb.toString()
    }
}
