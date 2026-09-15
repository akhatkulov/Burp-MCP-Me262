# Connecting Me262 to MCP clients

Me262 exposes the **MCP SSE transport** at:

```
http://127.0.0.1:9262/          <-- note: root path "/", NOT "/sse"
```

- Loopback only. Optional bearer token: start Burp with `-Dme262.token=SECRET`,
  then send header `Authorization: Bearer SECRET`.
- Transport is classic **SSE**. Clients that only speak stdio use the
  `mcp-remote` bridge (shown below). Clients that speak SSE/HTTP connect by URL.

Load the extension in Burp first (Extensions > Add > Java > the jar). When the
Output tab shows `ready -> http://127.0.0.1:9262/`, connect a client:

## Claude Code (CLI) — native SSE

```bash
claude mcp add --transport sse me262 http://127.0.0.1:9262/
# with a token:
claude mcp add --transport sse me262 http://127.0.0.1:9262/ --header "Authorization: Bearer SECRET"
# scope: add --scope user | project | local  (project writes ./.mcp.json and needs approval)
```

Or edit the config directly (`~/.claude.json`, or project `.mcp.json`):

```json
{ "mcpServers": { "me262": { "type": "sse", "url": "http://127.0.0.1:9262/" } } }
```

Approve it (`/mcp` or restart), then the `mcp__me262__*` tools appear.

## Cursor — native SSE (v0.48+)

`~/.cursor/mcp.json` (global) or `.cursor/mcp.json` (project):

```json
{ "mcpServers": { "me262": { "url": "http://127.0.0.1:9262/" } } }
```

## Cline (VS Code) — native SSE

`cline_mcp_settings.json` (Cline pane > MCP Servers > Configure):

```json
{ "mcpServers": { "me262": { "url": "http://127.0.0.1:9262/", "transportType": "sse" } } }
```

## Windsurf — native SSE

`~/.codeium/windsurf/mcp_config.json`:

```json
{ "mcpServers": { "me262": { "serverUrl": "http://127.0.0.1:9262/" } } }
```

## OpenCode — remote server

`opencode.json` (project root overrides global `~/.config/opencode/opencode.json`):

```json
{ "mcp": { "me262": { "type": "remote", "url": "http://127.0.0.1:9262/", "enabled": true } } }
```

Note: OpenCode prefers HTTP-streamable transport and has reported quirks with
SSE. If it will not connect, use the `mcp-remote` bridge below as a `type:"local"`
command server instead.

## Claude Desktop & any stdio-only client — via `mcp-remote`

Claude Desktop (and older clients) speak stdio only, so bridge with `mcp-remote`.
`claude_desktop_config.json` (macOS: `~/Library/Application Support/Claude/`,
Windows: `%APPDATA%\Claude\`):

```json
{
  "mcpServers": {
    "me262": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://127.0.0.1:9262/"]
    }
  }
}
```

With a token, append to `args`: `"--header", "Authorization: Bearer SECRET"`.
Restart the app; a successful connection shows the tool/hammer indicator.

The same `command`/`args` block works for OpenCode (`type: "local"`), or any
client that only supports local/stdio MCP servers.

## Verify

Any client: after connecting, list tools — you should see 26, e.g.
`send_http_request`, `fuzz`, `start_active_scan`. A quick no-target check is
`random_string` (length 8) or `transform` (base64_encode).

Raw check without a client (proves the server is up):

```bash
curl -sN http://127.0.0.1:9262/    # emits: event: endpoint / data: ?sessionId=...
```
