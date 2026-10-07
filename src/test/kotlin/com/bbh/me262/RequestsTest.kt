package com.bbh.me262

import com.bbh.me262.util.Requests
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestsTest {

    @Test fun normalizeRawRewritesLoneLfToCrlf() {
        val raw = "GET / HTTP/1.1\nHost: x\nCookie: a=b\n\n"
        val out = Requests.normalizeRaw(raw)
        // every line break is CRLF: stripping CRLF leaves no lone LF
        assertEquals(0, out.replace("\r\n", "").count { it == '\n' })
        assertTrue(out.startsWith("GET / HTTP/1.1\r\n"))
    }

    @Test fun normalizeRawLeavesExistingCrlfIntact() {
        val raw = "GET / HTTP/1.1\r\nHost: x\r\n\r\n"
        assertEquals(raw, Requests.normalizeRaw(raw))
    }

    @Test fun normalizeRawHandlesLoneCr() {
        assertEquals("A\r\nB", Requests.normalizeRaw("A\rB"))
    }

    @Test fun parseCookieStringBuildsOrderedPairs() {
        val pairs = Requests.parseCookies(JsonPrimitive("session=abc; theme=dark"))
        assertEquals(listOf("session", "theme"), pairs.keys.toList())
        assertEquals("abc", pairs["session"])
        assertEquals("dark", pairs["theme"])
    }

    @Test fun parseCookieObjectBuildsPairs() {
        val obj = buildJsonObject { put("session", "abc"); put("csrf", "t0ken") }
        val pairs = Requests.parseCookies(obj)
        assertEquals("abc", pairs["session"])
        assertEquals("t0ken", pairs["csrf"])
    }

    @Test fun cookieHeaderValueJoinsPairs() {
        assertEquals("a=1; b=2", Requests.cookieHeaderValue(linkedMapOf("a" to "1", "b" to "2")))
    }

    @Test fun cookieHeaderValueKeepsEqualsForEmptyValue() {
        // An empty value must still render as "k=", not a bare "k" (which drops the pair shape).
        assertEquals("a=; b=2", Requests.cookieHeaderValue(linkedMapOf("a" to "", "b" to "2")))
    }

    @Test fun parseHeadersFromStringArray() {
        val arr = buildJsonArray {
            add("Content-Type: application/json")
            add("X-Api-Key: secret")
        }
        val h = Requests.parseHeaders(arr)
        assertEquals("Content-Type" to "application/json", h[0])
        assertEquals("X-Api-Key" to "secret", h[1])
    }

    @Test fun parseHeadersFromObject() {
        val obj = buildJsonObject { put("Accept", "*/*") }
        assertEquals(listOf("Accept" to "*/*"), Requests.parseHeaders(obj))
    }
}
