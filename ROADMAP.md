# Roadmap — toward a "mature" Burp MCP

Legend: [x] done  [~] partial  [ ] planned

## v0.1 — core (done)
- [x] Own SSE + JSON-RPC transport (no MCP SDK dependency)
- [x] Tool registry + clean `Tool` contract
- [x] `send_http_request`, `get_proxy_history`, `start_active_scan`
- [x] Loopback bind + Origin check (DNS-rebinding hardening)

## v0.2 — Scanner loop (the Pro unlock) — done
- [x] `get_scanner_issues` — read audit issues (name, severity, confidence, URL, detail)
- [x] `scan_status` — progress/insertion-point counts for a running audit
- [x] `start_crawl` (Scanner.startCrawl)  [ ] crawl_and_audit
- [x] `generate_report` — HTML/XML via Scanner.generateReport
- [x] Track Audit/Crawl handles in a scan registry (ScanRegistry) so issues map to a scan id
- [x] `stop_scan` — cancel + drop a running audit/crawl (ScanTask.delete())
- [x] `passive_scan` — passive audit (LEGACY_PASSIVE_AUDIT_CHECKS)

## v0.3 — native fuzzer (Intruder replacement) — done
- [x] `fuzz` — sniper/clusterbomb/pitchfork over Burp HTTP, payload list/wordlist,
      grep-match, regex extract, status/length distribution, concurrency cap
- [ ] `send_to_intruder` / `send_to_repeater` convenience wrappers

## v0.4 — visibility & control — mostly done
- [x] `sitemap_query` (URL filter on api.siteMap())
- [x] `scope_add` / `scope_check` / `scope_remove`
- [x] `get_collaborator_*` (Pro) generate + poll interactions
- [x] `import_bcheck` (Scanner.bChecks) — runs in subsequent audits
- [x] `set_intercept` / `set_task_engine` (BurpSuite.taskExecutionEngine)

## v0.6 — send-to, utilities, config (done)
- [x] `send_to_repeater`, `send_to_intruder`, `send_to_organizer`
- [x] `transform` (url/base64/base64url/hex/html/jwt_decode), `random_string`
- [x] `export_burp_config` / `import_burp_config` (gated by -Dme262.allowConfigEdits)

## v0.7 — niche read/compare (done)
- [x] `get_websocket_history` (Proxy WebSocket messages)
- [x] `send_to_comparer`

## Hardening / ops
- [x] Suite status tab (endpoint + tool list)  [ ] interactive toggles
- [x] Optional bearer token on the MCP endpoint (-Dme262.token)
- [~] Structured JSON tool outputs — `structuredContent` on scan_status + get_scanner_issues (opt-in Tool.run())
- [x] ROE guard on active tools (Burp scope; -Dme262.allowOutOfScope override)
- [x] Unit + integration tests: combinatorics, registry, dispatch, live SSE transport, transform codecs, structured output (21)

## Known API ceilings (cannot fix in MCP)
- No cross-extension invocation / enumeration in Montoya.
- No programmatic "run Intruder UI attack + collect results" — we build our own.
- No single-call `crawlAndAudit` in Montoya 2025.5 — use start_crawl + start_active_scan.
- Macros / session-handling rule *execution* are not directly drivable.
