# Spec — Maturity work (priority groups 1, 2, 3)

Status legend: [ ] planned  [~] in progress  [x] done

This spec covers three prioritised improvement groups agreed after the v1.0.2
code review. Group 1 is shippable immediately; Group 2 is additive; Group 3 is a
transport modernisation that gets its own PR.

---

## Group 1 — Quick wins (this PR)

### 1.1 `stop_scan` tool  [x]
**Gap:** Scans can be started (`start_active_scan`, `start_crawl`) and read
(`scan_status`, `get_scanner_issues`) but never stopped. Montoya's `Audit` and
`Crawl` both extend `ScanTask`, which exposes `delete()`.

> Note: `list_scans` was considered but dropped — `scan_status` with no
> `scan_id` already lists every tracked scan. No separate tool needed.

- New file: `tools/StopScanTool.kt`.
- Name: `stop_scan`. Required arg: `scan_id`.
- Behaviour: look up the id in `ScanRegistry` (audit or crawl), call
  `task.delete()`, then remove it from the registry so `scan_status` no longer
  reports a dead handle.
- Registry additions: `ScanRegistry.remove(id)` that drops from whichever map.
- Registered in `Me262Extension` in the v0.2 scanner block.
- Output: `Stopped and removed audit-3.` or `unknown scan id: X`.

### 1.2 Fix `FuzzTool` request-building bugs  [x]
Two defects in `FuzzTool.runOne`:

**(a) Stale Content-Length.** Markers are substituted into the raw template and
the request is rebuilt with `HttpRequest.httpRequest(service, raw)`, which keeps
whatever `Content-Length` the template had. Fuzzing a body with payloads of
different lengths sends a wrong `Content-Length`, so the server mis-reads the
body. Fix: after building, force a recompute via
`request.withBody(request.bodyToString())` (Montoya recalculates
`Content-Length` on `withBody`). Guarded by a new optional arg
`update_content_length` (default `true`) so raw-desync test cases can opt out.

**(b) Global marker replacement.** `raw.replace(marker, payload)` replaces every
occurrence of the marker literal, not just the intended insertion point. If the
marker string collides with other text this corrupts the request. Keep
`replace` for now (documented) but make markers stricter in the description, and
`require` the marker count of substitutions makes sense. (Full single-position
control is a larger change tracked in Group 2 / future.)

Add a `CombinatoricsTest`-style unit path is not possible (needs Montoya HTTP),
so this is covered by manual/lab verification noted in the PR.

### 1.3 Refresh stale docs  [x]
`docs/ARCHITECTURE.md` predates the v1.0.1/v1.0.2 rewrites and is wrong on two
points:
- Says transport is `com.sun.net.httpserver.HttpServer` — it is now a raw
  `java.net.ServerSocket` (chunked SSE), the whole reason it loads in Burp's
  jlink JRE. Fix the ASCII diagram and the "Transport" section.
- Says "No auth token yet (roadmap v0.4)" — the bearer token
  (`-Dme262.token`) shipped. Fix the "Security" section.

---

## Group 2 — Medium (follow-up, additive)

### 2.1 Extend `transform`  [x]
Today: `url`, `base64`, `html` encode/decode only. Add ops:
- `hex_encode` / `hex_decode`
- `gzip_compress` / `gzip_decompress` (java.util.zip)
- `jwt_decode` — split on `.`, base64url-decode header+payload, pretty-print
  JSON (no signature verification; clearly labelled).
- `ascii_hex`, `url_encode_all` (encode every byte) — cheap, high-value for WAF
  work.

All via `api.utilities()` where available, else pure JDK. Keep the single
`op`/`input` shape; extend the enum + description.

### 2.2 `passive_scan` tool  [x]
Only active audit is exposed. Add a tool that runs Burp's passive checks against
a supplied request/response (or a proxy-history item id) via
`api.scanner()` passive audit config. ROE-guarded like the active scan.

### 2.3 Structured tool output  [~]
Roadmap item "Structured JSON tool outputs" is still open. `tools/call`
currently returns only `content: [{type:text}]`. Add optional
`structuredContent` (MCP 2025-06) for the high-value readers (`scan_status`,
`get_scanner_issues`, `fuzz`) so clients can parse without regexing text. Keep
the text block for back-compat. Requires a small change to `Dispatcher`
(`toolResult` accepts an optional structured payload) and a way for a `Tool` to
return both — introduce a richer return type or a second optional interface
method with a default.

---

## Group 3 — Strategic (separate PR): modern MCP transport

### 3.1 Streamable HTTP transport  [x]
The current transport is the deprecated `2024-11-05` HTTP+SSE two-endpoint
scheme. The current MCP standard is **Streamable HTTP** (`2025-06-18`): a single
endpoint that accepts POST (JSON-RPC, may reply with either a JSON body or an
SSE stream) and optional GET for a server-initiated SSE stream, with a
`Mcp-Session-Id` header.

Plan:
- Keep the raw-`ServerSocket` base (still needed for Burp's JRE).
- Add a single `POST /` handler that returns `application/json` for simple
  request/response, upgrading to `text/event-stream` only when the response
  streams.
- Session via `Mcp-Session-Id` response/request header instead of the
  `?sessionId=` query param.
- Negotiate protocol version in `initialize`; keep the old SSE endpoint working
  in parallel for one release (dual-stack) so existing configs don't break.
- Bump advertised `protocolVersion` and add `MCP-Protocol-Version` request
  header handling.

### 3.2 Progress notifications  [ ] (deferred)

> Deferred: the current scan tools are non-blocking (they return a scan id and
> the client polls `scan_status`), so there is no long-running `tools/call` for a
> `progressToken` to attach to. Revisit alongside a blocking `await_scan` tool.

Long scans force the client to poll `scan_status`. With Streamable HTTP (or even
the current SSE stream), emit `notifications/progress` for a running audit/crawl:
a background poller reads `requestCount`/`statusMessage` and pushes progress
tied to the `tools/call` `progressToken`. Requires the dispatcher to be able to
send server-initiated messages, which the SSE session already supports.

### 3.3 (stretch) `resources` + `prompts` capabilities  [ ] (deferred)
Expose proxy history / site map as MCP **resources**, and ship a couple of
**prompts** ("audit this endpoint", "triage these issues"). Advertised in
`initialize` capabilities. Out of scope for the first Group 3 PR.

---

## Delivery order
1. Group 1 → one PR (`feat/maturity-1-2-3`): stop_scan, fuzz fix, docs.
2. Group 2 → follow-up PR once Group 1 merges.
3. Group 3 → dedicated PR (transport), reviewed on its own.
