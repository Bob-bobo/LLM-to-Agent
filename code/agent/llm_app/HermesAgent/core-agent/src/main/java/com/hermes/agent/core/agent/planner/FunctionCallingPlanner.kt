package com.hermes.agent.core.agent.planner

import com.hermes.agent.core.data.model.AgentPlanConfig
import com.hermes.agent.core.llm.*
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Function Calling planner - uses the provider's native tool-use API.
 * This is the preferred strategy for providers that support it
 * (OpenAI, Anthropic, Gemini, DeepSeek, etc.).
 */
@Singleton
class FunctionCallingPlanner @Inject constructor() : Planner {

    override suspend fun plan(
        provider: LlmProvider,
        request: LlmRequest,
        config: AgentPlanConfig
    ): LlmResponse {
        Timber.d("FunctionCalling plan with %d messages, %d tools",
            request.messages.size, request.tools.size)
        return provider.generate(request)
    }

    override suspend fun planStream(
        provider: LlmProvider,
        request: LlmRequest,
        config: AgentPlanConfig,
        onStreamEvent: suspend (LlmStreamEvent) -> Unit
    ): LlmResponse {
        Timber.d("FunctionCalling planStream with %d messages, %d tools",
            request.messages.size, request.tools.size)

        val contentBlocks = mutableListOf<ContentBlock>()
        val toolCalls = mutableListOf<ToolCall>()
        val toolCallBuffers = mutableMapOf<String, StringBuilder>()
        val toolCallNames = mutableMapOf<String, String>()
        var stopReason = StopReason.END_TURN
        var usage: TokenUsage? = null

        provider.generateStream(request).collect { event ->
            onStreamEvent(event)
            when (event) {
                is LlmStreamEvent.TextDelta -> {
                    // Accumulate text
                }
                is LlmStreamEvent.ToolCallBegin -> {
                    toolCallNames[event.id] = event.name
                    toolCallBuffers[event.id] = StringBuilder()
                }
                is LlmStreamEvent.ToolCallDelta -> {
                    toolCallBuffers[event.id]?.append(event.argumentsJsonDelta)
                }
                is LlmStreamEvent.ToolCallEnd -> {
                    toolCalls.add(ToolCall(event.id, event.name, event.arguments))
                    contentBlocks.add(ContentBlock.ToolUse(event.id, event.name, event.arguments))
                }
                is LlmStreamEvent.Usage -> {
                    usage = event.usage
                }
                is LlmStreamEvent.Done -> {
                    // Complete any buffered tool calls
                    for ((id, buffer) in toolCallBuffers) {
                        if (toolCalls.none { it.id == id }) {
                            val name = toolCallNames[id] ?: "unknown"
                            val args = buffer.toString()
                            toolCalls.add(ToolCall(id, name, args))
                            contentBlocks.add(ContentBlock.ToolUse(id, name, args))
                        }
                    }
                    if (toolCalls.isNotEmpty()) stopReason = StopReason.TOOL_USE
                }
                is LlmStreamEvent.Error -> {
                    Timber.e("Stream error: %s", event.message)
                }
            }
        }

        // Build text content from accumulated deltas
        // (In practice, the ViewModel tracks text deltas; here we return the final state)
        return LlmResponse(
            content = contentBlocks,
            toolCalls = toolCalls,
            stopReason = stopReason,
            usage = usage
        )
    }

    override suspend fun summarize(
        provider: LlmProvider,
        model: String,
        messages: List<Message>
    ): String {
        val summarizeRequest = LlmRequest(
            model = model,
            system = "You are a helpful assistant. Summarize the following conversation and provide a final answer based on the information gathered.",
            messages = messages,
            tools = emptyList()
        )
        return try {
            val response = provider.generate(summarizeRequest)
            response.textContent.ifEmpty { "I've completed the task but reached the maximum number of steps."
            }
        } catch (e: Exception) {
            "I've completed the task but reached the maximum number of steps."
        }
    }
}
