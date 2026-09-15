package com.bbh.me262.mcp

import burp.api.montoya.logging.Logging
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Minimal, dependency-light MCP server speaking the SSE transport that Claude
 * Code / other MCP clients expect:
 *   GET  /                      -> text/event-stream; emits `event: endpoint`
 *                                  with `data: ?sessionId=<uuid>`
 *   POST /?sessionId=<uuid>     -> a JSON-RPC message; reply is pushed back over
 *                                  that session's SSE stream as `event: message`.
 *
 * Bound to loopback only, with Origin checks, to resist DNS-rebinding.
 */
class McpServer(
    private val host: String,
    private val port: Int,
    private val registry: ToolRegistry,
    private val logging: Logging,
    private val serverName: String = "burp-mcp-me262",
    private val serverVersion: String = "0.1.0",
    private val protocolVersion: String = "2024-11-05",
    private val authToken: String? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val sessions = ConcurrentHashMap<String, Session>()
    private val pool = Executors.newCachedThreadPool()
    private var http: HttpServer? = null

    private class Session(val out: OutputStream) {
        val lock = Any()
        @Volatile var open = true
    }

    fun start() {
        val server = HttpServer.create(InetSocketAddress(host, port), 0)
        server.executor = pool
        server.createContext("/") { ex -> handle(ex) }
        server.start()
        http = server
        logging.logToOutput("[Me262] MCP SSE listening on http://$host:$port/  (${registry.size()} tools)")
    }

    fun stop() {
        sessions.values.forEach { it.open = false }
        sessions.clear()
        http?.stop(0)
        pool.shutdownNow()
        logging.logToOutput("[Me262] MCP server stopped")
    }

    private fun handle(ex: HttpExchange) {
        try {
            val origin = ex.requestHeaders.getFirst("Origin")
            if (origin != null && !isLocalOrigin(origin)) {
                respond(ex, 403, "forbidden origin")
                return
            }
            if (authToken != null && !isAuthorized(ex)) {
                respond(ex, 401, "unauthorized")
                return
            }
            when (ex.requestMethod.uppercase()) {
                "GET" -> handleSse(ex)
                "POST" -> handlePost(ex)
                else -> respond(ex, 405, "method not allowed")
            }
        } catch (t: Throwable) {
            logging.logToError("[Me262] handler error: ${t.message}")
            runCatching { respond(ex, 500, "internal error") }
        }
    }

    private fun isAuthorized(ex: HttpExchange): Boolean =
        ex.requestHeaders.getFirst("Authorization") == "Bearer $authToken"

    private fun isLocalOrigin(origin: String): Boolean =
        origin.startsWith("http://127.0.0.1") ||
        origin.startsWith("http://localhost") ||
        origin.startsWith("http://[::1]")

    private fun securityHeaders(ex: HttpExchange) {
        val h = ex.responseHeaders
        h.add("X-Frame-Options", "DENY")
        h.add("X-Content-Type-Options", "nosniff")
        h.add("Referrer-Policy", "same-origin")
        h.add("Content-Security-Policy", "default-src 'none'")
    }

    private fun handleSse(ex: HttpExchange) {
        val sid = UUID.randomUUID().toString()
        securityHeaders(ex)
        ex.responseHeaders.apply {
            add("Content-Type", "text/event-stream")
            add("Cache-Control", "no-store")
            add("Connection", "keep-alive")
            add("X-Accel-Buffering", "no")
        }
        ex.sendResponseHeaders(200, 0)
        val session = Session(ex.responseBody)
        sessions[sid] = session
        try {
            writeEvent(session, "endpoint", "?sessionId=$sid")
            // Hold the stream open with periodic comments so proxies do not buffer/close.
            while (session.open) {
                Thread.sleep(15_000)
                if (!session.open) break
                synchronized(session.lock) {
                    session.out.write(": keepalive\n\n".toByteArray())
                    session.out.flush()
                }
            }
        } catch (_: Throwable) {
            // client went away
        } finally {
            session.open = false
            sessions.remove(sid)
            runCatching { session.out.close() }
        }
    }

    private fun writeEvent(session: Session, event: String, data: String) {
        synchronized(session.lock) {
            if (!session.open) return
            val sb = StringBuilder().append("event: ").append(event).append('\n')
            for (line in data.split("\n")) sb.append("data: ").append(line).append('\n')
            sb.append('\n')
            session.out.write(sb.toString().toByteArray())
            session.out.flush()
        }
    }

    private fun handlePost(ex: HttpExchange) {
        val query = ex.requestURI.query ?: ""
        val sid = query.split("&")
            .firstOrNull { it.startsWith("sessionId=") }
            ?.substringAfter("=")
        val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
        respond(ex, 202, "") // ack immediately; the JSON-RPC reply goes over SSE

        if (sid == null) {
            logging.logToError("[Me262] POST without sessionId")
            return
        }
        val session = sessions[sid]
        if (session == null) {
            logging.logToError("[Me262] POST for unknown session $sid")
            return
        }
        val req = runCatching { json.decodeFromString<JsonRpcRequest>(body) }.getOrElse {
            logging.logToError("[Me262] malformed JSON-RPC: ${it.message}")
            return
        }
        val response = dispatch(req) ?: return // notifications produce no reply
        writeEvent(session, "message", json.encodeToString(JsonRpcResponse.serializer(), response))
    }

    private fun dispatch(req: JsonRpcRequest): JsonRpcResponse? = when (req.method) {
        "initialize" -> ok(req.id, buildJsonObject {
            put("protocolVersion", protocolVersion)
            putJsonObject("capabilities") { putJsonObject("tools") {} }
            putJsonObject("serverInfo") {
                put("name", serverName)
                put("version", serverVersion)
            }
        })
        "notifications/initialized" -> null
        "ping" -> ok(req.id, buildJsonObject {})
        "tools/list" -> ok(req.id, buildJsonObject {
            putJsonArray("tools") {
                for (t in registry.list()) add(buildJsonObject {
                    put("name", t.name)
                    put("description", t.description)
                    put("inputSchema", t.inputSchema)
                })
            }
        })
        "tools/call" -> handleToolCall(req)
        else -> err(req.id, -32601, "Method not found: ${req.method}")
    }

    private fun handleToolCall(req: JsonRpcRequest): JsonRpcResponse {
        val params = req.params as? JsonObject ?: return err(req.id, -32602, "invalid params")
        val name = params["name"]?.jsonPrimitive?.contentOrNull
            ?: return err(req.id, -32602, "missing tool name")
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val tool = registry.get(name) ?: return err(req.id, -32602, "unknown tool: $name")
        return runCatching { tool.execute(args) }.fold(
            onSuccess = { text -> toolResult(req.id, text, isError = false) },
            onFailure = { t ->
                logging.logToError("[Me262] tool '$name' failed: ${t.message}")
                toolResult(req.id, "ERROR: ${t.message}", isError = true)
            },
        )
    }

    private fun toolResult(id: JsonElement?, text: String, isError: Boolean) = ok(id, buildJsonObject {
        putJsonArray("content") {
            add(buildJsonObject {
                put("type", "text")
                put("text", text)
            })
        }
        put("isError", isError)
    })

    private fun ok(id: JsonElement?, result: JsonElement) = JsonRpcResponse(id = id, result = result)
    private fun err(id: JsonElement?, code: Int, message: String) =
        JsonRpcResponse(id = id, error = JsonRpcError(code, message))

    private fun respond(ex: HttpExchange, code: Int, body: String) {
        securityHeaders(ex)
        val bytes = body.toByteArray()
        if (bytes.isEmpty()) {
            ex.sendResponseHeaders(code, -1)
        } else {
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        ex.close()
    }
}
