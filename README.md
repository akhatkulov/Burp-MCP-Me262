# Burp-MCP-Me262

Our own Model Context Protocol (MCP) server for **Burp Suite Pro**, built as a
native **Montoya API** extension in Kotlin. Unlike a thin wrapper around the
stock MCP, this runs *inside* Burp's JVM, so it can reach the full Montoya
surface — including the Pro Scanner engine that the official MCP leaves out.

> Codename **Me262** — first of its kind, built for speed. Loopback-only.

## Why this exists

The official `PortSwigger/mcp-server` exposes ~27 tools but deliberately omits
the heavy Pro machinery (no active-scan launch, no report generation, no
BChecks, no native fuzzer). Because Montoya *does* expose those
(`Scanner.startAudit`, `startCrawl`, `generateReport`, `bChecks`), we fork the
idea, not the code, and add them ourselves.

**Honest ceiling:** Montoya is the limit. There is no API to enumerate or call
other extensions directly. Installed extensions are leveraged *indirectly* — any
extension that registers scan checks / BChecks / proxy / HTTP / session-handling
/ Intruder-payload hooks runs automatically when we push traffic or start a scan
through Me262. UI-only extension buttons stay manual.

## Build

Requires JDK 17+ (local JDK 17 is fine; Burp ships its own JRE 21).

```bash
./gradlew shadowJar
# -> build/libs/burp-mcp-me262-0.9.0.jar
```

If the Gradle wrapper jar is missing (no gradle installed yet), bootstrap once:

```bash
brew install gradle && gradle wrapper --gradle-version 8.10.2
```

### Tests

```bash
./gradlew test   # unit tests for the fuzzer combinatorics + tool registry
```

## Load into Burp

1. Burp Suite Pro > **Extensions** > **Installed** > **Add**.
2. Type **Java**, select `build/libs/burp-mcp-me262-0.9.0.jar`.
3. The **Output** tab shows: `Burp-MCP-Me262 ready -> http://127.0.0.1:9262/`.
4. A **Me262** tab appears in Burp showing the endpoint and the loaded tools.

Override host/port by launching Burp with `-Dme262.port=9262 -Dme262.host=127.0.0.1`.

## Connect Claude Code

```bash
claude mcp add --transport sse me262 http://127.0.0.1:9262/ --scope project
```

Approve it (`/mcp` or restart), then the `mcp__me262__*` tools appear.

## Tools (v0.9.0)

| tool | what it does |
|------|--------------|
| `send_http_request` | send a URL or raw request through Burp's HTTP stack |
| `get_proxy_history` | read recent Proxy history, with substring filter |
| `start_active_scan` | launch a Pro active audit; returns a scan id — **Pro** |
| `start_crawl` | crawl from a seed URL; returns a scan id — **Pro** |
| `scan_status` | progress of scans started via Me262 |
| `get_scanner_issues` | list audit issues (per scan or whole site map) — **Pro** |
| `generate_report` | write an HTML/XML Scanner report to a file — **Pro** |
| `fuzz` | sniper/clusterbomb/pitchfork fuzzing through Burp (Intruder replacement) |
| `scope_check` | is a URL in Burp's Target scope? |
| `scope_add` | add a URL/prefix to Burp's Target scope |
| `sitemap_query` | list site map entries, URL-filtered |
| `generate_collaborator_payload` | mint an OOB Collaborator payload — **Pro** |
| `get_collaborator_interactions` | poll Collaborator DNS/HTTP/SMTP hits — **Pro** |
| `scope_remove` | remove a URL/prefix from Burp's Target scope |
| `set_intercept` | turn Burp Proxy intercept on/off |
| `import_bcheck` | import a BCheck so it runs in active scans — **Pro** |
| `send_to_repeater` | hand a request to Repeater |
| `send_to_intruder` | place a request in Intruder |
| `send_to_organizer` | store a request in Organizer |
| `transform` | url/base64/html encode & decode |
| `random_string` | random alphanumeric string |
| `set_task_engine` | pause/resume Burp's task engine |
| `export_burp_config` | export project/user options as JSON |
| `import_burp_config` | import options — gated by `-Dme262.allowConfigEdits` |
| `get_websocket_history` | read Proxy WebSocket messages |
| `send_to_comparer` | diff two strings in Burp Comparer |

See [ROADMAP.md](ROADMAP.md) for what's next and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
for how it fits together.

## Security

- Binds to `127.0.0.1` only; rejects non-loopback `Origin` (DNS-rebinding defence).
- Optional bearer token: start Burp with `-Dme262.token=SECRET`, then
  `claude mcp add --transport sse me262 http://127.0.0.1:9262/ --header "Authorization: Bearer SECRET"`.
- **Config edits:** `import_burp_config` is disabled unless Burp is started with `-Dme262.allowConfigEdits=true`.
- **ROE guard:** `fuzz`, `start_active_scan` and `start_crawl` refuse targets
  not in Burp's Target scope. Override for lab work with `-Dme262.allowOutOfScope=true`.

## Rules of engagement

This is a testing tool. Only point it at assets you are authorised to test.
Within this workspace, obey each program's `SCOPE.md` / `RULES.md` — do not run
`start_active_scan` against a target whose scope is still `GATED`.

## Licensing

Our own code. Uses the Montoya API (`compileOnly`, PortSwigger's). We did **not**
copy `PortSwigger/mcp-server` (GPL-3.0) source; if any GPL code is later pulled
in, this repo must adopt GPL-3.0. License choice: TBD before any distribution.
