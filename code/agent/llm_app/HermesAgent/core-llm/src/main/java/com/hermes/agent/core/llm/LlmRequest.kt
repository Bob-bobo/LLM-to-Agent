package com.hermes.agent.core.llm

import kotlinx.serialization.Serializable

/**
 * Request to an LLM provider.
 */
@Serializable
data class LlmRequest(
    val model: String,
    val system: String? = null,
    val messages: List<Message> = emptyList(),
    val tools: List<ToolDefinition> = emptyList(),
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val topP: Float = 1.0f,
    val stopSequences: List<String>? = null,
    val stream: Boolean = false
)

/**
 * A message in the LLM conversation.
 */
@Serializable
data class Message(
    val role: String,               // "user", "assistant", "system"
    val content: List<ContentBlock> = emptyList()
) {
    constructor(role: String, text: String) : this(role, listOf(ContentBlock.Text(text)))

    val textContent: String
        get() = content.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
}

/**
 * Content block in a message.
 */
@Serializable
sealed class ContentBlock {
    @Serializable
    data class Text(val text: String) : ContentBlock()

    @Serializable
    data class Image(
        val url: String? = null,     // URL to image
        val base64: String? = null,  // Base64 encoded image
        val mimeType: String = "image/png"
    ) : ContentBlock()

    @Serializable
    data class ToolUse(
        val id: String,
        val name: String,
        val input: String    // JSON string
    ) : ContentBlock()

    @Serializable
    data class ToolResult(
        val toolUseId: String,
        val name: String,
        val output: String,
        val isError: Boolean = false
    ) : ContentBlock()
}

/**
 * Tool definition for function calling.
 */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: String   // JSON Schema string
)
