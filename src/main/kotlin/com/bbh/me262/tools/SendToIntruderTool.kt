package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.ByteArray as BurpByteArray
import burp.api.montoya.core.Range
import burp.api.montoya.intruder.HttpRequestTemplate
import com.bbh.me262.mcp.Tool
import com.bbh.me262.util.IntruderMarkers
import com.bbh.me262.util.Requests
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Send a request to Burp Intruder, optionally with payload positions pre-marked. */
class SendToIntruderTool(private val api: MontoyaApi) : Tool {
    override val name = "send_to_intruder"
    override val description =
        "Place a request in Burp Intruder. Provide 'url' or 'raw'+host/port/tls (plus the usual " +
        "method/path/headers/cookies/cookie_file/use_cookie_jar/body), optional 'tab'. " +
        "Pre-mark payload positions by wrapping them in a 'marker' (default '§'), e.g. \"id=§1§\"; " +
        "the request is staged with those insertion points set. " +
        "NOTE: Montoya cannot choose the attack type, load a payload list, or launch the attack — " +
        "for an automated attack that returns results, use the 'fuzz' tool instead."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") { put("type", "string") }
            putJsonObject("raw") { put("type", "string") }
            putJsonObject("host") { put("type", "string") }
            putJsonObject("port") { put("type", "integer") }
            putJsonObject("tls") { put("type", "boolean") }
            putJsonObject("method") { put("type", "string") }
            putJsonObject("path") { put("type", "string") }
            putJsonObject("headers") { put("type", "array"); putJsonObject("items") { put("type", "string") } }
            putJsonObject("cookies") { put("type", "string") }
            putJsonObject("cookie_file") { put("type", "string") }
            putJsonObject("use_cookie_jar") { put("type", "boolean") }
            putJsonObject("body") { put("type", "string") }
            putJsonObject("tab") { put("type", "string") }
            putJsonObject("marker") {
                put("type", "string")
                put("description", "wrap each payload position in this marker (default '§'); omit for no positions")
            }
        }
    }

    override fun execute(arguments: JsonObject): String {
        val request = Requests.build(arguments, api)
        val tab = arguments["tab"]?.jsonPrimitive?.contentOrNull
        val markerStr = if (arguments.containsKey("marker")) {
            arguments["marker"]?.jsonPrimitive?.contentOrNull ?: "§"
        } else null

        if (markerStr == null) {
            if (tab != null) api.intruder().sendToIntruder(request, tab) else api.intruder().sendToIntruder(request)
            return "Sent to Intruder${if (tab != null) " (tab: $tab)" else ""}: ${request.method()} ${request.url()}"
        }

        // Marker mode: strip markers, compute insertion points, stage as a template.
        val markerBytes = markerStr.toByteArray(Charsets.UTF_8)
        val (stripped, offsets) = IntruderMarkers.strip(request.toByteArray().getBytes(), markerBytes)
        require(offsets.isNotEmpty()) { "no '$markerStr' marker pairs found; wrap each payload position, e.g. id=${markerStr}1$markerStr" }
        val ranges: List<Range> = offsets.map { (start, end) -> Range.range(start, end) }
        val template = HttpRequestTemplate.httpRequestTemplate(BurpByteArray.byteArray(*stripped), ranges)
        val service = request.httpService()
        if (tab != null) api.intruder().sendToIntruder(service, template, tab)
        else api.intruder().sendToIntruder(service, template)
        return "Sent to Intruder with ${offsets.size} payload position(s)${if (tab != null) " (tab: $tab)" else ""}: " +
            "${request.method()} ${request.url()}\nUse 'fuzz' to run an automated attack and get results."
    }
}
