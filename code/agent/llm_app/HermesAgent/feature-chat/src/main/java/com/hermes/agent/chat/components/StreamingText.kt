package com.hermes.agent.chat.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hermes.agent.core.common.theme.AssistantBubbleShape
import com.hermes.agent.core.common.theme.HermesThemeExt

@Composable
fun StreamingText(
    text: String,
    step: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val colors = HermesThemeExt.colors
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(AssistantBubbleShape)
                .background(colors.assistantBubble)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Step indicator
            if (totalSteps > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { step.toFloat() / totalSteps },
                        modifier = Modifier.width(60.dp).height(2.dp),
                        color = colors.streamingCursor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        text = "Step $step/$totalSteps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Streaming text with blinking cursor
            Text(
                text = text + "▊",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.onAssistantBubble.copy(
                        alpha = if (text.isNotEmpty()) 1f else 0.6f
                    )
                ),
                modifier = Modifier.animateContentSize()
            )
        }
    }
}
