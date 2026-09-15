package com.bbh.me262.scan

import burp.api.montoya.scanner.audit.issues.AuditIssue
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity

object Issues {
    /** Rank so we can filter by a minimum severity. Higher = worse. */
    fun rank(sev: AuditIssueSeverity): Int = when (sev) {
        AuditIssueSeverity.HIGH -> 4
        AuditIssueSeverity.MEDIUM -> 3
        AuditIssueSeverity.LOW -> 2
        AuditIssueSeverity.INFORMATION -> 1
        AuditIssueSeverity.FALSE_POSITIVE -> 0
    }

    fun parseMinSeverity(name: String?): Int {
        if (name == null) return 0
        return runCatching { rank(AuditIssueSeverity.valueOf(name.uppercase())) }.getOrDefault(0)
    }

    fun line(issue: AuditIssue): String =
        "[${issue.severity()}/${issue.confidence()}] ${issue.name()}  <${issue.baseUrl()}>"

    fun detail(issue: AuditIssue): String {
        val d = runCatching { issue.detail() }.getOrNull()?.takeIf { it.isNotBlank() }
        return if (d == null) "" else "\n    " + d.replace("\n", "\n    ").take(1500)
    }
}
