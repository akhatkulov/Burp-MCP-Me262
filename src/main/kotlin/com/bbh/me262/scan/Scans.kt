package com.bbh.me262.scan

import burp.api.montoya.MontoyaApi
import burp.api.montoya.scanner.AuditConfiguration
import burp.api.montoya.scanner.Crawl
import burp.api.montoya.scanner.CrawlConfiguration
import burp.api.montoya.scanner.audit.Audit

/**
 * Guard rails around Burp's Scanner. The Scanner only exists in Burp Suite
 * Professional; in Community edition startAudit/startCrawl can return null or
 * throw, which previously surfaced to the user as a bare `"audit" is null` NPE.
 * These wrappers turn that into one clear, actionable message.
 */
object Scans {
    private const val PRO_HINT =
        "This requires Burp Suite Professional — the Scanner is not available in this edition " +
        "(Community, or a feature-limited setup). Active scan, passive scan and crawl all need Pro."

    fun startAudit(api: MontoyaApi, config: AuditConfiguration): Audit {
        val audit = try {
            api.scanner().startAudit(config)
        } catch (t: Throwable) {
            error("Could not start audit: ${t.message ?: t.javaClass.simpleName}. $PRO_HINT")
        }
        return audit ?: error("Could not start audit: Burp returned no audit handle. $PRO_HINT")
    }

    fun startCrawl(api: MontoyaApi, config: CrawlConfiguration): Crawl {
        val crawl = try {
            api.scanner().startCrawl(config)
        } catch (t: Throwable) {
            error("Could not start crawl: ${t.message ?: t.javaClass.simpleName}. $PRO_HINT")
        }
        return crawl ?: error("Could not start crawl: Burp returned no crawl handle. $PRO_HINT")
    }
}
