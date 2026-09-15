# Architecture

```
Claude Code (MCP client)
        |  SSE + JSON-RPC 2.0  (http://127.0.0.1:9262/)
        v
+-------------------------------------------------------+
|  Me262Extension  (BurpExtension, runs in Burp's JVM)  |
|                                                       |
|   McpServer  (com.sun.net.httpserver.HttpServer)      |
|     GET  /                -> SSE stream, emits         |
|                              event: endpoint           |
|     POST /?sessionId=...   -> JSON-RPC message,        |
|                              reply pushed over SSE      |
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
We implement the MCP **SSE transport** directly on the JDK's built-in
`HttpServer` — no Ktor, no MCP SDK — to keep the extension small and avoid
classloader conflicts inside Burp. The only bundled runtime dependency is
`kotlinx-serialization-json`.

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
- No auth token yet (roadmap v0.4) — do not expose the port off-host.
