package com.bbh.me262.mcp

import burp.api.montoya.logging.Logging
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * MCP SSE transport on a raw [ServerSocket] — deliberately avoiding
 * com.sun.net.httpserver, which is NOT present in Burp's trimmed (jlink) JRE.
 * Only java.base APIs are used, so it loads inside Burp.
 *
 *   GET  /                    -> chunked text/event-stream; emits
 *                                `event: endpoint` / `data: ?sessionId=<uuid>`
 *   POST /?sessionId=<uuid>   -> a JSON-RPC message; the reply is pushed back
 *                                over that session's SSE stream as `event: message`.
 *
 * Loopback-only, with Origin checks and an optional bearer token.
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
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val dispatcher =
        Dispatcher(registry, serverName, serverVersion, protocolVersion) { logging.logToError("[Me262] $it") }
    private val sessions = ConcurrentHashMap<String, Session>()
    /** Streamable-HTTP session ids (Mcp-Session-Id); tracked so DELETE can end them. */
    private val streamSessions = ConcurrentHashMap.newKeySet<String>()
    private val pool = Executors.newCachedThreadPool()

    @Volatile private var running = false
    private var server: ServerSocket? = null

    private class Session(val socket: Socket, val out: OutputStream) {
        val lock = Any()
        @Volatile var open = true
    }

    fun start() {
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(InetAddress.getByName(host), port))
        server = ss
        running = true
        pool.submit { acceptLoop(ss) }
        logging.logToOutput("[Me262] MCP SSE listening on http://$host:$port/  (${registry.size()} tools)")
    }

    fun stop() {
        running = false
        sessions.values.forEach { it.open = false; runCatching { it.socket.close() } }
        sessions.clear()
        streamSessions.clear()
        runCatching { server?.close() }
        pool.shutdownNow()
        logging.logToOutput("[Me262] MCP server stopped")
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running) {
            val socket = try {
                ss.accept()
            } catch (t: Throwable) {
                if (running) logging.logToError("[Me262] accept failed: ${t.message}")
                break
            }
            pool.submit {
                runCatching { handle(socket) }
                    .onFailure { logging.logToError("[Me262] handler: ${it.message}"); runCatching { socket.close() } }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.tcpNoDelay = true
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        val headerBytes = readHeaderBlock(input) ?: run { socket.close(); return }
        val lines = String(headerBytes, Charsets.ISO_8859_1).split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) { socket.close(); return }
        val requestLine = lines[0].split(" ")
        if (requestLine.size < 2) { socket.close(); return }
        val method = requestLine[0].uppercase()
        val target = requestLine[1]

        val headers = HashMap<String, String>()
        for (l in lines.drop(1)) {
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }

        val origin = headers["origin"]
        if (origin != null && !isLocalOrigin(origin)) { writeSimple(output, 403, "forbidden origin"); socket.close(); return }
        if (authToken != null && headers["authorization"] != "Bearer $authToken") { writeSimple(output, 401, "unauthorized"); socket.close(); return }

        when (method) {
            "GET" -> handleSse(socket, output)
            "POST" -> {
                val len = headers["content-length"]?.toIntOrNull() ?: 0
                val body = String(readN(input, len), Charsets.UTF_8)
                if (target.contains("sessionId=")) {
                    // Legacy HTTP+SSE transport (2024-11-05): ack now, push the
                    // reply over the client's separate SSE stream.
                    writeSimple(output, 202, "")
                    processPost(target, body)
                } else {
                    // Streamable HTTP transport (2025-06-18): reply on this POST.
                    handleStreamablePost(output, body, headers["accept"])
                }
                socket.close()
            }
            "DELETE" -> {
                // Streamable HTTP session termination.
                headers["mcp-session-id"]?.let { streamSessions.remove(it) }
                writeSimple(output, 200, "")
                socket.close()
            }
            else -> { writeSimple(output, 405, "method not allowed"); socket.close() }
        }
    }

    /**
     * Streamable HTTP: a single POST carries one JSON-RPC message and the reply
     * comes straight back on the same connection — as `application/json`, or as a
     * one-shot `text/event-stream` when the client accepts only that.
     */
    private fun handleStreamablePost(output: OutputStream, body: String, accept: String?) {
        val req = runCatching { json.decodeFromString<JsonRpcRequest>(body) }
            .getOrElse {
                logging.logToError("[Me262] malformed JSON-RPC (streamable): ${it.message}")
                writeSimple(output, 400, "malformed JSON-RPC")
                return
            }
        val response = dispatcher.dispatch(req)
        if (response == null) {
            // Notification — nothing to return.
            writeSimple(output, 202, "")
            return
        }
        val extraHeaders = if (req.method == "initialize") {
            val sid = UUID.randomUUID().toString()
            streamSessions.add(sid)
            "Mcp-Session-Id: $sid\r\n"
        } else ""
        val payload = json.encodeToString(JsonRpcResponse.serializer(), response)
        val sseOnly = accept != null && accept.contains("text/event-stream") && !accept.contains("application/json")
        if (sseOnly) writeSseOnce(output, payload, extraHeaders) else writeJson(output, payload, extraHeaders)
    }

    private fun writeJson(out: OutputStream, jsonBody: String, extraHeaders: String) {
        val bytes = jsonBody.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder("HTTP/1.1 200 OK\r\n")
            .append("Content-Type: application/json\r\n")
            .append(extraHeaders)
            .append(SECURITY_HEADERS)
            .append("Content-Length: ").append(bytes.size).append("\r\n")
            .append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    /** One JSON-RPC response framed as a single SSE event, then EOF (Connection: close). */
    private fun writeSseOnce(out: OutputStream, jsonBody: String, extraHeaders: String) {
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/event-stream\r\n")
            append("Cache-Control: no-store\r\n")
            append(extraHeaders)
            append(SECURITY_HEADERS)
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(Charsets.ISO_8859_1))
        val sb = StringBuilder("event: message\n")
        for (line in jsonBody.split("\n")) sb.append("data: ").append(line).append('\n')
        sb.append('\n')
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun handleSse(socket: Socket, output: OutputStream) {
        val sid = UUID.randomUUID().toString()
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/event-stream\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: keep-alive\r\n")
            append("Transfer-Encoding: chunked\r\n")
            append("X-Accel-Buffering: no\r\n")
            append(SECURITY_HEADERS)
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.flush()

        val session = Session(socket, output)
        sessions[sid] = session
        try {
            writeEvent(session, "endpoint", "?sessionId=$sid")
            while (session.open && running && !socket.isClosed) {
                Thread.sleep(15_000)
                if (!session.open) break
                writeChunk(session, ": keepalive\n\n")
            }
        } catch (_: Throwable) {
        } finally {
            session.open = false
            sessions.remove(sid)
            runCatching { socket.close() }
        }
    }

    private fun writeEvent(session: Session, event: String, data: String) {
        val sb = StringBuilder("event: ").append(event).append('\n')
        for (line in data.split("\n")) sb.append("data: ").append(line).append('\n')
        sb.append('\n')
        writeChunk(session, sb.toString())
    }

    private fun writeChunk(session: Session, payload: String) {
        synchronized(session.lock) {
            if (!session.open) return
            val bytes = payload.toByteArray(Charsets.UTF_8)
            session.out.write((Integer.toHexString(bytes.size) + "\r\n").toByteArray(Charsets.US_ASCII))
            session.out.write(bytes)
            session.out.write("\r\n".toByteArray(Charsets.US_ASCII))
            session.out.flush()
        }
    }

    private fun processPost(target: String, body: String) {
        val sid = target.substringAfter("sessionId=", "").substringBefore("&").ifBlank { null }
        if (sid == null) { logging.logToError("[Me262] POST without sessionId"); return }
        val session = sessions[sid] ?: run { logging.logToError("[Me262] POST for unknown session"); return }
        val req = runCatching { json.decodeFromString<JsonRpcRequest>(body) }
            .getOrElse { logging.logToError("[Me262] malformed JSON-RPC: ${it.message}"); return }
        val response = dispatcher.dispatch(req) ?: return
        writeEvent(session, "message", json.encodeToString(JsonRpcResponse.serializer(), response))
    }

    private fun writeSimple(out: OutputStream, code: Int, body: String) {
        val reason = when (code) {
            202 -> "Accepted"; 400 -> "Bad Request"; 401 -> "Unauthorized"
            403 -> "Forbidden"; 405 -> "Method Not Allowed"; else -> "OK"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder("HTTP/1.1 $code $reason\r\n")
            .append(SECURITY_HEADERS)
            .append("Content-Length: ").append(bytes.size).append("\r\n")
            .append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        if (bytes.isNotEmpty()) out.write(bytes)
        out.flush()
    }

    private fun isLocalOrigin(origin: String): Boolean =
        origin.startsWith("http://127.0.0.1") || origin.startsWith("http://localhost") || origin.startsWith("http://[::1]")

    /** Read bytes up to and including the CRLFCRLF that ends the header block. */
    private fun readHeaderBlock(input: InputStream): ByteArray? {
        val buf = ByteArrayOutputStream()
        var state = 0
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toByteArray()
            buf.write(b)
            state = when (state) {
                0 -> if (b == CR) 1 else 0
                1 -> if (b == LF) 2 else if (b == CR) 1 else 0
                2 -> if (b == CR) 3 else 0
                3 -> if (b == LF) 4 else if (b == CR) 1 else 0
                else -> 0
            }
            if (state == 4) return buf.toByteArray()
            if (buf.size() > 65_536) return buf.toByteArray()
        }
    }

    private fun readN(input: InputStream, n: Int): ByteArray {
        if (n <= 0) return ByteArray(0)
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(out, off, n - off)
            if (r < 0) break
            off += r
        }
        return if (off == n) out else out.copyOf(off)
    }

    companion object {
        private const val CR = '\r'.code
        private const val LF = '\n'.code
        private const val SECURITY_HEADERS =
            "X-Frame-Options: DENY\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Referrer-Policy: same-origin\r\n" +
            "Content-Security-Policy: default-src 'none'\r\n"
    }
}
