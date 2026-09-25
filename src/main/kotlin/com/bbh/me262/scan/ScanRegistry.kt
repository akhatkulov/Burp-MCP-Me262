package com.bbh.me262.scan

import burp.api.montoya.scanner.audit.Audit
import burp.api.montoya.scanner.Crawl
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps handles to scans launched through Me262 so tools can read their status
 * and issues back by a stable id (audit-1, crawl-1, ...).
 */
class ScanRegistry {
    private val audits = ConcurrentHashMap<String, Audit>()
    private val crawls = ConcurrentHashMap<String, Crawl>()
    private val seq = AtomicInteger(0)

    fun addAudit(audit: Audit): String {
        val id = "audit-${seq.incrementAndGet()}"
        audits[id] = audit
        return id
    }

    fun addCrawl(crawl: Crawl): String {
        val id = "crawl-${seq.incrementAndGet()}"
        crawls[id] = crawl
        return id
    }

    fun audit(id: String): Audit? = audits[id]
    fun crawl(id: String): Crawl? = crawls[id]
    fun auditIds(): List<String> = audits.keys.sorted()
    fun crawlIds(): List<String> = crawls.keys.sorted()

    /** Drop a handle after it is stopped/deleted. Returns true if an id was removed. */
    fun remove(id: String): Boolean = (audits.remove(id) != null) || (crawls.remove(id) != null)
}
