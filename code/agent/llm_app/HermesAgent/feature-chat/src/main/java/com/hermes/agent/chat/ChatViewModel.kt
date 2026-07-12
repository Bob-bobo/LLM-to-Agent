package com.hermes.agent.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.agent.core.agent.AgentEngine
import com.hermes.agent.core.agent.AgentEvent
import com.hermes.agent.core.agent.ToolResult
import com.hermes.agent.core.data.keystore.ApiKeyStore
import com.hermes.agent.core.data.model.AgentPlanConfig
import com.hermes.agent.core.data.model.ChatMessage
import com.hermes.agent.core.data.model.ContentBlock
import com.hermes.agent.core.data.model.Conversation
import com.hermes.agent.core.data.model.MessageRole
import com.hermes.agent.core.data.model.ProviderConfig
import com.hermes.agent.core.data.prefs.UserPrefs
import com.hermes.agent.core.data.repository.AgentPlanRepository
import com.hermes.agent.core.data.repository.ConversationRepository
import com.hermes.agent.core.data.repository.ProviderRepository
import com.hermes.agent.core.llm.ContentBlock as LlmContentBlock
import com.hermes.agent.core.llm.LlmProvider
import com.hermes.agent.core.llm.Message
import com.hermes.agent.core.llm.factory.LlmProviderFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

data class ChatUiState(
    val conversationId: Long = 0,
    val title: String = "新对话",
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val activeToolCalls: Map<String, ToolCallUiState> = emptyMap(),
    val attachments: List<AttachmentUiState> = emptyList(),
    val error: String? = null,
    val activeProviderId: String = "",
    val activeModelId: String = "",
    val providerReady: Boolean = false  // true when currentProvider is initialized
)

data class ToolCallUiState(
    val name: String,
    val arguments: String,
    val result: String? = null,
    val isError: Boolean = false,
    val isExpanded: Boolean = false
)

data class AttachmentUiState(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
    val type: AttachmentType
)

enum class AttachmentType { IMAGE, FILE, VIDEO, AUDIO }

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentEngine: AgentEngine,
    private val conversationRepo: ConversationRepository,
    private val providerRepo: ProviderRepository,
    private val agentPlanRepo: AgentPlanRepository,
    private val apiKeyStore: ApiKeyStore,
    private val llmProviderFactory: LlmProviderFactory,
    private val userPrefs: UserPrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentProvider: LlmProvider? = null
    private var currentPlanConfig: AgentPlanConfig? = null

    init {
        // Load persisted active provider/model and initialize LLM provider
        loadActiveProvider()

        // Load current plan config
        viewModelScope.launch {
            try {
                val plan = agentPlanRepo.getPlanOrDefault()
                currentPlanConfig = plan
            } catch (e: Exception) {
                Timber.e(e, "Failed to load agent plan")
                currentPlanConfig = AgentPlanConfig()
            }
        }
    }

    /**
     * Load the persisted active provider and create the LLM provider instance.
     * This is the key fix: ChatViewModel now auto-initializes from UserPrefs.
     */
    private fun loadActiveProvider() {
        val providerId = userPrefs.activeProviderId
        val modelId = userPrefs.activeModelId

        if (providerId.isEmpty()) {
            Timber.w("No active provider configured yet")
            _uiState.update {
                it.copy(providerReady = false, error = null)
            }
            return
        }

        // Update UI state with the persisted selection
        _uiState.update {
            it.copy(
                activeProviderId = providerId,
                activeModelId = modelId
            )
        }

        // Load provider config and create LLM provider
        viewModelScope.launch {
            try {
                val providerConfig = providerRepo.getProvider(providerId)
                if (providerConfig == null) {
                    Timber.w("Provider %s not found in DB", providerId)
                    _uiState.update {
                        it.copy(
                            providerReady = false,
                            error = "提供商 $providerId 未找到，请在设置中重新选择"
                        )
                    }
                    return@launch
                }

                val apiKey = apiKeyStore.getKey(providerId)
                if (apiKey.isNullOrEmpty()) {
                    Timber.w("No API key for provider %s", providerId)
                    _uiState.update {
                        it.copy(
                            providerReady = false,
                            error = "未配置 ${providerConfig.displayName} 的 API 密钥，请在设置中添加"
                        )
                    }
                    return@launch
                }

                // Create the LLM provider
                currentProvider = llmProviderFactory.create(providerConfig, apiKey)
                _uiState.update {
                    it.copy(
                        providerReady = true,
                        activeProviderId = providerId,
                        activeModelId = modelId,
                        error = null
                    )
                }
                Timber.d("Loaded provider: %s, model: %s", providerConfig.displayName, modelId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load active provider")
                _uiState.update {
                    it.copy(
                        providerReady = false,
                        error = "加载提供商失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Send a user message and run the agent.
     */
    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() && _uiState.value.attachments.isEmpty()) return

        // Check provider readiness
        val provider = currentProvider
        if (provider == null) {
            _uiState.update {
                it.copy(
                    error = "未配置 LLM 提供商。请在设置中选择提供商并添加 API 密钥。"
                )
            }
            // Try to reload provider (maybe user just configured it in settings)
            loadActiveProvider()
            return
        }

        viewModelScope.launch {
            val currentState = _uiState.value

            // Build user content blocks
            val contentBlocks = mutableListOf<ContentBlock>()
            if (text.isNotEmpty()) {
                contentBlocks.add(ContentBlock.Text(text))
            }
            for (attachment in currentState.attachments) {
                when (attachment.type) {
                    AttachmentType.IMAGE -> contentBlocks.add(ContentBlock.Image(
                        uri = attachment.uri, mimeType = attachment.mimeType
                    ))
                    AttachmentType.FILE -> contentBlocks.add(ContentBlock.File(
                        uri = attachment.uri, name = attachment.name,
                        mimeType = attachment.mimeType, sizeBytes = attachment.sizeBytes
                    ))
                    AttachmentType.AUDIO -> contentBlocks.add(ContentBlock.Audio(
                        uri = attachment.uri, mimeType = attachment.mimeType
                    ))
                    AttachmentType.VIDEO -> contentBlocks.add(ContentBlock.Video(
                        uri = attachment.uri, mimeType = attachment.mimeType
                    ))
                }
            }

            // Create user message
            val userMessage = ChatMessage(
                conversationId = currentState.conversationId,
                role = MessageRole.USER,
                content = contentBlocks,
                createdAt = Instant.now()
            )

            // Update UI
            _uiState.update {
                it.copy(
                    messages = it.messages + userMessage,
                    inputText = "",
                    isStreaming = true,
                    streamingText = "",
                    attachments = emptyList(),
                    error = null
                )
            }

            // Save message to DB
            if (currentState.conversationId > 0) {
                try {
                    conversationRepo.addMessage(userMessage)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to save user message")
                }
            }

            val planConfig = currentPlanConfig ?: AgentPlanConfig()

            // Convert to LLM messages
            val history = _uiState.value.messages.map { msg ->
                Message(
                    role = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.SYSTEM -> "system"
                        MessageRole.TOOL -> "tool"
                    },
                    content = msg.content.map { block ->
                        when (block) {
                            is ContentBlock.Text -> LlmContentBlock.Text(block.text)
                            is ContentBlock.ToolUse -> LlmContentBlock.ToolUse(block.id, block.name, block.input)
                            is ContentBlock.ToolResult -> LlmContentBlock.ToolResult(block.toolUseId, block.name, block.output, block.isError)
                            is ContentBlock.Image -> LlmContentBlock.Image(url = block.uri, mimeType = block.mimeType)
                            else -> LlmContentBlock.Text("[Unsupported content]")
                        }
                    }
                )
            }

            val llmUserMessage = Message(
                role = "user",
                content = contentBlocks.map { block ->
                    when (block) {
                        is ContentBlock.Text -> LlmContentBlock.Text(block.text)
                        is ContentBlock.Image -> LlmContentBlock.Image(url = block.uri, mimeType = block.mimeType)
                        else -> LlmContentBlock.Text("[${block::class.simpleName}]")
                    }
                }
            )

            // Collect agent events with proper lifecycle management
            try {
                val modelId = _uiState.value.activeModelId

                // Run agent in a coroutine that completes when the agent finishes
                val agentJob = launch {
                    try {
                        agentEngine.runStream(modelId, llmUserMessage, history, provider, planConfig)
                    } catch (e: Exception) {
                        Timber.e(e, "Agent execution threw")
                        _uiState.update {
                            it.copy(isStreaming = false, error = "Agent 执行出错: ${e.message}")
                        }
                    }
                }

                // Collect events until the agent job completes
                val collectJob = launch {
                    agentEngine.events.collect { event ->
                        when (event) {
                            is AgentEvent.StepStart -> _uiState.update {
                                it.copy(currentStep = event.step, totalSteps = event.totalSteps)
                            }
                            is AgentEvent.TextDelta -> _uiState.update {
                                it.copy(streamingText = it.streamingText + event.text)
                            }
                            is AgentEvent.ToolCallStart -> _uiState.update {
                                it.copy(activeToolCalls = it.activeToolCalls + (
                                    event.call.id to ToolCallUiState(
                                        name = event.call.name,
                                        arguments = event.call.arguments
                                    )
                                ))
                            }
                            is AgentEvent.ToolCallComplete -> {
                                val existingCall = _uiState.value.activeToolCalls[event.callId]
                                if (existingCall != null) {
                                    _uiState.update {
                                        it.copy(activeToolCalls = it.activeToolCalls + (
                                            event.callId to existingCall.copy(
                                                result = event.result.output,
                                                isError = event.result.isError
                                            )
                                        ))
                                    }
                                }
                            }
                            is AgentEvent.FinalAnswer -> {
                                // Create assistant message
                                val assistantMessage = ChatMessage(
                                    conversationId = _uiState.value.conversationId,
                                    role = MessageRole.ASSISTANT,
                                    content = listOf(ContentBlock.Text(
                                        if (_uiState.value.streamingText.isNotEmpty())
                                            _uiState.value.streamingText
                                        else event.text
                                    )),
                                    createdAt = Instant.now()
                                )
                                _uiState.update {
                                    it.copy(
                                        messages = it.messages + assistantMessage,
                                        isStreaming = false,
                                        streamingText = "",
                                        activeToolCalls = emptyMap()
                                    )
                                }
                                // Save to DB
                                if (_uiState.value.conversationId > 0) {
                                    try {
                                        conversationRepo.addMessage(assistantMessage)
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to save assistant message")
                                    }
                                }
                                // Cancel collection — agent is done
                                collectJob.cancel()
                            }
                            is AgentEvent.Error -> {
                                _uiState.update {
                                    it.copy(
                                        isStreaming = false,
                                        error = event.message
                                    )
                                }
                            }
                            is AgentEvent.LlmResponse -> { /* handled via specific events */ }
                        }
                    }
                }

                // When agent job finishes, also cancel the collection
                agentJob.invokeOnCompletion {
                    collectJob.cancel()
                }

            } catch (e: Exception) {
                Timber.e(e, "Agent execution failed")
                _uiState.update {
                    it.copy(isStreaming = false, error = "Agent 错误: ${e.message}")
                }
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun addAttachment(attachment: AttachmentUiState) {
        _uiState.update { it.copy(attachments = it.attachments + attachment) }
    }

    fun removeAttachment(index: Int) {
        _uiState.update { it.copy(attachments = it.attachments.toMutableList().apply { removeAt(index) }) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun toggleToolCallExpanded(callId: String) {
        val existingCall = _uiState.value.activeToolCalls[callId] ?: return
        _uiState.update {
            it.copy(activeToolCalls = it.activeToolCalls + (
                callId to existingCall.copy(isExpanded = !existingCall.isExpanded)
            ))
        }
    }

    /**
     * Set the active provider and model (called when user changes selection in Settings).
     * Also persists the selection via UserPrefs.
     */
    fun setActiveProvider(providerConfig: ProviderConfig, modelId: String) {
        val apiKey = try {
            apiKeyStore.getKey(providerConfig.id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get API key")
            null
        }

        if (apiKey == null) {
            _uiState.update { it.copy(error = "未配置 ${providerConfig.displayName} 的 API 密钥") }
            return
        }

        currentProvider = llmProviderFactory.create(providerConfig, apiKey)
        _uiState.update {
            it.copy(
                activeProviderId = providerConfig.id,
                activeModelId = modelId,
                providerReady = true,
                error = null
            )
        }
        // Persist selection
        userPrefs.activeProviderId = providerConfig.id
        userPrefs.activeModelId = modelId
    }

    /**
     * Refresh the provider — called when returning from Settings where
     * the user may have changed the active provider/model.
     */
    fun refreshProvider() {
        loadActiveProvider()
    }
}
