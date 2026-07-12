package com.hermes.agent.core.agent.tool

import com.hermes.agent.core.agent.ToolResult
import com.hermes.agent.core.llm.ToolDefinition
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry for managing available agent tools.
 * Tools are registered at app startup and can be enabled/disabled per agent plan.
 */
@Singleton
class ToolRegistry @Inject constructor() {
    private val tools = mutableMapOf<String, AgentTool>()

    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    fun registerAll(tools: List<AgentTool>) {
        tools.forEach { this.tools[it.name] = it }
    }

    fun get(name: String): AgentTool? = tools[name]

    fun all(): List<AgentTool> = tools.values.toList()

    fun toToolDefinitions(): List<ToolDefinition> = tools.values.map { it.definition }

    fun getEnabledTools(enabledNames: Set<String>): List<AgentTool> =
        tools.filterKeys { it in enabledNames }.values.toList()

    fun hasTool(name: String): Boolean = tools.containsKey(name)
}

/**
 * Interface for an agent tool.
 */
interface AgentTool {
    val name: String
    val description: String
    val inputSchema: String  // JSON Schema string
    val definition: ToolDefinition
        get() = ToolDefinition(name, description, inputSchema)

    suspend fun execute(params: Map<String, Any>): ToolResult
}
