# Changelog

## 1.1.1 — 2026-10-03
### Scope guard hardening
- `send_http_request` and `send_http_requests` are now ROE-guarded: targets
  outside Burp's Target scope are refused (batch rows are marked `(refused)`
  and never sent). Previously only fuzz/scan/crawl were guarded.
- Fix `fuzz` scope check: it validated only `scheme://host/`, dropping port and
  path, so port- or path-restricted scope rules were misjudged. It now checks
  the full URL of every generated request (a payload can land in the path) and
  refuses the whole run — sending nothing — if any falls outside scope.
- `send_http_request` gains `follow_redirects` (default false). When set, Burp
  follows only hops that stay in scope (`RedirectionMode.IN_SCOPE`); the 3xx is
  returned as-is when the next hop would leave it. Default behaviour is
  unchanged: redirects are never followed automatically.
- `start_crawl` description now states that only the seed is checked by Me262;
  discovered links follow Burp's crawl settings.
- Tests: +7 `RoeGuard` tests (47 -> 54).

## 1.1.0 — 2026-10-03
### Group 4 — authenticated requests, richer queries, batch
- Fix the main footgun behind empty "HTTP 0 / no response" replies:
  `send_http_request` now normalises lone `LF` line endings in `raw` to `CRLF`
  and recomputes `Content-Length` to match the body, so long session cookies and
  edited bodies go through cleanly. New structured fields avoid hand-crafting raw
  entirely — `method`, `path`, `headers` (array of `"Name: Value"` or object),
  `cookies` (`"a=b; c=d"` or object), `cookie_file` (path), `use_cookie_jar`
  (merge Burp's cookie jar for the host), `body`, `max_body`. A null response now
  reports status 0 with bytes-sent instead of a bare message. The same builder
  backs `send_to_repeater` / `send_to_intruder`, so they gain the fields too.
- Add `send_http_requests` — batch send through Burp's pipelined `sendRequests`,
  returning a status/length table; ideal for IDOR/access-control (same request,
  different session cookies). Optional `match`, `include_body`.
- Add `get_proxy_entry` — return the full request/response bytes for a Proxy
  history entry by `#index` (companion to `get_proxy_history`, which stays
  metadata-only). `max_body`, `request_only`/`response_only`.
- Extend `get_proxy_history` and `sitemap_query` filtering beyond `contains`:
  `regex` (URL), `method`, `status` (int or array), `mime_type`,
  `min_length`/`max_length` (response body bytes). `get_proxy_history` prints a
  stable `#index` and an optional `include_body` preview.
- Fix `start_active_scan` / `passive_scan` / `start_crawl`: guard the Scanner
  handle so Community edition (or any setup without the Scanner) returns one
  clear "requires Burp Suite Professional" message instead of a bare
  `"audit" is null` NPE. Now 30 tools.
- `start_active_scan` can audit a specific request, not just a bare-GET URL:
  pass `raw`+host/port/tls (with the shared builder's headers/cookies/body) or
  an `index` from `get_proxy_history`. This is how installed scan-check
  extensions (Active Scan++, HTTP Request Smuggler, BChecks, …) run against the
  real request — Burp derives insertion points automatically. Note: Burp
  isolates extensions, so there is no Montoya API to invoke another extension's
  menu/buttons directly; participating via the audit is the supported path, and
  their findings already surface through `get_scanner_issues` (site-map issues).
- `send_to_intruder` can pre-mark payload positions: wrap each position in a
  `marker` (default `§`), e.g. `id=§1§`, and the request is staged in Intruder
  with those insertion points set. Montoya has no API to pick the attack type,
  load a payload list, or launch the attack, so automated attacks with results
  stay in `fuzz` — the tool says so. It also inherits the shared builder's
  method/path/headers/cookies/cookie_file/use_cookie_jar/body fields.
- Tests: +24 unit tests (23 -> 47) covering the request builder (CRLF
  normalisation, cookie/header parsing), the shared history/site-map filters,
  and Intruder marker parsing (byte-offset insertion points).

### Group 3 — transport modernisation
- Streamable HTTP transport (`2025-06-18`): a single `POST /` returns the
  JSON-RPC reply directly (`application/json`, or one-shot SSE when the client
  accepts only `text/event-stream`), issues an `Mcp-Session-Id` on `initialize`,
  and honours `DELETE /` to end a session. The endpoint is now dual-stack — the
  legacy HTTP+SSE (`2024-11-05`) path still works on `GET /` + `/?sessionId=`.
- `initialize` negotiates the protocol version (echoes the client's when
  supported: 2024-11-05 / 2025-03-26 / 2025-06-18).
- Deferred (documented in SPEC): `notifications/progress` (scan tools are
  non-blocking/polled) and `resources`/`prompts` capabilities.

### Group 2 — read/compare + structured output
- Add `passive_scan` — Burp Pro passive audit (no active payloads) returning a
  scan id, read via `scan_status`/`get_scanner_issues`. Now 28 tools.
- Extend `transform` with `url_encode_all`, `base64url_encode/decode`,
  `hex_encode/decode`, and `jwt_decode` (header+payload, no signature check).
- Structured tool output: `tools/call` now includes MCP `structuredContent`
  alongside the text block for `scan_status` and `get_scanner_issues`. Opt-in
  via `Tool.run()` (default stays text-only), so existing tools are unchanged.

### Group 1 — quick wins
- Add `stop_scan` — cancel and drop an audit/crawl started via Me262
  (`ScanTask.delete()`); registry gains `remove(id)`.
- Fix `fuzz`: recompute `Content-Length` after payload substitution so
  body-fuzzing sends a correct length (new `update_content_length` arg, default
  true; set false for desync tests).
- Docs: correct `ARCHITECTURE.md` (raw `ServerSocket` transport, bearer token
  shipped); add `docs/SPEC-maturity.md` planning groups 1–3.

## 1.0.2
- Fix: Me262 status tab showed raw <html> markup. Burp's Look-and-Feel disables
  HTML in Swing components, so the JLabel rendered literally. Rebuilt the tab as
  a plain monospaced JTextArea.

## 1.0.1
- Fix: rewrite the SSE transport on a raw java.net.ServerSocket. Burp's trimmed
  (jlink) JRE has no com.sun.net.httpserver, which threw ClassNotFoundException
  on load. Now uses only java.base (chunked SSE). Loads inside Burp.

## 1.0.0
- Milestone: 26 tools across HTTP, Scanner (Pro), fuzzer, send-to, scope,
  sitemap, Collaborator, BChecks, control, utilities, config.
- SSE transport now covered by an integration test (real handshake, no Burp).
- 13 unit/integration tests total; MIT license; extension-compatibility study.

## 0.10.0
- Extract pure `Dispatcher` (JSON-RPC) from `McpServer`; 6 dispatch tests.

## 0.9.0
- Extract fuzzer `Combinatorics`; unit tests for it and the tool registry.

## 0.8.0
- `fuzz` gains clusterbomb + pitchfork modes (multi-position).

## 0.7.0
- `get_websocket_history`, `send_to_comparer`.

## 0.6.0
- Send-to family (repeater/intruder/organizer), `transform`, `random_string`,
  `set_task_engine`, `export_burp_config`, `import_burp_config` (gated).

## 0.5.0
- `scope_remove`, `set_intercept`, `import_bcheck`; Burp status tab.

## 0.4.0
- ROE guard (Burp scope), bearer token; `scope_check`/`scope_add`,
  `sitemap_query`, Collaborator payload + interactions.

## 0.3.0
- Native sniper fuzzer (`fuzz`).

## 0.2.0
- Scanner loop: `start_active_scan`/`start_crawl`/`scan_status`/
  `get_scanner_issues`/`generate_report` + scan registry.

## 0.1.0
- Montoya extension scaffold; own SSE + JSON-RPC transport; core HTTP tools.
