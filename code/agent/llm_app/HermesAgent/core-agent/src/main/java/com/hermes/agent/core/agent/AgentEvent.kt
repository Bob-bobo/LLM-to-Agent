package com.hermes.agent.core.agent

import com.hermes.agent.core.llm.ToolCall

/**
 * Events emitted by the AgentEngine during execution.
 * The ViewModel collects these to update the UI in real-time.
 */
sealed class AgentEvent {
    /** A new reasoning step has started */
    data class StepStart(val step: Int, val totalSteps: Int) : AgentEvent()

    /** The LLM has produced a response (may contain tool calls) */
    data class LlmResponse(val response: com.hermes.agent.core.llm.LlmResponse) : AgentEvent()

    /** A text delta from streaming LLM output */
    data class TextDelta(val text: String) : AgentEvent()

    /** A tool call is being made */
    data class ToolCallStart(val call: ToolCall) : AgentEvent()

    /** A tool call has completed with a result */
    data class ToolCallComplete(
        val callId: String,
        val toolName: String,
        val result: ToolResult
    ) : AgentEvent()

    /** The agent has produced a final answer */
    data class FinalAnswer(val text: String) : AgentEvent()

    /** An error occurred during agent execution */
    data class Error(val message: String, val throwable: Throwable? = null) : AgentEvent()
}

/**
 * Result of a tool execution.
 */
data class ToolResult(
    val output: String,
    val isError: Boolean = false
) {
    companion object {
        fun success(output: String) = ToolResult(output, false)
        fun error(message: String) = ToolResult(message, true)
    }
}
