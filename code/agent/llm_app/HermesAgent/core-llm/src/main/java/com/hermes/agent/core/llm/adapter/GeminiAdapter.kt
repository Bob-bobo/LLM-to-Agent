package com.hermes.agent.core.llm.adapter

import com.hermes.agent.core.llm.*
import com.hermes.agent.core.llm.streaming.SseParser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import timber.log.Timber
import com.hermes.agent.core.common.util.HermesJson

/**
 * Google Gemini API adapter.
 * Key differences from OpenAI:
 * - Uses generateContent endpoint
 * - contents array with parts instead of messages
 * - functionDeclarations instead of tools
 * - API key as query parameter ?key=...
 * - Streaming via streamGenerateContent?alt=sse
 */
class GeminiAdapter(
    private val apiKey: String,
    private val httpClient: OkHttpClient
) : LlmProvider {

    override val id: String = "gemini"
    override val name: String = "Gemini"

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }

    override suspend fun generate(request: LlmRequest): LlmResponse {
        val body = buildRequestBody(request)
        val httpRequest = Request.Builder()
            .url("$BASE_URL/models/${request.model}:generateContent?key=$apiKey")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = suspendCancellableHttpCall(httpRequest)
        val responseBody = response.body?.string()
            ?: throw IOException("Empty response body")

        return parseResponse(responseBody)
    }

    override fun generateStream(request: LlmRequest): Flow<LlmStreamEvent> = callbackFlow {
        val body = buildRequestBody(request)
        val httpRequest = Request.Builder()
            .url("$BASE_URL/models/${request.model}:streamGenerateContent?alt=sse&key=$apiKey")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = httpClient.newCall(httpRequest)
        val sseParser = SseParser()

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
                            val streamEvents = parseStreamChunk(event.data)
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

    private fun buildRequestBody(request: LlmRequest): String {
        val json = buildJsonObject {
            putJsonArray("contents") {
                for (msg in request.messages) {
                    add(buildContentJson(msg))
                }
            }

            // System instruction
            request.system?.let {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", it) })
                    }
                }
            }

            // Tools
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    add(buildJsonObject {
                        putJsonArray("functionDeclarations") {
                            for (tool in request.tools) {
                                add(buildToolJson(tool))
                            }
                        }
                    })
                }
            }

            // Generation config
            putJsonObject("generationConfig") {
                put("temperature", request.temperature)
                put("maxOutputTokens", request.maxTokens)
                put("topP", request.topP)
                request.stopSequences?.let {
                    putJsonArray("stopSequences") {
                        it.forEach { s -> add(s) }
                    }
                }
            }
        }
        return json.toString()
    }

    private fun buildContentJson(message: Message): JsonObject = buildJsonObject {
        put("role", if (message.role == "assistant") "model" else "user")
        putJsonArray("parts") {
            for (block in message.content) {
                when (block) {
                    is ContentBlock.Text -> add(buildJsonObject { put("text", block.text) })
                    is ContentBlock.Image -> add(buildJsonObject {
                        putJsonObject("inlineData") {
                            put("mimeType", block.mimeType)
                            put("data", block.base64 ?: "")
                        }
                    })
                    is ContentBlock.ToolUse -> add(buildJsonObject {
                        putJsonObject("functionCall") {
                            put("name", block.name)
                            put("args", HermesJson.parseToJsonElement(block.input))
                        }
                    })
                    is ContentBlock.ToolResult -> add(buildJsonObject {
                        putJsonObject("functionResponse") {
                            put("name", block.name) // Gemini needs name in response
                            putJsonObject("response") {
                                put("result", block.output)
                            }
                        }
                    })
                }
            }
        }
    }

    private fun buildToolJson(tool: ToolDefinition): JsonObject = buildJsonObject {
        put("name", tool.name)
        put("description", tool.description)
        put("parameters", HermesJson.parseToJsonElement(tool.inputSchema))
    }

    // ========== Response Parsing ==========

    private fun parseResponse(body: String): LlmResponse {
        val json = HermesJson.parseToJsonElement(body).jsonObject
        val candidate = json["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return LlmResponse()

        val contentBlocks = mutableListOf<ContentBlock>()
        val toolCalls = mutableListOf<ToolCall>()

        candidate["content"]?.jsonObject?.get("parts")?.jsonArray?.forEach { partElement ->
            val part = partElement.jsonObject
            part["text"]?.jsonPrimitive?.contentOrNull?.let {
                contentBlocks.add(ContentBlock.Text(it))
            }
            part["functionCall"]?.jsonObject?.let { fc ->
                val name = fc["name"]?.jsonPrimitive?.contentOrNull ?: return@let
                val args = fc["args"]?.toString() ?: "{}"
                val id = "tool_${System.nanoTime()}"
                toolCalls.add(ToolCall(id, name, args))
                contentBlocks.add(ContentBlock.ToolUse(id, name, args))
            }
        }

        val stopReason = when (candidate["finishReason"]?.jsonPrimitive?.contentOrNull) {
            "STOP" -> StopReason.END_TURN
            "RECITATION" -> StopReason.END_TURN
            "SAFETY" -> StopReason.END_TURN
            else -> StopReason.END_TURN
        }

        val usage = json["usageMetadata"]?.jsonObject?.let {
            TokenUsage(
                inputTokens = it["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
                outputTokens = it["candidatesTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
                totalTokens = it["totalTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
            )
        }

        return LlmResponse(contentBlocks, toolCalls, stopReason, usage)
    }

    private fun parseStreamChunk(data: String): List<LlmStreamEvent> {
        val events = mutableListOf<LlmStreamEvent>()
        try {
            val json = HermesJson.parseToJsonElement(data).jsonObject
            val candidate = json["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: return emptyList()

            candidate["content"]?.jsonObject?.get("parts")?.jsonArray?.forEach { partElement ->
                val part = partElement.jsonObject
                part["text"]?.jsonPrimitive?.contentOrNull?.let {
                    if (it.isNotEmpty()) events.add(LlmStreamEvent.TextDelta(it))
                }
                part["functionCall"]?.jsonObject?.let { fc ->
                    val name = fc["name"]?.jsonPrimitive?.contentOrNull ?: return@let
                    val args = fc["args"]?.toString() ?: "{}"
                    val id = "tool_${System.nanoTime()}"
                    events.add(LlmStreamEvent.ToolCallEnd(id, name, args))
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse Gemini stream chunk: %s", data.take(100))
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
