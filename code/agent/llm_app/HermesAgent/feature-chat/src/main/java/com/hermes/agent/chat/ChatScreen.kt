package com.hermes.agent.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.hermes.agent.chat.components.ChatInputBar
import com.hermes.agent.chat.components.MediaAttachmentBar
import com.hermes.agent.chat.components.MessageBubble
import com.hermes.agent.chat.components.StreamingText
import com.hermes.agent.chat.components.ToolCallCard
import com.hermes.agent.core.common.theme.HermesThemeExt
import com.hermes.agent.core.data.model.ChatMessage
import com.hermes.agent.core.data.model.ContentBlock
import com.hermes.agent.core.data.model.MessageRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNewConversation: () -> Unit = {},
    onShowConversations: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Refresh provider when screen becomes active (e.g., returning from Settings)
    LaunchedEffect(Unit) {
        viewModel.refreshProvider()
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size, uiState.streamingText) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = uiState.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        if (uiState.activeModelId.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        uiState.activeModelId,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onShowConversations) {
                        Icon(Icons.Default.ChatBubble, contentDescription = "Conversations")
                    }
                },
                actions = {
                    IconButton(onClick = onNewConversation) {
                        Icon(Icons.Default.Add, contentDescription = "New chat")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                // Attachment preview bar
                if (uiState.attachments.isNotEmpty()) {
                    MediaAttachmentBar(
                        attachments = uiState.attachments,
                        onRemove = { viewModel.removeAttachment(it) }
                    )
                }

                // Input bar
                ChatInputBar(
                    value = uiState.inputText,
                    onValueChange = { viewModel.updateInputText(it) },
                    onSend = { viewModel.sendMessage() },
                    isStreaming = uiState.isStreaming,
                    onAttachImage = {
                        viewModel.addAttachment(
                            AttachmentUiState("", "image", "image/png", type = AttachmentType.IMAGE)
                        )
                    },
                    onAttachFile = {
                        viewModel.addAttachment(
                            AttachmentUiState("", "file", "application/octet-stream", type = AttachmentType.FILE)
                        )
                    },
                    onAttachVideo = {
                        viewModel.addAttachment(
                            AttachmentUiState("", "video", "video/mp4", type = AttachmentType.VIDEO)
                        )
                    },
                    onAttachAudio = {
                        viewModel.addAttachment(
                            AttachmentUiState("", "audio", "audio/mpeg", type = AttachmentType.AUDIO)
                        )
                    },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.messages.isEmpty() && !uiState.isStreaming) {
                // Empty state — show different hint based on provider readiness
                EmptyChatState(
                    providerReady = uiState.providerReady,
                    activeModelId = uiState.activeModelId,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // Message list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = 8.dp, bottom = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Existing messages
                    items(
                        items = uiState.messages,
                        key = { it.id }
                    ) { message ->
                        MessageBubble(message = message)
                    }

                    // Streaming text (in-progress assistant response)
                    if (uiState.isStreaming && uiState.streamingText.isNotEmpty()) {
                        item(key = "streaming") {
                            StreamingText(
                                text = uiState.streamingText,
                                step = uiState.currentStep,
                                totalSteps = uiState.totalSteps
                            )
                        }
                    }

                    // Active tool calls
                    if (uiState.activeToolCalls.isNotEmpty()) {
                        items(
                            items = uiState.activeToolCalls.entries.toList(),
                            key = { it.key }
                        ) { (callId, toolCallState) ->
                            ToolCallCard(
                                toolName = toolCallState.name,
                                arguments = toolCallState.arguments,
                                result = toolCallState.result,
                                isError = toolCallState.isError,
                                isExpanded = toolCallState.isExpanded,
                                onToggleExpand = { viewModel.toggleToolCallExpanded(callId) }
                            )
                        }
                    }
                }
            }

            // Error snackbar
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text(error, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun EmptyChatState(
    providerReady: Boolean,
    activeModelId: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubble,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(16.dp))
        if (providerReady) {
            Text(
                text = "开始新对话",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "输入消息或添加附件开始",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (activeModelId.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(activeModelId, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(24.dp)
                )
            }
        } else {
            Text(
                text = "请先配置 LLM 提供商",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "前往 设置 → 选择提供商并添加 API 密钥",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
