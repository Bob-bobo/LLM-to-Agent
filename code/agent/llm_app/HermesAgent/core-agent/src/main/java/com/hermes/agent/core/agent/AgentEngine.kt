package com.hermes.agent.core.agent

import com.hermes.agent.core.agent.planner.Planner
import com.hermes.agent.core.agent.tool.ToolExecutor
import com.hermes.agent.core.agent.tool.ToolRegistry
import com.hermes.agent.core.data.model.AgentPlanConfig
import com.hermes.agent.core.llm.ContentBlock
import com.hermes.agent.core.llm.LlmProvider
import com.hermes.agent.core.llm.LlmRequest
import com.hermes.agent.core.llm.LlmResponse
import com.hermes.agent.core.llm.LlmStreamEvent
import com.hermes.agent.core.llm.Message
import com.hermes.agent.core.llm.ToolCall
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The central agent engine that orchestrates the multi-step reasoning loop.
 *
 * Flow:
 * 1. User sends a message
 * 2. AgentEngine enters a loop (up to maxSteps)
 * 3. Each step: call LLM with conversation history + tool definitions
 * 4. If LLM returns tool calls → execute tools → add results to history → loop
 * 5. If LLM returns text only → that's the final answer → exit loop
 * 6. If max steps reached → force a summary
 */
@Singleton
class AgentEngine @Inject constructor(
    private val planner: Planner,
    private val toolExecutor: ToolExecutor,
    private val toolRegistry: ToolRegistry
) {
    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    /**
     * Run the agent loop with the given user message and conversation history.
     * Returns the final assistant message.
     */
    suspend fun run(
        modelId: String,
        userMessage: Message,
        history: List<Message>,
        provider: LlmProvider,
        planConfig: AgentPlanConfig
    ): Message {
        val messages = mutableListOf<Message>().apply {
            addAll(history)
            add(userMessage)
        }
        val tools = if (planConfig.enabledTools.isNotEmpty()) {
            toolRegistry.toToolDefinitions().filter { it.name in planConfig.enabledTools }
        } else {
            emptyList()
        }

        for (step in 1..planConfig.maxSteps) {
            _events.emit(AgentEvent.StepStart(step, planConfig.maxSteps))
            Timber.d("Agent step %d/%d", step, planConfig.maxSteps)

            try {
                // Plan: call LLM with tools
                val request = LlmRequest(
                    model = modelId,
                    messages = messages.toList(),
                    tools = tools
                )

                // Use streaming if the provider supports it
                val response = planner.plan(provider, request, planConfig)
                _events.emit(AgentEvent.LlmResponse(response))

                // Process text content
                val textContent = response.textContent
                if (textContent.isNotEmpty()) {
                    // Emit text deltas for streaming effect
                    _events.emit(AgentEvent.TextDelta(textContent))
                }

                // Process tool calls
                if (response.toolCalls.isNotEmpty()) {
                    // Add assistant message with tool calls to history
                    messages.add(Message(
                        role = "assistant",
                        content = response.content
                    ))

                    for (toolCall in response.toolCalls) {
                        _events.emit(AgentEvent.ToolCallStart(toolCall))
                        Timber.d("Tool call: %s(%s)", toolCall.name, toolCall.arguments.take(100))

                        // Execute the tool
                        val result = toolExecutor.execute(toolCall)
                        _events.emit(AgentEvent.ToolCallComplete(
                            toolCall.id, toolCall.name, result
                        ))
                        Timber.d("Tool result: %s", result.output.take(100))

                        // Add tool result to conversation
                        messages.add(Message(
                            role = "user",
                            content = listOf(ContentBlock.ToolResult(
                                toolUseId = toolCall.id,
                                name = toolCall.name,
                                output = result.output,
                                isError = result.isError
                            ))
                        ))
                    }
                    // Continue the loop - LLM will see tool results
                    continue
                }

                // No tool calls = final answer
                _events.emit(AgentEvent.FinalAnswer(textContent))
                return Message(
                    role = "assistant",
                    content = response.content
                )

            } catch (e: Exception) {
                Timber.e(e, "Agent step %d failed", step)
                _events.emit(AgentEvent.Error(e.message ?: "Step $step failed", e))

                // On error, try to continue if we have steps left
                if (step == planConfig.maxSteps) {
                    val errorMsg = "Agent encountered an error after $step steps: ${e.message}"
                    _events.emit(AgentEvent.FinalAnswer(errorMsg))
                    return Message(role = "assistant", content = listOf(ContentBlock.Text(errorMsg)))
                }
            }
        }

        // Max steps reached - force summarize
        val summary = planner.summarize(provider, modelId, messages.toList())
        _events.emit(AgentEvent.FinalAnswer(summary))
        return Message(role = "assistant", content = listOf(ContentBlock.Text(summary)))
    }

    /**
     * Run the agent with streaming support.
     * Collects LLM stream events and re-emits them as AgentEvents.
     */
    suspend fun runStream(
        modelId: String,
        userMessage: Message,
        history: List<Message>,
        provider: LlmProvider,
        planConfig: AgentPlanConfig
    ): Message {
        val messages = mutableListOf<Message>().apply {
            addAll(history)
            add(userMessage)
        }
        val tools = if (planConfig.enabledTools.isNotEmpty()) {
            toolRegistry.toToolDefinitions().filter { it.name in planConfig.enabledTools }
        } else {
            emptyList()
        }

        for (step in 1..planConfig.maxSteps) {
            _events.emit(AgentEvent.StepStart(step, planConfig.maxSteps))

            try {
                val request = LlmRequest(
                    model = modelId,
                    messages = messages.toList(),
                    tools = tools
                )

                // Use planner with streaming
                val response = planner.planStream(provider, request, planConfig) { streamEvent ->
                    // Forward stream events as agent events
                    when (streamEvent) {
                        is LlmStreamEvent.TextDelta -> {
                            // Emit directly to agent events
                            _events.emit(AgentEvent.TextDelta(streamEvent.text))
                        }
                        is LlmStreamEvent.ToolCallBegin -> {
                            _events.emit(AgentEvent.ToolCallStart(
                                ToolCall(streamEvent.id, streamEvent.name, "")
                            ))
                        }
                        else -> { /* Other events handled internally by planner */ }
                    }
                }

                _events.emit(AgentEvent.LlmResponse(response))

                if (response.toolCalls.isNotEmpty()) {
                    messages.add(Message(role = "assistant", content = response.content))

                    for (toolCall in response.toolCalls) {
                        _events.emit(AgentEvent.ToolCallStart(toolCall))
                        val result = toolExecutor.execute(toolCall)
                        _events.emit(AgentEvent.ToolCallComplete(toolCall.id, toolCall.name, result))
                        messages.add(Message(
                            role = "user",
                            content = listOf(ContentBlock.ToolResult(
                                toolUseId = toolCall.id,
                                name = toolCall.name,
                                output = result.output,
                                isError = result.isError
                            ))
                        ))
                    }
                    continue
                }

                _events.emit(AgentEvent.FinalAnswer(response.textContent))
                return Message(role = "assistant", content = response.content)

            } catch (e: Exception) {
                Timber.e(e, "Agent stream step %d failed", step)
                _events.emit(AgentEvent.Error(e.message ?: "Step $step failed", e))
                if (step == planConfig.maxSteps) {
                    val errorMsg = "Agent error after $step steps: ${e.message}"
                    _events.emit(AgentEvent.FinalAnswer(errorMsg))
                    return Message(role = "assistant", content = listOf(ContentBlock.Text(errorMsg)))
                }
            }
        }

        val summary = planner.summarize(provider, modelId, messages.toList())
        _events.emit(AgentEvent.FinalAnswer(summary))
        return Message(role = "assistant", content = listOf(ContentBlock.Text(summary)))
    }
}
