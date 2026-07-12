package com.hermes.agent.core.llm.factory

import com.hermes.agent.core.data.model.ProviderConfig
import com.hermes.agent.core.data.model.ProviderType
import com.hermes.agent.core.llm.LlmProvider
import com.hermes.agent.core.llm.adapter.*
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating LLM provider instances from configuration.
 */
@Singleton
class LlmProviderFactory @Inject constructor(
    private val httpClient: OkHttpClient
) {
    /**
     * Create an LLM provider from a ProviderConfig and API key.
     */
    fun create(config: ProviderConfig, apiKey: String): LlmProvider = when (config.type) {
        ProviderType.OPENAI -> OpenAiAdapter(
            apiKey = apiKey,
            baseUrl = config.baseUrl ?: "https://api.openai.com/v1",
            httpClient = httpClient
        )
        ProviderType.ANTHROPIC -> AnthropicAdapter(
            apiKey = apiKey,
            baseUrl = config.baseUrl ?: "https://api.anthropic.com",
            httpClient = httpClient
        )
        ProviderType.GEMINI -> GeminiAdapter(
            apiKey = apiKey,
            httpClient = httpClient
        )
        ProviderType.DEEPSEEK -> DeepSeekAdapter(
            apiKey = apiKey,
            httpClient = httpClient
        )
        ProviderType.QWEN -> QwenAdapter(
            apiKey = apiKey,
            httpClient = httpClient
        )
        ProviderType.GLM -> GlmAdapter(
            apiKey = apiKey,
            httpClient = httpClient
        )
        ProviderType.MOONSHOT -> MoonshotAdapter(
            apiKey = apiKey,
            httpClient = httpClient
        )
        ProviderType.BAICHUAN -> BaichuanAdapter(
            apiKey = apiKey,
            httpClient = httpClient
        )
        ProviderType.CUSTOM -> CustomEndpointAdapter(
            apiKey = apiKey,
            baseUrl = config.baseUrl ?: throw IllegalArgumentException("Custom provider requires a base URL"),
            httpClient = httpClient,
            id = config.id,
            name = config.displayName
        )
    }
}
