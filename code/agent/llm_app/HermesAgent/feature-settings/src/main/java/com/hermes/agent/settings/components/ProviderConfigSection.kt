package com.hermes.agent.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermes.agent.core.data.model.ModelInfo
import com.hermes.agent.core.data.model.ProviderConfig
import com.hermes.agent.core.data.model.ProviderType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderConfigSection(
    providers: List<ProviderConfig>,
    activeProviderId: String,
    onSelectProvider: (String) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onAddCustom: (name: String, url: String, models: List<ModelInfo>) -> Unit,
    onUpdateProvider: (ProviderConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<ProviderConfig?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Provider chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            providers.forEach { provider ->
                val isSelected = provider.id == activeProviderId
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectProvider(provider.id) },
                    label = { Text(provider.displayName) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null,
                    trailingIcon = if (provider.type == ProviderType.CUSTOM) {
                        {
                            IconButton(
                                onClick = { onDeleteProvider(provider.id) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    } else null
                )
            }

            // Add custom button
            FilterChip(
                selected = false,
                onClick = { showAddDialog = true },
                label = { Text("+ 自定义") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add",
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        // Selected provider info with edit button
        val selectedProvider = providers.find { it.id == activeProviderId }
        if (selectedProvider != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "已选择: ${selectedProvider.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (selectedProvider.models.isNotEmpty()) {
                            Text(
                                text = "${selectedProvider.models.size} 个模型可用",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        } else {
                            Text(
                                text = "⚠ 未配置模型，点击编辑添加",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    // Edit button for custom providers or providers with no models
                    if (selectedProvider.type == ProviderType.CUSTOM || selectedProvider.models.isEmpty()) {
                        IconButton(onClick = { showEditDialog = selectedProvider }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "编辑",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "请选择一个提供商",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Add custom provider dialog (with model configuration)
    if (showAddDialog) {
        AddCustomProviderDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url, models ->
                onAddCustom(name, url, models)
                showAddDialog = false
            }
        )
    }

    // Edit provider models dialog
    showEditDialog?.let { provider ->
        EditProviderModelsDialog(
            provider = provider,
            onDismiss = { showEditDialog = null },
            onSave = { updatedProvider ->
                onUpdateProvider(updatedProvider)
                showEditDialog = null
            }
        )
    }
}

/**
 * Enhanced "Add Custom Provider" dialog with model name configuration.
 */
@Composable
private fun AddCustomProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, models: List<ModelInfo>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf("") }
    var models by remember { mutableStateOf(listOf<ModelInfo>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义 API") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("接口名称") },
                    placeholder = { Text("My API") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Text(
                    "模型配置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "添加该接口支持的模型名称（如 gpt-4o、deepseek-chat）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Model input row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = { Text("模型 ID") },
                        placeholder = { Text("model-name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(
                        onClick = {
                            if (modelInput.isNotBlank() && models.none { it.id == modelInput.trim() }) {
                                models = models + ModelInfo(
                                    id = modelInput.trim(),
                                    displayName = modelInput.trim()
                                )
                                modelInput = ""
                            }
                        },
                        enabled = modelInput.isNotBlank(),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加模型", modifier = Modifier.size(20.dp))
                    }
                }

                // Model list
                if (models.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(models) { index, model ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    model.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { models = models.filterIndexed { i, _ -> i != index } },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "暂未添加模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, url, models) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * Edit provider models dialog - allows adding/removing models for any provider.
 */
@Composable
private fun EditProviderModelsDialog(
    provider: ProviderConfig,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig) -> Unit
) {
    var models by remember { mutableStateOf(provider.models) }
    var modelInput by remember { mutableStateOf("") }
    var displayNameInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑 ${provider.displayName}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                provider.baseUrl?.let { baseUrl ->
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {},
                        label = { Text("Base URL") },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                Text(
                    "模型列表 (${models.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // Add model row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = { Text("模型 ID") },
                        placeholder = { Text("model-name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = displayNameInput,
                        onValueChange = { displayNameInput = it },
                        label = { Text("显示名") },
                        placeholder = { Text("可选") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(
                        onClick = {
                            if (modelInput.isNotBlank() && models.none { it.id == modelInput.trim() }) {
                                models = models + ModelInfo(
                                    id = modelInput.trim(),
                                    displayName = displayNameInput.ifBlank { modelInput.trim() }
                                )
                                modelInput = ""
                                displayNameInput = ""
                            }
                        },
                        enabled = modelInput.isNotBlank(),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加", modifier = Modifier.size(20.dp))
                    }
                }

                // Existing models
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(models) { index, model ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.id, style = MaterialTheme.typography.bodySmall)
                                if (model.displayName != model.id) {
                                    Text(
                                        model.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { models = models.filterIndexed { i, _ -> i != index } },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(provider.copy(models = models)) }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
