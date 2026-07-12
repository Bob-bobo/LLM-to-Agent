package com.hermes.agent.core.data.model

import com.hermes.agent.core.common.util.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Domain model for a conversation.
 */
@Serializable
data class Conversation(
    val id: Long = 0,
    val title: String,
    val providerId: String,
    val modelId: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now(),
    val isPinned: Boolean = false,
    val syncVersion: Long = 0
)

/**
 * Domain model for a chat message with multi-content blocks.
 */
@Serializable
data class ChatMessage(
    val id: Long = 0,
    val conversationId: Long,
    val role: MessageRole,
    val content: List<ContentBlock>,
    val tokenCount: Int? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now(),
    val syncVersion: Long = 0
)

@Serializable
enum class MessageRole {
    USER, ASSISTANT, SYSTEM, TOOL
}

/**
 * Content block - supports text, tool use, tool results, and media.
 */
@Serializable
sealed class ContentBlock {
    @Serializable
    data class Text(val text: String) : ContentBlock()

    @Serializable
    data class ToolUse(
        val id: String,
        val name: String,
        val input: String   // JSON string of tool input
    ) : ContentBlock()

    @Serializable
    data class ToolResult(
        val toolUseId: String,
        val name: String,
        val output: String,
        val isError: Boolean = false
    ) : ContentBlock()

    @Serializable
    data class Image(
        val uri: String,        // Local URI or base64
        val mimeType: String,
        val base64: String? = null  // Base64 for API submission
    ) : ContentBlock()

    @Serializable
    data class File(
        val uri: String,
        val name: String,
        val mimeType: String,
        val sizeBytes: Long
    ) : ContentBlock()

    @Serializable
    data class Audio(
        val uri: String,
        val mimeType: String,
        val base64: String? = null
    ) : ContentBlock()

    @Serializable
    data class Video(
        val uri: String,
        val mimeType: String,
        val base64: String? = null
    ) : ContentBlock()
}

/**
 * Agent plan configuration.
 */
@Serializable
data class AgentPlanConfig(
    val id: String = "default",
    val strategy: PlanStrategy = PlanStrategy.FUNCTION_CALLING,
    val maxSteps: Int = 10,
    val enabledTools: Set<String> = defaultEnabledTools,
    val codeSandbox: SandboxType = SandboxType.RHINO,
    val reflectionEnabled: Boolean = true
) {
    companion object {
        val defaultEnabledTools = setOf(
            "calculator", "web_search", "file_reader",
            "note", "date_time", "clipboard", "code_interpreter"
        )
    }
}

@Serializable
enum class PlanStrategy { REACT, FUNCTION_CALLING }

@Serializable
enum class SandboxType { RHINO, WEBVIEW, OFF }
