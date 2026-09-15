package com.bbh.me262.mcp

/** Ordered registry of tools, keyed by name. */
class ToolRegistry {
    private val tools = LinkedHashMap<String, Tool>()

    fun register(tool: Tool): ToolRegistry {
        require(tools.put(tool.name, tool) == null) { "duplicate tool name: ${tool.name}" }
        return this
    }

    fun list(): List<Tool> = tools.values.toList()
    fun get(name: String): Tool? = tools[name]
    fun size(): Int = tools.size
}
