package com.hermes.agent.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Data storage section - shows local storage status and optional WebDAV cloud sync.
 * Makes it clear that all data is saved locally by default.
 */
@Composable
fun SyncSection(
    webDavUrl: String,
    username: String,
    password: String,
    autoSync: Boolean,
    isSyncing: Boolean,
    providerCount: Int,
    apiKeyCount: Int,
    conversationCount: Int,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ========== Local Storage Status ==========
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "本地存储 (已自动保存)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    "所有配置、API 密钥和对话记录均保存在本机，使用 AES256 加密存储密钥。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Storage stats
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StorageStatItem(label = "提供商", value = "$providerCount 个")
                    StorageStatItem(label = "API 密钥", value = "$apiKeyCount 个")
                    StorageStatItem(label = "对话", value = "$conversationCount 条")
                }
            }
        }

        // ========== Cloud Sync (Optional) ==========
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "云同步 (可选)",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Text(
                    "配置 WebDAV 后可将数据同步到云端，实现跨设备共享。不配置则仅使用本地存储。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (webDavUrl.isBlank()) {
                    // Collapsed state - just show hint
                    Text(
                        "未配置 — 点击下方输入 WebDAV 地址启用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                OutlinedTextField(
                    value = webDavUrl,
                    onValueChange = onUrlChange,
                    label = { Text("WebDAV URL") },
                    placeholder = { Text("https://dav.example.com/hermes/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Uri
                    )
                )

                if (webDavUrl.isNotBlank()) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Switch(
                            checked = autoSync,
                            onCheckedChange = onAutoSyncChange
                        )
                        Text("自动同步", style = MaterialTheme.typography.bodyMedium)
                    }

                    Button(
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("同步中...")
                        } else {
                            Text("立即同步")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
