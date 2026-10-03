package com.bbh.me262

import burp.api.montoya.http.RedirectionMode
import com.bbh.me262.safety.RoeGuard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoeGuardTest {
    // Scope restricted by port and path, like a real "only the API on :8443" rule.
    private val scope: (String) -> Boolean = { it.startsWith("https://t.example:8443/api/") }

    @Test fun refusesOutOfScopeWithClearMessage() {
        val roe = RoeGuard(scope, allowOutOfScope = false)
        val e = assertFailsWith<IllegalStateException> { roe.requireInScope("https://other.example/") }
        assertTrue(e.message!!.startsWith("REFUSED by ROE guard"))
        roe.requireInScope("https://t.example:8443/api/users")
    }

    @Test fun overrideAllowsEverythingAndFollowsAllRedirects() {
        val roe = RoeGuard(scope, allowOutOfScope = true)
        assertTrue(roe.isAllowed("https://other.example/"))
        assertEquals(RedirectionMode.ALWAYS, roe.redirectionMode())
    }

    @Test fun redirectsStayInScopeByDefault() {
        assertEquals(RedirectionMode.IN_SCOPE, RoeGuard(scope, allowOutOfScope = false).redirectionMode())
    }

    @Test fun targetUrlKeepsPortAndPath() {
        val raw = "GET /api/users?id=1 HTTP/1.1\r\nHost: t.example:8443\r\n\r\n"
        assertEquals("https://t.example:8443/api/users?id=1", RoeGuard.targetUrl(true, "t.example", 8443, raw))
    }

    @Test fun targetUrlOmitsDefaultPorts() {
        assertEquals("https://t.example/x", RoeGuard.targetUrl(true, "t.example", 443, "GET /x HTTP/1.1\r\n\r\n"))
        assertEquals("http://t.example/x", RoeGuard.targetUrl(false, "t.example", 80, "GET /x HTTP/1.1\r\n\r\n"))
    }

    @Test fun targetUrlUsesPathOfAbsoluteFormButConnectionHost() {
        // The connection goes to the service host no matter what the request line says.
        val raw = "GET http://evil.example/admin HTTP/1.1\r\n\r\n"
        assertEquals("http://t.example:8080/admin", RoeGuard.targetUrl(false, "t.example", 8080, raw))
    }

    @Test fun fuzzPayloadInPathIsCheckedPerRequest() {
        // Same host, but a payload walks the path out of the in-scope /api/ prefix.
        val roe = RoeGuard(scope, allowOutOfScope = false)
        val ok = RoeGuard.targetUrl(true, "t.example", 8443, "GET /api/users HTTP/1.1\r\n\r\n")
        val out = RoeGuard.targetUrl(true, "t.example", 8443, "GET /admin HTTP/1.1\r\n\r\n")
        assertTrue(roe.isAllowed(ok))
        assertFalse(roe.isAllowed(out))
        // The old host-root check dropped port and path entirely.
        assertFalse(roe.isAllowed("https://t.example/"))
    }
}
