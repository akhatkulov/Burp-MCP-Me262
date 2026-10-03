package com.bbh.me262.safety

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.RedirectionMode

/**
 * Rules-of-engagement guard. Every tool that sends traffic (single/batch sends,
 * fuzz, active/passive scan, crawl) must pass its target through here first.
 * In-scope is defined by Burp's own Target > Scope, so the operator controls it
 * in one familiar place. Checks use the full URL (scheme, host, port, path), so
 * port- and path-restricted scope rules are honoured. Override for lab work with
 * -Dme262.allowOutOfScope=true.
 */
class RoeGuard(
    private val inScope: (String) -> Boolean,
    val allowOutOfScope: Boolean = System.getProperty("me262.allowOutOfScope")?.toBoolean() ?: false,
) {
    constructor(api: MontoyaApi) : this({ api.scope().isInScope(it) })

    fun isAllowed(url: String): Boolean = allowOutOfScope || inScope(url)

    fun requireInScope(url: String) {
        if (!isAllowed(url)) {
            error(
                "REFUSED by ROE guard: '$url' is not in Burp's Target scope. " +
                "Add it (Burp > Target > Scope, or the scope_add tool), or start Burp with " +
                "-Dme262.allowOutOfScope=true to override.",
            )
        }
    }

    /**
     * Redirect policy when a caller opts into following redirects: Burp follows
     * only hops that stay inside the Target scope, and returns the 3xx as-is when
     * the next hop would leave it. The lab override follows everything.
     */
    fun redirectionMode(): RedirectionMode =
        if (allowOutOfScope) RedirectionMode.ALWAYS else RedirectionMode.IN_SCOPE

    companion object {
        /**
         * The URL a raw request will actually hit: scheme/host/port come from the
         * connection target, path from the request line (origin-form, or the path
         * of an absolute-form target). Default ports are omitted, as Burp does.
         */
        fun targetUrl(tls: Boolean, host: String, port: Int, raw: String): String {
            val scheme = if (tls) "https" else "http"
            val authority = if ((tls && port == 443) || (!tls && port == 80)) host else "$host:$port"
            val target = raw.lineSequence().firstOrNull()?.trim()?.split(' ')?.getOrNull(1).orEmpty()
            val path = when {
                target.startsWith("/") -> target
                target.contains("://") -> target.substringAfter("://").let { rest ->
                    val i = rest.indexOf('/')
                    if (i >= 0) rest.substring(i) else "/"
                }
                else -> "/"
            }
            return "$scheme://$authority$path"
        }
    }
}
