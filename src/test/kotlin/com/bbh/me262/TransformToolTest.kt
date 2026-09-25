package com.bbh.me262

import burp.api.montoya.MontoyaApi
import com.bbh.me262.tools.TransformTool
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Covers the pure-JDK transform ops (no api.utilities() call), so a MontoyaApi
 * proxy that throws on any use is enough — it proves these ops never touch Burp.
 */
class TransformToolTest {
    private val api = Proxy.newProxyInstance(
        MontoyaApi::class.java.classLoader,
        arrayOf(MontoyaApi::class.java),
    ) { _, method, _ -> error("MontoyaApi.${method.name} must not be called for this op") } as MontoyaApi

    private val tool = TransformTool(api)

    private fun run(op: String, input: String) =
        tool.execute(buildJsonObject { put("op", op); put("input", input) })

    @Test fun hexRoundTrip() {
        assertEquals("68656c6c6f", run("hex_encode", "hello"))
        assertEquals("hello", run("hex_decode", "68656c6c6f"))
        assertEquals("hello", run("hex_decode", "0x68 65 6c 6c 6f"))
    }

    @Test fun base64UrlRoundTripNoPadding() {
        val enc = run("base64url_encode", "hi?>")
        assertTrue(!enc.contains('=') && !enc.contains('+') && !enc.contains('/'))
        assertEquals("hi?>", run("base64url_decode", enc))
    }

    @Test fun urlEncodeAllEncodesEveryByte() {
        assertEquals("%41%42", run("url_encode_all", "AB"))
    }

    @Test fun jwtDecodeShowsHeaderAndPayload() {
        // {"alg":"none"} . {"sub":"1"} . (no sig)
        val jwt = "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0."
        val out = run("jwt_decode", jwt)
        assertTrue(out.contains("\"alg\": \"none\""), out)
        assertTrue(out.contains("\"sub\": \"1\""), out)
        assertTrue(out.contains("signature NOT verified"))
    }

    @Test fun hexDecodeRejectsOddLength() {
        assertFailsWith<IllegalArgumentException> { run("hex_decode", "abc") }
    }

    @Test fun unknownOpFails() {
        assertFailsWith<IllegalStateException> { run("rot13", "x") }
    }
}
