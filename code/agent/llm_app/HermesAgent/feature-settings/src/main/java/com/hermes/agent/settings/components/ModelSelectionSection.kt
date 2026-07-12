package com.hermes.agent.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hermes.agent.core.data.model.ModelInfo
import com.hermes.agent.core.data.model.ProviderConfig

/**
 * Model selection section with provider and model dropdowns.
 * Uses simple dialogs instead of ExposedDropdownMenu for better compatibility.
 */
@Composable
fun ModelSelectionSection(
    providers: List<ProviderConfig>,
    activeProviderId: String,
    activeModelId: String,
    onSelectProvider: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeProvider = providers.find { it.id == activeProviderId }
    val selectedModel = activeProvider?.models?.find { it.id == activeModelId }

    var showProviderDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Provider selector - clickable field that opens a dialog
        OutlinedTextField(
            value = activeProvider?.displayName ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = true,
            label = { Text("提供商") },
            placeholder = { Text("选择提供商") },
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = false,
                    role = Role.Button,
                    onClick = { showProviderDialog = true }
                ),
            trailingIcon = {
                IconButton(onClick = { showProviderDialog = true }) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "选择"
                    )
                }
            },
            singleLine = true
        )

        // Model selector
        OutlinedTextField(
            value = selectedModel?.displayName ?: activeModelId,
            onValueChange = {},
            readOnly = true,
            enabled = activeProvider != null,
            label = { Text("模型") },
            placeholder = { Text("选择模型") },
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = false,
                    role = Role.Button,
                    onClick = { if (activeProvider != null) showModelDialog = true }
                ),
            trailingIcon = {
                if (activeProvider != null) {
                    IconButton(onClick = { showModelDialog = true }) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "选择"
                        )
                    }
                }
            },
            singleLine = true
        )
    }

    // Provider selection dialog
    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = { Text("选择提供商") },
            text = {
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    providers.forEach { provider ->
                        val isSelected = provider.id == activeProviderId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelectProvider(provider.id)
                                        showProviderDialog = false
                                    }
                                )
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // Model selection dialog
    if (showModelDialog && activeProvider != null) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("选择模型 - ${activeProvider.displayName}") },
            text = {
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    activeProvider.models.forEach { model ->
                        val isSelected = model.id == activeModelId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelectModel(model.id)
                                        showModelDialog = false
                                    }
                                )
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Column {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (model.supportsToolCalling) {
                                        Text(
                                            "🔧 工具调用",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (model.supportsVision) {
                                        Text(
                                            "👁 图片",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Text(
                                        "${model.contextWindow / 1000}K",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}
