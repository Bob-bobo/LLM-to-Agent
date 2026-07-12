package com.hermes.agent.core.agent.tool

import com.hermes.agent.core.agent.ToolResult
import com.hermes.agent.core.llm.ToolCall
import com.hermes.agent.core.common.util.HermesJson
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes tool calls from the LLM by dispatching to the appropriate AgentTool.
 */
@Singleton
class ToolExecutor @Inject constructor(
    private val toolRegistry: ToolRegistry
) {
    /**
     * Execute a tool call and return the result.
     */
    suspend fun execute(toolCall: ToolCall): ToolResult {
        val tool = toolRegistry.get(toolCall.name)
        if (tool == null) {
            return ToolResult.error("Unknown tool: ${toolCall.name}")
        }

        return try {
            val params = parseArguments(toolCall.arguments)
            Timber.d("Executing tool: %s with params: %s", toolCall.name, params)
            tool.execute(params)
        } catch (e: Exception) {
            Timber.e(e, "Tool execution failed: %s", toolCall.name)
            ToolResult.error("Tool '${toolCall.name}' failed: ${e.message}")
        }
    }

    /**
     * Parse JSON arguments string into a Map.
     */
    private fun parseArguments(argumentsJson: String): Map<String, Any> {
        if (argumentsJson.isBlank() || argumentsJson == "{}") return emptyMap()
        return try {
            val jsonElement = HermesJson.parseToJsonElement(argumentsJson)
            when (jsonElement) {
                is kotlinx.serialization.json.JsonObject -> jsonElement.mapValues { (_, value) ->
                    parseJsonValue(value)
                }
                else -> emptyMap()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse tool arguments: %s", argumentsJson.take(100))
            emptyMap()
        }
    }

    private fun parseJsonValue(value: kotlinx.serialization.json.JsonElement): Any {
        return when (value) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                when {
                    value.isString -> value.content
                    value.content == "true" -> true
                    value.content == "false" -> false
                    else -> value.content.toDoubleOrNull() ?: value.content
                }
            }
            is kotlinx.serialization.json.JsonArray -> value.map { parseJsonValue(it) }
            is kotlinx.serialization.json.JsonObject -> value.mapValues { parseJsonValue(it.value) }
            else -> value.toString()
        }
    }
}
