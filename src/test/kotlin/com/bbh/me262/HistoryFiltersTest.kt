package com.bbh.me262

import com.bbh.me262.util.HistoryFilters
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryFiltersTest {

    @Test fun noFiltersMatchesEverything() {
        val f = HistoryFilters.from(buildJsonObject {})
        assertTrue(f.matches("GET", "https://x/a", 200, "HTML", 10))
    }

    @Test fun statusArrayKeepsOnlyListed() {
        val f = HistoryFilters.from(buildJsonObject {
            put("status", buildJsonArray { add(200); add(302) })
        })
        assertTrue(f.matches("GET", "https://x/a", 200, "HTML", 10))
        assertTrue(f.matches("GET", "https://x/a", 302, "HTML", 10))
        assertFalse(f.matches("GET", "https://x/a", 404, "HTML", 10))
    }

    @Test fun statusSingleIntWorks() {
        val f = HistoryFilters.from(buildJsonObject { put("status", 500) })
        assertTrue(f.matches("GET", "https://x/a", 500, null, 0))
        assertFalse(f.matches("GET", "https://x/a", 200, null, 0))
    }

    @Test fun methodIsCaseInsensitive() {
        val f = HistoryFilters.from(buildJsonObject { put("method", "post") })
        assertTrue(f.matches("POST", "https://x/a", 200, null, 0))
        assertFalse(f.matches("GET", "https://x/a", 200, null, 0))
    }

    @Test fun mimeTypeSubstringCaseInsensitive() {
        val f = HistoryFilters.from(buildJsonObject { put("mime_type", "json") })
        assertTrue(f.matches("GET", "https://x/a", 200, "JSON", 5))
        assertFalse(f.matches("GET", "https://x/a", 200, "HTML", 5))
        assertFalse(f.matches("GET", "https://x/a", 200, null, 5))
    }

    @Test fun lengthBounds() {
        val f = HistoryFilters.from(buildJsonObject { put("min_length", 100); put("max_length", 500) })
        assertTrue(f.matches("GET", "https://x/a", 200, null, 250))
        assertFalse(f.matches("GET", "https://x/a", 200, null, 50))
        assertFalse(f.matches("GET", "https://x/a", 200, null, 600))
    }

    @Test fun regexOnUrl() {
        val f = HistoryFilters.from(buildJsonObject { put("regex", "/api/v[0-9]+/users") })
        assertTrue(f.matches("GET", "https://x/api/v2/users/1", 200, null, 0))
        assertFalse(f.matches("GET", "https://x/api/users/1", 200, null, 0))
    }

    @Test fun containsSubstring() {
        val f = HistoryFilters.from(buildJsonObject { put("contains", "admin") })
        assertTrue(f.matches("GET", "https://x/admin/panel", 200, null, 0))
        assertFalse(f.matches("GET", "https://x/user/panel", 200, null, 0))
    }

    @Test fun statusNullFailsWhenFilterSet() {
        val f = HistoryFilters.from(buildJsonObject { put("status", 200) })
        assertFalse(f.matches("GET", "https://x/a", null, null, 0))
    }
}
