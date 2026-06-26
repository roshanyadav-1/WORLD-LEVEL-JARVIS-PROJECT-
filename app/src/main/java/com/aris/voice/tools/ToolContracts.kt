package com.aris.voice.tools

import com.aris.voice.core.ArisResult

/**
 * Metadata definition specifying the capabilities and requirements of an integrated system tool.
 */
interface IToolDefinition {
    val name: String
    val description: String
    val parameterSchema: Map<String, String> // Name -> Type schema
    val isEnabled: Boolean
}

/**
 * Universal interface to execute a registered platform tool with dynamic parameters.
 */
interface IToolExecutor {
    suspend fun execute(parameters: Map<String, String>): ArisResult<String>
}

/**
 * Central registry tracking available tools and managing execution dispatches.
 */
interface IToolRegistry {
    fun registerTool(definition: IToolDefinition, executor: IToolExecutor)
    fun unregisterTool(name: String)
    fun getRegisteredTools(): List<IToolDefinition>
    fun getExecutor(name: String): IToolExecutor?
}
