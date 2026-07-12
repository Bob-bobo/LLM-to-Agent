package com.hermes.agent.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hermes.agent.core.common.theme.HermesThemeExt

@Composable
fun ToolCallCard(
    toolName: String,
    arguments: String,
    result: String?,
    isError: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HermesThemeExt.colors

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = colors.toolCallBackground
        ),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: tool name + status icon + expand button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status icon
                    if (result != null) {
                        Icon(
                            imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                            contentDescription = if (isError) "Error" else "Success",
                            tint = if (isError) MaterialTheme.colorScheme.error else colors.toolCallBorder,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.toolCallBorder
                        )
                    }

                    Text(
                        text = "🔧 $toolName",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Expand/collapse icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded content
            if (isExpanded) {
                Spacer(Modifier.height(8.dp))

                // Arguments
                if (arguments.isNotEmpty()) {
                    Text(
                        text = "Input:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = arguments,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp)
                    )
                }

                // Result
                if (result != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Result:",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isError) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (isError) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
