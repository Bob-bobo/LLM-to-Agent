package com.hermes.agent.core.llm

import kotlinx.coroutines.flow.Flow

/**
 * Core interface for all LLM providers.
 * Each provider must implement both synchronous and streaming generation.
 */
interface LlmProvider {
    val id: String
    val name: String

    /**
     * Synchronous generation - waits for the full response.
     */
    suspend fun generate(request: LlmRequest): LlmResponse

    /**
     * Streaming generation - emits events as they arrive.
     */
    fun generateStream(request: LlmRequest): Flow<LlmStreamEvent>
}
