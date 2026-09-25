package com.bbh.me262.tools

import burp.api.montoya.MontoyaApi
import com.bbh.me262.mcp.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/** Encode/decode helpers using Burp's utilities plus pure-JDK codecs. */
class TransformTool(private val api: MontoyaApi) : Tool {
    override val name = "transform"
    override val description =
        "Encode/decode a string. 'op' is one of: url_encode, url_decode, url_encode_all, " +
        "base64_encode, base64_decode, base64url_encode, base64url_decode, hex_encode, hex_decode, " +
        "html_encode, html_decode, jwt_decode (decode a JWT's header+payload, NO signature check). " +
        "'input' is the text."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("op") { put("type", "string") }
            putJsonObject("input") { put("type", "string") }
        }
        putJsonArray("required") { add("op"); add("input") }
    }

    private val pretty = Json { prettyPrint = true }

    override fun execute(arguments: JsonObject): String {
        val op = arguments["op"]?.jsonPrimitive?.contentOrNull ?: error("'op' is required")
        val input = arguments["input"]?.jsonPrimitive?.contentOrNull ?: error("'input' is required")
        // Resolved only for ops that need Burp's utilities; pure-JDK ops never touch it.
        val u by lazy { api.utilities() }
        return when (op.lowercase()) {
            "url_encode" -> u.urlUtils().encode(input)
            "url_decode" -> u.urlUtils().decode(input)
            "url_encode_all" -> urlEncodeAll(input)
            "base64_encode" -> u.base64Utils().encodeToString(input)
            "base64_decode" -> u.base64Utils().decode(input).toString()
            "base64url_encode" -> Base64.getUrlEncoder().withoutPadding()
                .encodeToString(input.toByteArray(Charsets.UTF_8))
            "base64url_decode" -> String(base64UrlDecode(input), Charsets.UTF_8)
            "hex_encode" -> input.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
            "hex_decode" -> hexDecode(input)
            "html_encode" -> u.htmlUtils().encode(input)
            "html_decode" -> u.htmlUtils().decode(input)
            "jwt_decode" -> jwtDecode(input)
            else -> error("unknown op '$op'")
        }
    }

    /** Percent-encode every byte (aggressive; useful for WAF-bypass work). */
    private fun urlEncodeAll(s: String): String =
        s.toByteArray(Charsets.UTF_8).joinToString("") { "%%%02X".format(it) }

    private fun hexDecode(s: String): String {
        val clean = s.trim().replace(" ", "").removePrefix("0x")
        require(clean.length % 2 == 0) { "hex input must have an even number of digits" }
        val bytes = ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte()
                ?: error("invalid hex at position ${i * 2}")
        }
        return String(bytes, Charsets.UTF_8)
    }

    /** Base64url-decode, tolerating missing padding. */
    private fun base64UrlDecode(s: String): ByteArray {
        val padded = s.trim().let { it + "=".repeat((4 - it.length % 4) % 4) }
        return Base64.getUrlDecoder().decode(padded)
    }

    private fun jwtDecode(token: String): String {
        val parts = token.trim().split(".")
        require(parts.size >= 2) { "not a JWT: expected header.payload.signature" }
        val header = decodeSegment(parts[0])
        val payload = decodeSegment(parts[1])
        return "HEADER:\n$header\n\nPAYLOAD:\n$payload\n\n(signature NOT verified)"
    }

    private fun decodeSegment(seg: String): String {
        val raw = String(base64UrlDecode(seg), Charsets.UTF_8)
        return runCatching { pretty.encodeToString(JsonElement.serializer(), pretty.parseToJsonElement(raw)) }
            .getOrDefault(raw)
    }
}
