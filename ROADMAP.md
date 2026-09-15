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

## v0.3 — native fuzzer (Intruder replacement)
- [ ] `fuzz` — sniper/cluster-bomb over `api.http().sendRequests()`, with
      payload lists, grep-match/extract, length/status diffing, concurrency cap
- [ ] `send_to_intruder` / `send_to_repeater` convenience wrappers

## v0.4 — visibility & control
- [ ] `sitemap_query` (regex on api.siteMap())
- [ ] `scope_add` / `scope_remove` / `scope_check`
- [ ] `get_collaborator_*` (Pro) generate + poll interactions
- [ ] `run_bcheck` (Scanner.bChecks)
- [ ] `set_intercept` / `set_task_engine` toggles

## Hardening / ops
- [ ] Config UI tab (toggle server, port, "allow config edits", per-tool enable)
- [ ] Optional bearer token on the MCP endpoint
- [ ] Structured JSON tool outputs (not just text) where useful
- [ ] Per-tool ROE guard hook (refuse out-of-scope hosts)
- [ ] Unit tests for the JSON-RPC/SSE layer (transport is Burp-independent)

## Known API ceilings (cannot fix in MCP)
- No cross-extension invocation / enumeration in Montoya.
- No programmatic "run Intruder UI attack + collect results" — we build our own.
- Macros / session-handling rule *execution* are not directly drivable.
