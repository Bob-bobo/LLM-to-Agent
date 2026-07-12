package com.hermes.agent.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ApiKeyItem(
    providerName: String,
    providerId: String,
    hasKey: Boolean,
    isVisible: Boolean,
    onSetKey: (String) -> Unit,
    onRemoveKey: () -> Unit,
    onToggleVisibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    var keyInput by remember { mutableStateOf("") }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = providerName,
                    style = MaterialTheme.typography.titleSmall
                )
                if (hasKey) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = "Key set",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (hasKey) {
                // Show masked key with visibility toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isVisible) "sk-***" else "••••••••",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isVisible) "Hide" else "Show"
                        )
                    }
                    TextButton(onClick = onRemoveKey) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                // Key input
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-...") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(
                        onClick = {
                            if (keyInput.isNotBlank()) {
                                onSetKey(keyInput)
                                keyInput = ""
                            }
                        },
                        enabled = keyInput.isNotBlank()
                    ) {
                        Text("✓")
                    }
                }
            }
        }
    }
}
