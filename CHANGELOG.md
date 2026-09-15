# Changelog

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
