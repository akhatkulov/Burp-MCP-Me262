package com.bbh.me262.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Filter predicate shared by get_proxy_history and sitemap_query so both support
 * the same richer filtering: URL substring/regex, method, status codes, MIME type
 * and response body length. Pure (no Montoya types) so it is unit-testable; the
 * tools feed it already-extracted fields.
 */
class HistoryFilters private constructor(
    private val contains: String?,
    private val regex: Regex?,
    private val method: String?,
    private val statuses: Set<Int>?,
    private val mime: String?,
    private val minLength: Int?,
    private val maxLength: Int?,
) {
    /** A row to test. mimeName is Burp's MimeType.name() or null; -1 length = unknown. */
    fun matches(method: String, url: String, status: Int?, mimeName: String?, bodyLength: Int): Boolean {
        if (contains != null && !url.contains(contains)) return false
        if (regex != null && !regex.containsMatchIn(url)) return false
        if (this.method != null && !this.method.equals(method, ignoreCase = true)) return false
        if (statuses != null && (status == null || status !in statuses)) return false
        if (mime != null && (mimeName == null || !mimeName.contains(mime, ignoreCase = true))) return false
        if (minLength != null && bodyLength < minLength) return false
        if (maxLength != null && bodyLength > maxLength) return false
        return true
    }

    companion object {
        /** Build from standard tool arguments. `status` accepts a single int or an array. */
        fun from(arguments: JsonObject): HistoryFilters {
            val statuses: Set<Int>? = when (val s = arguments["status"]) {
                is JsonArray -> s.mapNotNull { it.jsonPrimitive.intOrNull }.toSet().ifEmpty { null }
                else -> arguments["status"]?.jsonPrimitive?.intOrNull?.let { setOf(it) }
            }
            return HistoryFilters(
                contains = arguments["contains"]?.jsonPrimitive?.contentOrNull,
                regex = arguments["regex"]?.jsonPrimitive?.contentOrNull?.let { Regex(it) },
                method = arguments["method"]?.jsonPrimitive?.contentOrNull,
                statuses = statuses,
                mime = arguments["mime_type"]?.jsonPrimitive?.contentOrNull,
                minLength = arguments["min_length"]?.jsonPrimitive?.intOrNull,
                maxLength = arguments["max_length"]?.jsonPrimitive?.intOrNull,
            )
        }
    }
}
