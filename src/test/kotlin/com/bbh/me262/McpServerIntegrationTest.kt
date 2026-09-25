package com.bbh.me262

import burp.api.montoya.logging.Logging
import com.bbh.me262.mcp.McpServer
import com.bbh.me262.mcp.Tool
import com.bbh.me262.mcp.ToolRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Starts the real SSE transport (no Burp) and drives a full MCP handshake. */
class McpServerIntegrationTest {

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun noopLogging(): Logging =
        Proxy.newProxyInstance(Logging::class.java.classLoader, arrayOf(Logging::class.java)) { _, _, _ -> null } as Logging

    private fun echoRegistry() = ToolRegistry().register(object : Tool {
        override val name = "echo"
        override val description = "echo"
        override val inputSchema = buildJsonObject {}
        override fun execute(arguments: JsonObject) = arguments["x"]?.jsonPrimitive?.content ?: "none"
    })

    @Test fun sseHandshakeInitializeAndToolCall() {
        val port = freePort()
        val server = McpServer("127.0.0.1", port, echoRegistry(), noopLogging(), serverVersion = "itest")
        server.start()
        try {
            val events = LinkedBlockingQueue<Pair<String, String>>()
            val sse = URI("http://127.0.0.1:$port/").toURL().openConnection() as HttpURLConnection
            sse.setRequestProperty("Accept", "text/event-stream")
            sse.connectTimeout = 3000
            sse.readTimeout = 9000
            val reader = BufferedReader(InputStreamReader(sse.inputStream))
            thread(isDaemon = true) {
                var ev: String? = null
                val data = StringBuilder()
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        when {
                            line.startsWith("event:") -> ev = line.substring(6).trim()
                            line.startsWith("data:") -> {
                                if (data.isNotEmpty()) data.append("\n")
                                data.append(line.substring(5).trim())
                            }
                            line.isEmpty() && ev != null -> {
                                events.put(ev!! to data.toString()); ev = null; data.setLength(0)
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            val endpoint = events.poll(6, TimeUnit.SECONDS) ?: fail("no endpoint event")
            assertEquals("endpoint", endpoint.first)
            val postUrl = "http://127.0.0.1:$port/${endpoint.second}"

            post(postUrl, """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")
            val initReply = events.poll(6, TimeUnit.SECONDS) ?: fail("no initialize reply")
            assertEquals("message", initReply.first)
            val init = Json.parseToJsonElement(initReply.second).jsonObject
            assertEquals(
                "itest",
                init["result"]!!.jsonObject["serverInfo"]!!.jsonObject["version"]!!.jsonPrimitive.content,
            )

            post(postUrl, """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo","arguments":{"x":"pong"}}}""")
            val toolReply = events.poll(6, TimeUnit.SECONDS) ?: fail("no tool reply")
            val res = Json.parseToJsonElement(toolReply.second).jsonObject["result"]!!.jsonObject
            assertEquals("pong", res["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
            reader.close()
        } finally {
            server.stop()
        }
    }

    @Test fun streamableHttpInitializeAndToolCall() {
        val port = freePort()
        val server = McpServer("127.0.0.1", port, echoRegistry(), noopLogging(), serverVersion = "itest")
        server.start()
        try {
            val url = "http://127.0.0.1:$port/"
            // initialize with a modern protocol version -> echoed back, session id issued.
            val init = postJson(url, """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}""")
            assertEquals(200, init.code)
            assertTrue(init.contentType?.startsWith("application/json") == true, "content-type: ${init.contentType}")
            val initObj = Json.parseToJsonElement(init.body).jsonObject["result"]!!.jsonObject
            assertEquals("2025-06-18", initObj["protocolVersion"]!!.jsonPrimitive.content)
            assertEquals("itest", initObj["serverInfo"]!!.jsonObject["version"]!!.jsonPrimitive.content)
            assertNotNull(init.sessionId, "Mcp-Session-Id header expected on initialize")

            // tools/call returns the result directly on the POST (no separate SSE).
            val call = postJson(url, """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo","arguments":{"x":"pong"}}}""")
            assertEquals(200, call.code)
            val res = Json.parseToJsonElement(call.body).jsonObject["result"]!!.jsonObject
            assertEquals("pong", res["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        } finally {
            server.stop()
        }
    }

    @Test fun streamableHttpNotificationReturns202() {
        val port = freePort()
        val server = McpServer("127.0.0.1", port, echoRegistry(), noopLogging(), serverVersion = "itest")
        server.start()
        try {
            val r = postJson("http://127.0.0.1:$port/", """{"jsonrpc":"2.0","method":"notifications/initialized"}""")
            assertEquals(202, r.code)
        } finally {
            server.stop()
        }
    }

    private fun post(url: String, body: String) {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        OutputStreamWriter(c.outputStream).use { it.write(body) }
        c.responseCode
        c.disconnect()
    }

    private data class Resp(val code: Int, val body: String, val contentType: String?, val sessionId: String?)

    private fun postJson(url: String, body: String): Resp {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 3000
        c.readTimeout = 6000
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Accept", "application/json, text/event-stream")
        OutputStreamWriter(c.outputStream).use { it.write(body) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        val ct = c.getHeaderField("Content-Type")
        val sid = c.getHeaderField("Mcp-Session-Id")
        c.disconnect()
        return Resp(code, text, ct, sid)
    }
}
