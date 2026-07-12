package com.hermes.agent.core.agent.planner

import com.hermes.agent.core.data.model.AgentPlanConfig
import com.hermes.agent.core.llm.LlmProvider
import com.hermes.agent.core.llm.LlmRequest
import com.hermes.agent.core.llm.LlmResponse
import com.hermes.agent.core.llm.LlmStreamEvent
import com.hermes.agent.core.llm.Message

/**
 * Interface for pluggable planning strategies.
 * A planner decides how to call the LLM and process its response.
 */
interface Planner {
    /**
     * Plan the next step by calling the LLM.
     */
    suspend fun plan(
        provider: LlmProvider,
        request: LlmRequest,
        config: AgentPlanConfig
    ): LlmResponse

    /**
     * Plan with streaming support.
     * @param onStreamEvent callback for each stream event during generation
     */
    suspend fun planStream(
        provider: LlmProvider,
        request: LlmRequest,
        config: AgentPlanConfig,
        onStreamEvent: suspend (LlmStreamEvent) -> Unit
    ): LlmResponse

    /**
     * Summarize the conversation when max steps are reached.
     */
    suspend fun summarize(
        provider: LlmProvider,
        model: String,
        messages: List<Message>
    ): String
}
