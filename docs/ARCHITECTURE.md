# Architecture

```
Claude Code (MCP client)
        |  JSON-RPC 2.0 over MCP  (http://127.0.0.1:9262/)
        v
+-------------------------------------------------------+
|  Me262Extension  (BurpExtension, runs in Burp's JVM)  |
|                                                       |
|   McpServer  (raw java.net.ServerSocket)              |
|     POST /                -> Streamable HTTP: reply    |
|                              on the same request       |
|     GET  /  + POST /?sessionId=...                     |
|                           -> legacy HTTP+SSE transport |
|                                                       |
|   ToolRegistry -> [ Tool, Tool, ... ]                  |
|        each Tool.execute(args) calls the Montoya API   |
+------------------------|------------------------------+
                         v
                 Montoya API (api.http, api.scanner,
                 api.proxy, api.siteMap, api.collaborator...)
                         v
                 Burp Suite Pro engine + installed extensions
                 (scan checks, BChecks, handlers run in-band)
```

## Transport
We implement the MCP transport directly on a raw `java.net.ServerSocket`
(chunked `Transfer-Encoding`) — no Ktor, no MCP SDK, and deliberately **not**
`com.sun.net.httpserver`, which is absent from Burp's trimmed (jlink) JRE and
threw `ClassNotFoundException` on load (fixed in v1.0.1). Only `java.base` APIs
are used, so it loads inside Burp. The only bundled runtime dependency is
`kotlinx-serialization-json`.

The single endpoint (`/`) is **dual-stack**, so one server serves both
generations of MCP client:

- **Streamable HTTP** (`2025-06-18`, current): client `POST`s a JSON-RPC message
  and the reply comes straight back on the same request — `application/json`, or
  a one-shot `text/event-stream` when the client accepts only that. `initialize`
  returns an `Mcp-Session-Id` header; `DELETE /` ends a session.
- **Legacy HTTP+SSE** (`2024-11-05`): `GET /` opens an SSE stream that emits
  `event: endpoint` with a `?sessionId=…` POST-back URL; the client then `POST`s
  to `/?sessionId=…` (server returns `202`) and the reply is pushed over that SSE
  stream. Requests carrying a `sessionId` query take this path; requests without
  one take the Streamable path.

`initialize` negotiates the protocol version: the server echoes the client's
requested version when it is one of `2024-11-05` / `2025-03-26` / `2025-06-18`,
else falls back to its default.

> Not yet done (see [SPEC-maturity.md](SPEC-maturity.md) Group 3): server-side
> `notifications/progress` (deferred — current scan tools are non-blocking and
> polled via `scan_status`), and `resources` / `prompts` capabilities.

Flow (matches what MCP clients expect):
1. Client opens `GET /`. Server replies `text/event-stream` and sends
   `event: endpoint` / `data: ?sessionId=<uuid>`.
2. Client `POST`s JSON-RPC to `/?sessionId=<uuid>`; server returns `202` and
   pushes the JSON-RPC response back on that session's SSE stream as
   `event: message`.
3. `initialize` -> `tools/list` -> `tools/call` are the methods handled.

## Adding a tool
1. Create `tools/YourTool.kt` implementing `com.bbh.me262.mcp.Tool`
   (name, description, JSON-schema `inputSchema`, `execute`).
2. Register it in `Me262Extension.initialize` on the `ToolRegistry`.
3. Rebuild, reload the extension in Burp. That's it.

## Security
- Binds to `127.0.0.1` only.
- Rejects non-loopback `Origin` headers (DNS-rebinding defence).
- Optional bearer token on the endpoint (`-Dme262.token=SECRET`); when set, every
  request must send `Authorization: Bearer SECRET`.
- Emits hardening response headers (`X-Frame-Options`, `X-Content-Type-Options`,
  `Content-Security-Policy: default-src 'none'`, `Referrer-Policy`).
- Still loopback-first — do not expose the port off-host.
