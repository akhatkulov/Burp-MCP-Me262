package com.bbh.me262.util

import java.io.ByteArrayOutputStream

/**
 * Parse Burp-style insertion-point markers out of a request body.
 *
 * Montoya's Intruder API can pre-set payload positions when a request is staged
 * (via HttpRequestTemplate's offset list) but cannot choose an attack type, load
 * a payload list, or launch the attack — those have no API, so automated attacks
 * with results stay in the `fuzz` tool. This helper powers the one thing that is
 * possible: mark positions for the operator so they land in Intruder ready to go.
 *
 * Markers come in pairs — the Nth `§...§` region (default marker `§`) becomes one
 * insertion point. Offsets are byte offsets into the STRIPPED content (markers
 * removed), which is what HttpRequestTemplate expects. Pure (no Montoya types) so
 * it is unit-testable; the tool maps the int pairs to core.Range.
 */
object IntruderMarkers {

    /** Returns the content with markers removed, plus (startInclusive, endExclusive) byte offsets per position. */
    fun strip(content: ByteArray, marker: ByteArray): Pair<ByteArray, List<Pair<Int, Int>>> {
        require(marker.isNotEmpty()) { "marker must not be empty" }
        val out = ByteArrayOutputStream(content.size)
        val ranges = ArrayList<Pair<Int, Int>>()
        var pendingStart = -1
        var i = 0
        while (i < content.size) {
            if (matchesAt(content, marker, i)) {
                if (pendingStart < 0) {
                    pendingStart = out.size()
                } else {
                    ranges.add(pendingStart to out.size()) // end exclusive
                    pendingStart = -1
                }
                i += marker.size
            } else {
                out.write(content[i].toInt())
                i++
            }
        }
        require(pendingStart < 0) { "unbalanced markers: odd number of '${String(marker)}' found" }
        return out.toByteArray() to ranges
    }

    private fun matchesAt(haystack: ByteArray, needle: ByteArray, at: Int): Boolean {
        if (at + needle.size > haystack.size) return false
        for (k in needle.indices) if (haystack[at + k] != needle[k]) return false
        return true
    }
}
