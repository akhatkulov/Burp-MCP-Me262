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
                val body = readN(input, len)
                writeSimple(output, 202, "")
                processPost(target, String(body, Charsets.UTF_8))
                socket.close()
            }
            else -> { writeSimple(output, 405, "method not allowed"); socket.close() }
        }
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
