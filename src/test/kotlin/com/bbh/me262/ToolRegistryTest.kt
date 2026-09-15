package com.bbh.me262

import com.bbh.me262.mcp.Tool
import com.bbh.me262.mcp.ToolRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private class FakeTool(override val name: String) : Tool {
    override val description = "fake"
    override val inputSchema = buildJsonObject {}
    override fun execute(arguments: JsonObject) = "ok:$name"
}

class ToolRegistryTest {
    @Test fun registersAndLists() {
        val r = ToolRegistry().register(FakeTool("a")).register(FakeTool("b"))
        assertEquals(2, r.size())
        assertEquals("ok:a", r.get("a")?.execute(buildJsonObject {}))
        assertNull(r.get("missing"))
    }

    @Test fun rejectsDuplicates() {
        val r = ToolRegistry().register(FakeTool("dup"))
        assertFailsWith<IllegalArgumentException> { r.register(FakeTool("dup")) }
    }
}
