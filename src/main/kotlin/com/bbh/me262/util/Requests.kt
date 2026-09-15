package com.bbh.me262.util

import burp.api.montoya.http.HttpService
import burp.api.montoya.http.message.requests.HttpRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Build an HttpRequest from tool args: either 'url', or 'raw' + host/port/tls. */
object Requests {
    fun build(arguments: JsonObject): HttpRequest {
        val url = arguments["url"]?.jsonPrimitive?.contentOrNull
        val raw = arguments["raw"]?.jsonPrimitive?.contentOrNull
        return when {
            raw != null -> {
                val host = arguments["host"]?.jsonPrimitive?.contentOrNull ?: error("'raw' requires 'host'")
                val port = arguments["port"]?.jsonPrimitive?.intOrNull ?: error("'raw' requires 'port'")
                val tls = arguments["tls"]?.jsonPrimitive?.booleanOrNull ?: (port == 443)
                HttpRequest.httpRequest(HttpService.httpService(host, port, tls), raw)
            }
            url != null -> HttpRequest.httpRequestFromUrl(url)
            else -> error("provide 'url' or 'raw' (+host/port/tls)")
        }
    }
}
