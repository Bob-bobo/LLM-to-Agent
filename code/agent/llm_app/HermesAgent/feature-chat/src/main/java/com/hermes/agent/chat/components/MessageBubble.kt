package com.hermes.agent.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hermes.agent.core.common.theme.HermesThemeExt
import com.hermes.agent.core.common.theme.UserBubbleShape
import com.hermes.agent.core.common.theme.AssistantBubbleShape
import com.hermes.agent.core.data.model.ChatMessage
import com.hermes.agent.core.data.model.ContentBlock
import com.hermes.agent.core.data.model.MessageRole

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val colors = HermesThemeExt.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .widthIn(max = 320.dp)
                .clip(
                    if (isUser) UserBubbleShape else AssistantBubbleShape
                )
                .background(
                    if (isUser) colors.userBubble else colors.assistantBubble
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (block in message.content) {
                when (block) {
                    is ContentBlock.Text -> {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUser) colors.onUserBubble else colors.onAssistantBubble,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    is ContentBlock.ToolUse -> {
                        ToolCallCard(
                            toolName = block.name,
                            arguments = block.input,
                            result = null,
                            isError = false,
                            isExpanded = false,
                            onToggleExpand = {}
                        )
                    }
                    is ContentBlock.ToolResult -> {
                        Text(
                            text = block.output,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (block.isError)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    is ContentBlock.Image -> {
                        // Image thumbnail
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "📷 Image",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is ContentBlock.File -> {
                        Text(
                            text = "📎 ${block.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) colors.onUserBubble else colors.onAssistantBubble
                        )
                    }
                    is ContentBlock.Audio -> {
                        Text(
                            text = "🎵 Audio",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) colors.onUserBubble else colors.onAssistantBubble
                        )
                    }
                    is ContentBlock.Video -> {
                        Text(
                            text = "🎬 Video",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) colors.onUserBubble else colors.onAssistantBubble
                        )
                    }
                }
            }
        }
    }
}
