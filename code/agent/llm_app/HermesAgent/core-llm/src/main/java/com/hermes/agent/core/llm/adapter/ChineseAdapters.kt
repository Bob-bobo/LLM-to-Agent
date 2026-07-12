package com.hermes.agent.core.llm.adapter

import okhttp3.OkHttpClient

/**
 * DeepSeek adapter - extends OpenAI adapter with DeepSeek's base URL.
 * DeepSeek's API is OpenAI-compatible.
 */
class DeepSeekAdapter(
    apiKey: String,
    httpClient: OkHttpClient
) : OpenAiAdapter(
    apiKey = apiKey,
    baseUrl = "https://api.deepseek.com/v1",
    httpClient = httpClient
) {
    override val id: String = "deepseek"
    override val name: String = "DeepSeek"
}

/**
 * Qwen (Tongyi Qianwen) adapter via DashScope.
 * DashScope provides an OpenAI-compatible endpoint.
 */
class QwenAdapter(
    apiKey: String,
    httpClient: OkHttpClient
) : OpenAiAdapter(
    apiKey = apiKey,
    baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    httpClient = httpClient
) {
    override val id: String = "qwen"
    override val name: String = "Qwen"
}

/**
 * GLM (Zhipu AI) adapter.
 * Zhipu provides an OpenAI-compatible endpoint.
 */
class GlmAdapter(
    apiKey: String,
    httpClient: OkHttpClient
) : OpenAiAdapter(
    apiKey = apiKey,
    baseUrl = "https://open.bigmodel.cn/api/paas/v4",
    httpClient = httpClient
) {
    override val id: String = "glm"
    override val name: String = "GLM"
}

/**
 * Moonshot (Kimi) adapter.
 * Moonshot provides an OpenAI-compatible endpoint.
 */
class MoonshotAdapter(
    apiKey: String,
    httpClient: OkHttpClient
) : OpenAiAdapter(
    apiKey = apiKey,
    baseUrl = "https://api.moonshot.cn/v1",
    httpClient = httpClient
) {
    override val id: String = "moonshot"
    override val name: String = "Moonshot"
}

/**
 * Baichuan adapter.
 * Baichuan provides an OpenAI-compatible endpoint.
 */
class BaichuanAdapter(
    apiKey: String,
    httpClient: OkHttpClient
) : OpenAiAdapter(
    apiKey = apiKey,
    baseUrl = "https://api.baichuan-ai.com/v1",
    httpClient = httpClient
) {
    override val id: String = "baichuan"
    override val name: String = "Baichuan"
}

/**
 * Custom endpoint adapter for user-defined OpenAI-compatible APIs.
 */
class CustomEndpointAdapter(
    apiKey: String,
    baseUrl: String,
    httpClient: OkHttpClient,
    override val id: String = "custom",
    override val name: String = "Custom"
) : OpenAiAdapter(
    apiKey = apiKey,
    baseUrl = baseUrl,
    httpClient = httpClient
)
