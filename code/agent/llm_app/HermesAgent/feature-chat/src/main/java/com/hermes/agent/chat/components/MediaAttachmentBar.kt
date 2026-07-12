package com.hermes.agent.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hermes.agent.chat.AttachmentType
import com.hermes.agent.chat.AttachmentUiState

@Composable
fun MediaAttachmentBar(
    attachments: List<AttachmentUiState>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 1.dp
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(attachments) { index, attachment ->
                AttachmentChip(
                    attachment = attachment,
                    onRemove = { onRemove(index) }
                )
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: AttachmentUiState,
    onRemove: () -> Unit
) {
    val icon = when (attachment.type) {
        AttachmentType.IMAGE -> Icons.Default.Image
        AttachmentType.FILE -> Icons.Default.AttachFile
        AttachmentType.VIDEO -> Icons.Default.Movie
        AttachmentType.AUDIO -> Icons.Default.Mic
    }

    val label = when (attachment.type) {
        AttachmentType.IMAGE -> "图片"
        AttachmentType.FILE -> "文件"
        AttachmentType.VIDEO -> "视频"
        AttachmentType.AUDIO -> "音频"
    }

    InputChip(
        selected = true,
        onClick = {},
        label = { Text(attachment.name.ifEmpty { label }, maxLines = 1) },
        leadingIcon = {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(18.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(14.dp)
                )
            }
        },
        modifier = Modifier.height(32.dp)
    )
}
