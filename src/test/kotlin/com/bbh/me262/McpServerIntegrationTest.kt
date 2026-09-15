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

    private fun post(url: String, body: String) {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        OutputStreamWriter(c.outputStream).use { it.write(body) }
        c.responseCode
        c.disconnect()
    }
}
