package com.hermes.agent.core.llm

import kotlinx.serialization.Serializable

/**
 * Response from an LLM provider.
 */
@Serializable
data class LlmResponse(
    val content: List<ContentBlock> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val stopReason: StopReason = StopReason.END_TURN,
    val usage: TokenUsage? = null
) {
    val textContent: String
        get() = content.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
}

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String   // JSON string of arguments
)

@Serializable
enum class StopReason {
    END_TURN,    // Model finished naturally
    TOOL_USE,    // Model wants to call a tool
    MAX_TOKENS,  // Hit max token limit
    STOP_SEQUENCE // Hit a stop sequence
}

@Serializable
data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0
)
