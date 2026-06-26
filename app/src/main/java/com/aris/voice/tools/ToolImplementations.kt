package com.aris.voice.tools

import com.aris.voice.core.ArisError
import com.aris.voice.core.ArisResult
import java.util.concurrent.ConcurrentHashMap

data class SimpleToolDefinition(
    override val name: String,
    override val description: String,
    override val parameterSchema: Map<String, String>,
    override val isEnabled: Boolean = true
) : IToolDefinition

class ToolRegistryImpl : IToolRegistry {
    private val tools = ConcurrentHashMap<String, Pair<IToolDefinition, IToolExecutor>>()

    override fun registerTool(definition: IToolDefinition, executor: IToolExecutor) {
        tools[definition.name.uppercase()] = Pair(definition, executor)
    }

    override fun unregisterTool(name: String) {
        tools.remove(name.uppercase())
    }

    override fun getRegisteredTools(): List<IToolDefinition> {
        return tools.values.map { it.first }
    }

    override fun getExecutor(name: String): IToolExecutor? {
        return tools[name.uppercase()]?.second
    }
}
