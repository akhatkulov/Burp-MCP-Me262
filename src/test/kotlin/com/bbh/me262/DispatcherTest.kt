package com.bbh.me262

import com.bbh.me262.mcp.Dispatcher
import com.bbh.me262.mcp.JsonRpcRequest
import com.bbh.me262.mcp.Tool
import com.bbh.me262.mcp.ToolRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class EchoTool(override val name: String = "echo") : Tool {
    override val description = "echo x"
    override val inputSchema = buildJsonObject {}
    override fun execute(arguments: JsonObject) =
        arguments["x"]?.jsonPrimitive?.content ?: "<none>"
}

private class BoomTool(override val name: String = "boom") : Tool {
    override val description = "throws"
    override val inputSchema = buildJsonObject {}
    override fun execute(arguments: JsonObject): String = error("kaboom")
}

class DispatcherTest {
    private fun disp() = Dispatcher(
        ToolRegistry().register(EchoTool()).register(BoomTool()),
        serverName = "burp-mcp-me262", serverVersion = "test", protocolVersion = "2024-11-05",
    )

    private fun req(method: String, params: JsonObject? = null) =
        JsonRpcRequest(id = JsonPrimitive(1), method = method, params = params)

    @Test fun initializeReturnsServerInfo() {
        val r = disp().dispatch(req("initialize"))!!
        val res = r.result!!.jsonObject
        assertEquals("2024-11-05", res["protocolVersion"]!!.jsonPrimitive.content)
        assertEquals("burp-mcp-me262", res["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test fun toolsListReturnsRegistered() {
        val r = disp().dispatch(req("tools/list"))!!
        val names = r.result!!.jsonObject["tools"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(listOf("echo", "boom"), names)
    }

    @Test fun toolsCallExecutes() {
        val params = buildJsonObject {
            put("name", "echo")
            put("arguments", buildJsonObject { put("x", "hi") })
        }
        val r = disp().dispatch(req("tools/call", params))!!
        val res = r.result!!.jsonObject
        assertEquals(false, res["isError"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("hi", res["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test fun toolErrorBecomesIsError() {
        val params = buildJsonObject { put("name", "boom") }
        val r = disp().dispatch(req("tools/call", params))!!
        assertTrue(r.result!!.jsonObject["isError"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun unknownMethodErrors() {
        val r = disp().dispatch(req("nope"))!!
        assertEquals(-32601, r.error!!.code)
    }

    @Test fun notificationReturnsNull() {
        assertNull(disp().dispatch(req("notifications/initialized")))
    }
}
