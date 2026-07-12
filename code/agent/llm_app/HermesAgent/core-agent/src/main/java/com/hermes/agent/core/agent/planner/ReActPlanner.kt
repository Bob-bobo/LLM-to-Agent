package com.hermes.agent.core.agent.planner

import com.hermes.agent.core.common.util.HermesJson
import com.hermes.agent.core.data.model.AgentPlanConfig
import com.hermes.agent.core.llm.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ReAct (Reasoning + Acting) planner.
 * Uses prompt engineering to make the LLM output Thought/Action/Observation in JSON.
 * This is the fallback for providers that lack native tool calling.
 *
 * The LLM is prompted to respond in the format:
 * ```json
 * {
 *   "thought": "I need to search for...",
 *   "action": "web_search",
 *   "action_input": {"query": "..."}
 * }
 * ```
 * or when done:
 * ```json
 * {
 *   "thought": "I now have the answer",
 *   "answer": "The answer is..."
 * }
 * ```
 */
@Singleton
class ReActPlanner @Inject constructor() : Planner {

    companion object {
        private const val REACT_SYSTEM_PROMPT = """You are a helpful AI assistant that can use tools to help the user.

You must respond in JSON format with one of these structures:

To use a tool:
```json
{
  "thought": "Your reasoning about what to do next",
  "action": "tool_name",
  "action_input": {"param1": "value1"}
}
```

To give a final answer:
```json
{
  "thought": "Your reasoning about the final answer",
  "answer": "Your final answer to the user"
}
```

Available tools will be listed in the tools section. Always think step by step.
Always respond with valid JSON only, no other text."""
    }

    override suspend fun plan(
        provider: LlmProvider,
        request: LlmRequest,
        config: AgentPlanConfig
    ): LlmResponse {
        // Build the ReAct prompt with tool descriptions
        val toolsDescription = if (request.tools.isNotEmpty()) {
            "\n\nAvailable tools:\n" + request.tools.joinToString("\n") { tool ->
                "- ${tool.name}: ${tool.description}"
            }
        } else ""

        val reactRequest = request.copy(
            system = REACT_SYSTEM_PROMPT + toolsDescription
        )

        val response = provider.generate(reactRequest)
        return parseReActResponse(response)
    }

    override suspend fun planStream(
        provider: LlmProvider,
        request: LlmRequest,
        config: AgentPlanConfig,
        onStreamEvent: suspend (LlmStreamEvent) -> Unit
    ): LlmResponse {
        // For ReAct, streaming is less useful since we need the full JSON
        // Just collect all text deltas and parse at the end
        val toolsDescription = if (request.tools.isNotEmpty()) {
            "\n\nAvailable tools:\n" + request.tools.joinToString("\n") { tool ->
                "- ${tool.name}: ${tool.description}"
            }
        } else ""

        val reactRequest = request.copy(
            system = REACT_SYSTEM_PROMPT + toolsDescription
        )

        val fullText = StringBuilder()
        provider.generateStream(reactRequest).collect { event ->
            when (event) {
                is LlmStreamEvent.TextDelta -> {
                    fullText.append(event.text)
                    onStreamEvent(event)
                }
                is LlmStreamEvent.Done -> { /* parse below */ }
                is LlmStreamEvent.Error -> {
                    Timber.e("ReAct stream error: %s", event.message)
                }
                else -> {}
            }
        }

        // Parse the accumulated text as ReAct response
        val textResponse = LlmResponse(
            content = listOf(ContentBlock.Text(fullText.toString())),
            stopReason = StopReason.END_TURN
        )
        return parseReActResponse(textResponse)
    }

    override suspend fun summarize(
        provider: LlmProvider,
        model: String,
        messages: List<Message>
    ): String {
        val request = LlmRequest(
            model = model,
            system = "Summarize the conversation and provide a final answer.",
            messages = messages,
            tools = emptyList()
        )
        return try {
            provider.generate(request).textContent
        } catch (e: Exception) {
            "I've completed the task but reached the maximum number of steps."
        }
    }

    /**
     * Parse the LLM's ReAct JSON response into a standard LlmResponse.
     */
    private fun parseReActResponse(response: LlmResponse): LlmResponse {
        val text = response.textContent
        if (text.isEmpty()) return response

        return try {
            // Try to extract JSON from the response
            val jsonStr = extractJson(text)
            val json = HermesJson.parseToJsonElement(jsonStr).jsonObject

            // Check if it's a final answer
            json["answer"]?.let { answerElement ->
                val answer = when (answerElement) {
                    is kotlinx.serialization.json.JsonPrimitive -> answerElement.content
                    else -> answerElement.toString()
                }
                return LlmResponse(
                    content = listOf(ContentBlock.Text(answer)),
                    stopReason = StopReason.END_TURN
                )
            }

            // Check if it's a tool call
            val action = json["action"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content }
            val actionInput = json["action_input"]?.toString() ?: "{}"

            if (action != null) {
                val toolCallId = "react_${System.nanoTime()}"
                LlmResponse(
                    content = listOf(
                        ContentBlock.Text(json["thought"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: ""),
                        ContentBlock.ToolUse(toolCallId, action, actionInput)
                    ),
                    toolCalls = listOf(ToolCall(toolCallId, action, actionInput)),
                    stopReason = StopReason.TOOL_USE
                )
            } else {
                // Couldn't parse as ReAct, treat as plain text
                response
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse ReAct response, treating as plain text")
            response
        }
    }

    /**
     * Extract JSON from text that might contain markdown code blocks.
     */
    private fun extractJson(text: String): String {
        // Try to find JSON in code blocks
        val codeBlockRegex = Regex("```(?:json)?\\s*\\n([\\s\\S]*?)\\n```")
        codeBlockRegex.find(text)?.let { return it.groupValues[1].trim() }

        // Try to find raw JSON object
        val jsonRegex = Regex("\\{[\\s\\S]*\\}")
        jsonRegex.find(text)?.let { return it.value }

        return text.trim()
    }
}
