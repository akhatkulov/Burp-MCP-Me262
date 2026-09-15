package com.bbh.me262.safety

import burp.api.montoya.MontoyaApi

/**
 * Rules-of-engagement guard. Active tools (fuzz, active scan, crawl) must pass a
 * target through here first. In-scope is defined by Burp's own Target > Scope, so
 * the operator controls it in one familiar place. Override for lab work with
 * -Dme262.allowOutOfScope=true.
 */
class RoeGuard(private val api: MontoyaApi) {
    private val allowOutOfScope: Boolean =
        System.getProperty("me262.allowOutOfScope")?.toBoolean() ?: false

    fun requireInScope(url: String) {
        if (allowOutOfScope) return
        if (!api.scope().isInScope(url)) {
            error(
                "REFUSED by ROE guard: '$url' is not in Burp's Target scope. " +
                "Add it (Burp > Target > Scope, or the scope_add tool), or start Burp with " +
                "-Dme262.allowOutOfScope=true to override.",
            )
        }
    }
}
