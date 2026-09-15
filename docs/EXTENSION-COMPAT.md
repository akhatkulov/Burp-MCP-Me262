# Me262 × Burp Extensions — compatibility study

Can Me262 drive the other extensions installed in Burp? This document answers
that for the BApp Store (~300 extensions) by classifying **how each extension
plugs into Burp**, because that — not the extension's topic — decides whether we
can reach it.

## The one rule

Montoya (Burp's extension API) has **no cross-extension calls and no way to
enumerate or click another extension**. So Me262 never calls extension X
directly. Instead, when Me262 drives a Burp *pipeline*, every extension hooked
into that pipeline runs **automatically, in-band**. We leverage extensions by
routing scans/traffic through the hooks they register — nothing more, nothing
less.

The hooks that matter:

| Extension registers… | Does Me262 reach it? | via |
|----------------------|----------------------|-----|
| Active/passive **scan check** | ✅ automatically | `start_active_scan`, `start_crawl` |
| Global **HTTP handler** (all-tool traffic) | ✅ automatically | `send_http_request`, `fuzz` |
| **Session-handling** action / macro | ⚠️ conditional | only when the session-handling path applies |
| **Proxy-only** handler | ⚠️ conditional | only traffic that flows through the proxy |
| **Intruder** payload processor/generator | ⚠️ conditional | `send_to_intruder`, then run in Burp (not native `fuzz`) |
| **UI** tab / context-menu / viewer / report | ❌ not drivable | — |

## Verdicts

- **AUTO ✅** — participates on its own once Me262 starts the matching scan/traffic. No extra step.
- **CONDITIONAL ⚠️** — reachable only through a specific route (Intruder hand-off, proxy path, or session-handling config).
- **NOT DRIVABLE ❌** — UI/manual only; no API surface for us to invoke.
- **REDUNDANT ↺** — Me262 already does it natively (`transform`, raw requests, `get_proxy_history`), so no need to call the extension.

> Confidence: AUTO/NOT-DRIVABLE calls below are based on the extension's known
> integration type. The definitive check is empirical (see the bottom). Some
> extensions are **mixed** — e.g. a scan check (AUTO) plus a manual button (not
> drivable); those are listed with the caveat.

## AUTO ✅ — run inside `start_active_scan` / `start_crawl`

These register scan checks; a scan you launch through Me262 invokes them and the
issues come back via `get_scanner_issues`:

Active Scan++, Additional Scanner Checks, Additional CSRF Checks, Backslash
Powered Scanner, Backup Finder, CMS Scanner, Command Injection Attacker, CORS\*,
CRLF-Powered Desync Scanner, CSP Auditor, CSRF Scanner, Cypher Injection
Scanner, Error Message Checks, ExifTool Scanner, HTTPoxy Scanner, HeartBleed,
Hunt Scanner, IIS Tilde Enumeration Scanner, J2EEScan, JS Miner, JSON Web Token
Attacker / JWT Scanner, Log4Shell Scanner, NGINX Alias Traversal, NoSQLi
Scanner, OAUTH Scan, OWASP API Security Top 10 Scanner, Padding Oracle Hunter,
PHP Object Injection Check, Prototype Pollution Gadgets Finder, Reflected File
Download Checker, Reflected Parameters, Retire.js, RouteVulScan, Same Origin
Method Execution, Software Vulnerability Scanner, CKEditor Passive Scanner,
Header Guardian, Headers Analyzer.

Also AUTO for the traffic they touch (they register HTTP handlers that see
Me262's `send_http_request`/`fuzz` sends): Add Custom Header, Custom Parameter
Handler, Conditional Match and Replace, Collaborator Everywhere (injects OOB
payloads), PyCript / BurpCrypto / AES Killer / JCryption Handler (encrypt/decrypt
in-band **if** they hook all-tool HTTP), Random IP Address Header.

## CONDITIONAL ⚠️ — reachable only via a specific route

- **Intruder payload processors/generators** — apply only to a Burp *Intruder*
  attack, which our native `fuzz` bypasses. Route via `send_to_intruder`, then
  run in Burp: Hackvertor (Intruder side), Bradamsa, AES Payloads, Adhoc Payload
  Processors, Java Serialized Payloads, Intruder File Payload Generator, Intruder
  Time Payloads, Hashcat Maskprocessor, Bad Character Wordlist Generator.
- **Session-handling actions** — apply only when the request goes through the
  session-handling path (configure the rule; sends may need it enabled): AWS
  Signer / AWS Sigv4, Kerberos Authentication, HTTP Digest Auth, ExtendedMacro,
  Authentication Token Obtain and Replace, Match/Replace Session Action.
- **Proxy-only handlers** — apply to proxied traffic, not our direct sends:
  Bypass WAF (when proxy-based), Non HTTP Proxy, Proxy Action Rules.
- **Hackvertor** — its outgoing tag conversion reaches our sends **only if** its
  global tag-processing HTTP handler is enabled; otherwise use `send_to_repeater`
  / `send_to_intruder`, or our native `transform` for url/base64/html.
- **Piper** — runs external tools on messages via its handlers; reaches our
  traffic where its handler is registered.

## NOT DRIVABLE ❌ — UI/manual only

- **Own attack engines (UI/script):** Turbo Intruder (use native `fuzz`
  instead), Autorize / Authz / AuthMatrix / Auth Analyzer (authz engines with
  UI results we can't read), Multi Session Replay, Repeater Strike.
- **The "guess/probe" buttons** of otherwise-AUTO extensions: Param Miner
  (param guessing), HTTP Request Smuggler (smuggle probe), GadgetProbe — the
  passive/scan-check parts are AUTO, the manual context action is not.
- **Viewers / loggers / notes:** Logger++ (use `get_proxy_history`), Flow,
  Custom Logger, History Explorer, Log Viewer, Notes, Bookmarks, Organizer
  Notes, Progress Tracker.
- **Editors / transformers (UI):** Decoder Improved, CSTC, HackBar, EsPReSSO,
  JWT Editor (editor; its scan check is AUTO), SAML Raider (editor; its scan
  checks are AUTO), InQL / GraphQL Raider (mostly UI), Content Type Converter.
- **Report / external integrations:** Report Generator, Batch Scan Report
  Generator, ReportLM, Document My Pentest, Burp2Slack, Burp2Telegram, Burpcord,
  Burp to Discord, Faraday, Dradis Framework, Nessus Loader, Nuclei Burp
  Integration, Qualys WAS, Pentest-Tools.com Integration, Faction Integration.
- **Context-menu "Copy as X":** Copy as FFUF/Python Requests/PowerShell/Node/Go/
  cURL, Paste cURL to Repeater, Reissue Request Scripter — not callable, and
  redundant (Me262 already holds the raw request).

## REDUNDANT ↺ — Me262 already does it

- Encoders/decoders: `transform` (url/base64/html), plus you can chain your own.
- "Send to Repeater/Intruder/Organizer/Comparer": native tools.
- Proxy history / site map: `get_proxy_history`, `sitemap_query`.
- The official **MCP Server** BApp — Me262 replaces it.

## How to classify any of the ~300

1. Open the extension's BApp Store page → note its **type/behaviour**.
2. Ask: *what does it register?*
   - Scan check → **AUTO** (start a scan).
   - HTTP handler (all tools) → **AUTO** (send/fuzz).
   - Session-handling action / Intruder processor / proxy handler → **CONDITIONAL**.
   - A tab, context-menu item, editor, or external push → **NOT DRIVABLE**.
3. If it does several things, split the verdict per feature (mixed).

## Empirical verification (once Me262 is loaded in Burp)

- **AUTO (scan checks):** load Active Scan++, run `start_active_scan` on a lab
  target, then `get_scanner_issues` — its signature issues should appear.
- **AUTO (HTTP handler):** with an encrypt/tag handler enabled, `send_http_request`
  a marked request and confirm in Logger/Proxy that it was transformed on the way out.
- **CONDITIONAL (Intruder):** `send_to_intruder`, apply the extension's payload
  processor in Burp, run — confirming the native-fuzz gap is only bridged here.
