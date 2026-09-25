# Burp-MCP-Me262

> Drive **Burp Suite Pro** from any AI agent over MCP — launch active scans, run a
> native fuzzer, mint Collaborator payloads, and generate reports, all guarded by
> Burp's own Target scope.

[![CI](https://github.com/akhatkulov/Burp-MCP-Me262/actions/workflows/ci.yml/badge.svg)](https://github.com/akhatkulov/Burp-MCP-Me262/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/akhatkulov/Burp-MCP-Me262)](https://github.com/akhatkulov/Burp-MCP-Me262/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](build.gradle.kts)
[![Stars](https://img.shields.io/github/stars/akhatkulov/Burp-MCP-Me262?style=social)](https://github.com/akhatkulov/Burp-MCP-Me262/stargazers)

Me262 is a native **Montoya-API** extension that runs an **MCP server inside Burp
Suite Pro**, exposing **28 tools** to Claude Code, Cursor, Cline, Windsurf,
OpenCode, Claude Desktop, and any MCP client. It runs its own SSE transport on a
raw socket (no `com.sun.net.httpserver`, so it loads in Burp's trimmed JRE) and
needs no third-party runtime beyond one JSON library.

## Why Me262

The stock Burp MCP can send requests and read history — but it stops there. Me262
unlocks the Pro engine and adds the discipline you actually need on a live target.

| Capability | Stock Burp MCP | **Me262** |
|---|:---:|:---:|
| Send HTTP/1.1 & /2, read Proxy history | ✅ | ✅ |
| Collaborator payloads + interactions | ✅ | ✅ |
| **Launch active scan / crawl** | ❌ | ✅ |
| **Read scanner issues + generate report** | ❌ | ✅ |
| **Native fuzzer** (sniper/clusterbomb/pitchfork) | ❌ | ✅ |
| **BCheck import**, task-engine, intercept control | ❌ | ✅ |
| Scope / site map / send-to (Repeater/Intruder/Comparer) | partial | ✅ |
| **ROE guard** — refuses out-of-scope targets | ❌ | ✅ |
| Bearer-token auth on the endpoint | ❌ | ✅ |
| Unit + live-transport tests | ❌ | ✅ 13 |

## Quickstart

Requires JDK 17+ (Burp ships its own JRE 21).

```bash
git clone https://github.com/akhatkulov/Burp-MCP-Me262
cd Burp-MCP-Me262
./gradlew shadowJar          # -> build/libs/burp-mcp-me262-<version>.jar
```

1. **Load in Burp**: Extensions > Installed > Add > type **Java** > pick the jar.
   The Output tab prints `ready -> http://127.0.0.1:9262/ (28 tools)`, and a
   **Me262** tab appears.
2. **Connect Claude Code**:
   ```bash
   claude mcp add --transport sse me262 http://127.0.0.1:9262/
   ```
   Approve it (`/mcp`), then the `mcp__me262__*` tools are live.

Other clients (Cursor, Cline, Windsurf, OpenCode, Claude Desktop, `mcp-remote`):
see **[docs/CONNECTING.md](docs/CONNECTING.md)**.

## Tools (28)

| group | tools |
|---|---|
| HTTP | `send_http_request`, `get_proxy_history`, `get_websocket_history` |
| Scanner (Pro) | `start_active_scan`, `passive_scan`, `start_crawl`, `scan_status`, `stop_scan`, `get_scanner_issues`, `generate_report` |
| Fuzzer | `fuzz` (sniper / clusterbomb / pitchfork) |
| Send-to | `send_to_repeater`, `send_to_intruder`, `send_to_organizer`, `send_to_comparer` |
| Scope / map | `scope_check`, `scope_add`, `scope_remove`, `sitemap_query` |
| Collaborator (Pro) | `generate_collaborator_payload`, `get_collaborator_interactions` |
| Control | `import_bcheck`, `set_intercept`, `set_task_engine` |
| Utilities | `transform` (url/base64/base64url/hex/html/jwt_decode), `random_string` |
| Config | `export_burp_config`, `import_burp_config` (gated) |

## Safety

- Binds to `127.0.0.1` only; rejects non-loopback `Origin` (DNS-rebinding defence).
- **ROE guard**: `fuzz`, `start_active_scan`, `start_crawl` refuse targets not in
  Burp's Target scope. Override for lab work with `-Dme262.allowOutOfScope=true`.
- Optional token: start Burp with `-Dme262.token=SECRET`.
- `import_burp_config` is disabled unless `-Dme262.allowConfigEdits=true`.
- This is a testing tool — only point it at systems you are authorised to test.

## How it works

```
MCP client ──SSE/JSON-RPC──> Me262Extension (in Burp's JVM) ──> Montoya API ──> Burp Pro engine + extensions
```

Any installed extension that hooks Burp's pipelines (scan checks like Active
Scan++, global HTTP handlers, BChecks) participates automatically when Me262
starts a scan or sends traffic. See **[docs/EXTENSION-COMPAT.md](docs/EXTENSION-COMPAT.md)**.

## Docs

- [Connecting from any MCP client](docs/CONNECTING.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Which Burp extensions Me262 can drive](docs/EXTENSION-COMPAT.md)
- [Roadmap](ROADMAP.md) · [Changelog](CHANGELOG.md)

## Build & test

```bash
./gradlew test        # 13 tests: combinatorics, registry, JSON-RPC dispatch, live SSE transport
./gradlew shadowJar   # the loadable fat jar
```

## License

MIT — see [LICENSE](LICENSE). Uses the Burp Montoya API `compileOnly`
(PortSwigger's, not redistributed). No code from `PortSwigger/mcp-server`
(GPL-3.0) was used.

## Contributing

Issues and PRs welcome — new tools are one Kotlin file plus one `register(...)`
line (see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)). If Me262 saves you time
on an engagement, a ⭐ helps others find it.
