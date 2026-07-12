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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import timber.log.Timber
import com.hermes.agent.core.common.util.HermesJson

/**
 * Anthropic Claude Messages API adapter.
 * Key differences from OpenAI:
 * - Uses /v1/messages endpoint
 * - System prompt is a top-level field, not a message
 * - Tool definitions use input_schema
 * - Streaming uses content_block_delta events
 * - x-api-key header instead of Bearer token
 */
class AnthropicAdapter(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com",
    private val httpClient: OkHttpClient
) : LlmProvider {

    override val id: String = "anthropic"
    override val name: String = "Claude"

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }

    override suspend fun generate(request: LlmRequest): LlmResponse {
        val body = buildRequestBody(request, stream = false)
        val httpRequest = Request.Builder()
            .url("$baseUrl/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("content-type", "application/json")
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
            .url("$baseUrl/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("content-type", "application/json")
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
                            val streamEvents = parseStreamEvent(event.event, event.data, streamBuffer)
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

    private fun buildRequestBody(request: LlmRequest, stream: Boolean): String {
        val json = buildJsonObject {
            put("model", request.model)
            put("stream", stream)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            put("top_p", request.topP)

            // System message (top-level in Anthropic)
            request.system?.let {
                putJsonArray("system") {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", it)
                    })
                }
            }

            // Messages (filter out system role)
            putJsonArray("messages") {
                for (msg in request.messages.filter { it.role != "system" }) {
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
                putJsonArray("stop_sequences") {
                    it.forEach { s -> add(s) }
                }
            }
        }
        return json.toString()
    }

    private fun buildMessageJson(message: Message): JsonObject = buildJsonObject {
        put("role", message.role)
        putJsonArray("content") {
            for (block in message.content) {
                when (block) {
                    is ContentBlock.Text -> add(buildJsonObject {
                        put("type", "text")
                        put("text", block.text)
                    })
                    is ContentBlock.Image -> add(buildJsonObject {
                        put("type", "image")
                        putJsonObject("source") {
                            put("type", "base64")
                            put("media_type", block.mimeType)
                            put("data", block.base64 ?: "")
                        }
                    })
                    is ContentBlock.ToolUse -> add(buildJsonObject {
                        put("type", "tool_use")
                        put("id", block.id)
                        put("name", block.name)
                        put("input", HermesJson.parseToJsonElement(block.input) as JsonElement)
                    })
                    is ContentBlock.ToolResult -> add(buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", block.toolUseId)
                        put("content", block.output)
                        put("is_error", block.isError)
                    })
                }
            }
        }
    }

    private fun buildToolJson(tool: ToolDefinition): JsonObject = buildJsonObject {
        put("name", tool.name)
        put("description", tool.description)
        put("input_schema", HermesJson.parseToJsonElement(tool.inputSchema))
    }

    // ========== Response Parsing ==========

    private fun parseResponse(body: String): LlmResponse {
        val json = HermesJson.parseToJsonElement(body).jsonObject
        val contentBlocks = mutableListOf<ContentBlock>()
        val toolCalls = mutableListOf<ToolCall>()

        json["content"]?.jsonArray?.forEach { element ->
            val block = element.jsonObject
            when (block["type"]?.jsonPrimitive?.contentOrNull) {
                "text" -> {
                    val text = block["text"]?.jsonPrimitive?.contentOrNull ?: ""
                    contentBlocks.add(ContentBlock.Text(text))
                }
                "tool_use" -> {
                    val id = block["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val name = block["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val input = block["input"]?.toString() ?: "{}"
                    toolCalls.add(ToolCall(id, name, input))
                    contentBlocks.add(ContentBlock.ToolUse(id, name, input))
                }
            }
        }

        val stopReason = when (json["stop_reason"]?.jsonPrimitive?.contentOrNull) {
            "end_turn" -> StopReason.END_TURN
            "tool_use" -> StopReason.TOOL_USE
            "max_tokens" -> StopReason.MAX_TOKENS
            "stop_sequence" -> StopReason.STOP_SEQUENCE
            else -> StopReason.END_TURN
        }

        val usage = json["usage"]?.jsonObject?.let {
            TokenUsage(
                inputTokens = it["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                outputTokens = it["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            )
        }

        return LlmResponse(contentBlocks, toolCalls, stopReason, usage)
    }

    // ========== Stream Event Parsing ==========

    private fun parseStreamEvent(
        eventType: String,
        data: String,
        streamBuffer: StreamBuffer
    ): List<LlmStreamEvent> {
        val events = mutableListOf<LlmStreamEvent>()
        try {
            val json = HermesJson.parseToJsonElement(data).jsonObject

            when (eventType) {
                "content_block_delta" -> {
                    val delta = json["delta"]?.jsonObject ?: return emptyList()
                    when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                        "text_delta" -> {
                            val text = delta["text"]?.jsonPrimitive?.contentOrNull ?: ""
                            if (text.isNotEmpty()) events.add(LlmStreamEvent.TextDelta(text))
                        }
                        "input_json_delta" -> {
                            val toolId = json["index"]?.jsonPrimitive?.intOrNull?.toString() ?: return emptyList()
                            val partialJson = delta["partial_json"]?.jsonPrimitive?.contentOrNull ?: ""
                            if (partialJson.isNotEmpty()) {
                                streamBuffer.appendArguments(toolId, partialJson)
                                events.add(LlmStreamEvent.ToolCallDelta(toolId, partialJson))
                            }
                        }
                    }
                }
                "content_block_start" -> {
                    val contentBlock = json["content_block"]?.jsonObject ?: return emptyList()
                    if (contentBlock["type"]?.jsonPrimitive?.contentOrNull == "tool_use") {
                        val id = contentBlock["id"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
                        val name = contentBlock["name"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
                        streamBuffer.setName(id, name)
                        events.add(LlmStreamEvent.ToolCallBegin(id, name))
                    }
                }
                "content_block_stop" -> {
                    // Tool call completed
                    val index = json["index"]?.jsonPrimitive?.intOrNull ?: return emptyList()
                    // Find the tool call by index and emit ToolCallEnd
                }
                "message_stop" -> {
                    // Message completed
                }
                "message_delta" -> {
                    val delta = json["delta"]?.jsonObject
                    val stopReason = delta?.get("stop_reason")?.jsonPrimitive?.contentOrNull
                    if (stopReason != null) {
                        // Complete any pending tool calls
                    }
                    val usage = json["usage"]?.jsonObject
                    if (usage != null) {
                        events.add(LlmStreamEvent.Usage(TokenUsage(
                            outputTokens = usage["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                        )))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse Anthropic stream event: %s", data.take(100))
        }
        return events
    }

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
