package com.hermes.agent.core.data.model

import kotlinx.serialization.Serializable

/**
 * Domain model for an LLM provider configuration.
 */
@Serializable
data class ProviderConfig(
    val id: String,              // e.g., "openai", "anthropic", "deepseek", "custom_1"
    val displayName: String,     // e.g., "OpenAI", "Claude", "DeepSeek"
    val type: ProviderType,      // Provider type for adapter selection
    val baseUrl: String? = null, // Custom base URL (null = use default)
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val models: List<ModelInfo> = emptyList() // Available models for this provider
)

@Serializable
enum class ProviderType {
    OPENAI,
    ANTHROPIC,
    GEMINI,
    DEEPSEEK,
    QWEN,
    GLM,
    MOONSHOT,
    BAICHUAN,
    CUSTOM
}

@Serializable
data class ModelInfo(
    val id: String,              // e.g., "gpt-4o", "claude-3-5-sonnet-20241022"
    val displayName: String,     // e.g., "GPT-4o", "Claude 3.5 Sonnet"
    val contextWindow: Int = 4096,  // Max context tokens
    val supportsToolCalling: Boolean = true,
    val supportsStreaming: Boolean = true,
    val supportsVision: Boolean = false
)

// Default models per provider
object DefaultModels {
    val OPENAI = listOf(
        ModelInfo("gpt-4o", "GPT-4o", 128000, true, true, true),
        ModelInfo("gpt-4o-mini", "GPT-4o Mini", 128000, true, true, true),
        ModelInfo("gpt-4-turbo", "GPT-4 Turbo", 128000, true, true, true),
    )
    val ANTHROPIC = listOf(
        ModelInfo("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", 200000, true, true, true),
        ModelInfo("claude-3-haiku-20240307", "Claude 3 Haiku", 200000, true, true, true),
    )
    val GEMINI = listOf(
        ModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", 1000000, true, true, true),
        ModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", 1000000, true, true, true),
    )
    val DEEPSEEK = listOf(
        ModelInfo("deepseek-chat", "DeepSeek Chat", 64000, true, true, false),
        ModelInfo("deepseek-coder", "DeepSeek Coder", 64000, true, true, false),
    )
    val QWEN = listOf(
        ModelInfo("qwen-turbo", "Qwen Turbo", 8192, true, true, false),
        ModelInfo("qwen-plus", "Qwen Plus", 32768, true, true, false),
        ModelInfo("qwen-max", "Qwen Max", 32768, true, true, false),
    )
    val GLM = listOf(
        ModelInfo("glm-4", "GLM-4", 128000, true, true, true),
        ModelInfo("glm-4-flash", "GLM-4 Flash", 128000, true, true, true),
    )
}

// Default providers
object DefaultProviders {
    val ALL = listOf(
        ProviderConfig("openai", "OpenAI", ProviderType.OPENAI, models = DefaultModels.OPENAI, sortOrder = 0),
        ProviderConfig("anthropic", "Claude", ProviderType.ANTHROPIC, models = DefaultModels.ANTHROPIC, sortOrder = 1),
        ProviderConfig("gemini", "Gemini", ProviderType.GEMINI, models = DefaultModels.GEMINI, sortOrder = 2),
        ProviderConfig("deepseek", "DeepSeek", ProviderType.DEEPSEEK, models = DefaultModels.DEEPSEEK, sortOrder = 3),
        ProviderConfig("qwen", "Qwen", ProviderType.QWEN, models = DefaultModels.QWEN, sortOrder = 4),
        ProviderConfig("glm", "GLM", ProviderType.GLM, models = DefaultModels.GLM, sortOrder = 5),
    )
}
