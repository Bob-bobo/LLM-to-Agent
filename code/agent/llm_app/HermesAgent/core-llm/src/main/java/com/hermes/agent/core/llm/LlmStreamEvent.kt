package com.hermes.agent.core.llm

/**
 * Events emitted during streaming LLM generation.
 */
sealed class LlmStreamEvent {
    /** A chunk of text content */
    data class TextDelta(val text: String) : LlmStreamEvent()

    /** Beginning of a tool call */
    data class ToolCallBegin(val id: String, val name: String) : LlmStreamEvent()

    /** Partial arguments for a tool call (JSON fragment) */
    data class ToolCallDelta(val id: String, val argumentsJsonDelta: String) : LlmStreamEvent()

    /** End of a tool call (all arguments received) */
    data class ToolCallEnd(val id: String, val name: String, val arguments: String) : LlmStreamEvent()

    /** Usage information received */
    data class Usage(val usage: TokenUsage) : LlmStreamEvent()

    /** Stream completed successfully */
    data object Done : LlmStreamEvent()

    /** An error occurred during streaming */
    data class Error(val message: String, val throwable: Throwable? = null) : LlmStreamEvent()
}
