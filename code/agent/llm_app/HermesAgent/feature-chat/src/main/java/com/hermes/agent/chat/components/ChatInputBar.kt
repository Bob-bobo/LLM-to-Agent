package com.hermes.agent.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isStreaming: Boolean,
    onAttachImage: () -> Unit,
    onAttachFile: () -> Unit,
    onAttachVideo: () -> Unit,
    onAttachAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAttachMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Attach button
            Box {
                FilledIconButton(
                    onClick = { showAttachMenu = !showAttachMenu },
                    modifier = Modifier.size(40.dp),
                    enabled = !isStreaming
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Attach",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Attachment dropdown menu
                DropdownMenu(
                    expanded = showAttachMenu,
                    onDismissRequest = { showAttachMenu = false }
                ) {
                    AttachmentMenuItem(
                        icon = Icons.Default.Image,
                        label = "图片",
                        onClick = { showAttachMenu = false; onAttachImage() }
                    )
                    AttachmentMenuItem(
                        icon = Icons.Default.AttachFile,
                        label = "文件",
                        onClick = { showAttachMenu = false; onAttachFile() }
                    )
                    AttachmentMenuItem(
                        icon = Icons.Default.Movie,
                        label = "视频",
                        onClick = { showAttachMenu = false; onAttachVideo() }
                    )
                    AttachmentMenuItem(
                        icon = Icons.Default.Mic,
                        label = "音频",
                        onClick = { showAttachMenu = false; onAttachAudio() }
                    )
                }
            }

            // Text input field
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 120.dp),
                placeholder = {
                    Text(
                        if (isStreaming) "思考中..." else "输入消息...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                shape = MaterialTheme.shapes.large,
                enabled = !isStreaming,
                textStyle = MaterialTheme.typography.bodyLarge
            )

            // Send button
            FilledIconButton(
                onClick = onSend,
                modifier = Modifier.size(40.dp),
                enabled = value.isNotBlank() && !isStreaming
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) },
        onClick = onClick
    )
}
