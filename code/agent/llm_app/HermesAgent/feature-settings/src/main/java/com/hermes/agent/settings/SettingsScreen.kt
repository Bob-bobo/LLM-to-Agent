package com.hermes.agent.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hermes.agent.core.data.model.*
import com.hermes.agent.settings.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ========== LLM Providers Section ==========
        item {
            SectionHeader(title = "LLM 提供商", icon = Icons.Default.Cloud)
        }

        item {
            ProviderConfigSection(
                providers = uiState.providers,
                activeProviderId = uiState.activeProviderId,
                onSelectProvider = { viewModel.setActiveProvider(it) },
                onDeleteProvider = { viewModel.deleteProvider(it) },
                onAddCustom = { name, url, models -> viewModel.addCustomProvider(name, url, models) },
                onUpdateProvider = { viewModel.updateProvider(it) }
            )
        }

        // ========== Model Selection Section ==========
        item {
            SectionHeader(title = "模型选择", icon = Icons.Default.Psychology)
        }

        item {
            ModelSelectionSection(
                providers = uiState.providers,
                activeProviderId = uiState.activeProviderId,
                activeModelId = uiState.activeModelId,
                onSelectProvider = { viewModel.setActiveProvider(it) },
                onSelectModel = { viewModel.setActiveModel(it) }
            )
        }

        // ========== API Keys Section ==========
        item {
            SectionHeader(title = "API 密钥", icon = Icons.Default.Key)
        }

        items(
            count = uiState.providers.size,
            key = { i -> uiState.providers[i].id }
        ) { index ->
            val provider = uiState.providers[index]
            ApiKeyItem(
                providerName = provider.displayName,
                providerId = provider.id,
                hasKey = viewModel.hasApiKey(provider.id),
                isVisible = uiState.apiKeyVisibility[provider.id] ?: false,
                onSetKey = { viewModel.setApiKey(provider.id, it) },
                onRemoveKey = { viewModel.removeApiKey(provider.id) },
                onToggleVisibility = { viewModel.toggleApiKeyVisibility(provider.id) }
            )
        }

        // ========== Agent Plan Section ==========
        item {
            SectionHeader(title = "Agent 计划", icon = Icons.Default.SmartToy)
        }

        item {
            AgentPlanSection(
                planConfig = uiState.agentPlan,
                onStrategyChange = { viewModel.setStrategy(it) },
                onMaxStepsChange = { viewModel.setMaxSteps(it) },
                onToggleTool = { name, enabled -> viewModel.toggleTool(name, enabled) },
                onSandboxChange = { viewModel.setCodeSandbox(it) },
                onReflectionChange = { viewModel.setReflectionEnabled(it) }
            )
        }

        // ========== Cloud Sync Section ==========
        item {
            SectionHeader(title = "数据存储", icon = Icons.Default.Storage)
        }

        item {
            SyncSection(
                webDavUrl = uiState.webDavUrl,
                username = uiState.webDavUsername,
                password = uiState.webDavPassword,
                autoSync = uiState.autoSync,
                isSyncing = uiState.isSyncing,
                providerCount = uiState.providers.size,
                apiKeyCount = viewModel.getApiKeyCount(),
                conversationCount = uiState.conversationCount,
                onUrlChange = { viewModel.setWebDavUrl(it) },
                onUsernameChange = { viewModel.setWebDavUsername(it) },
                onPasswordChange = { viewModel.setWebDavPassword(it) },
                onAutoSyncChange = { viewModel.setAutoSync(it) },
                onSyncNow = { viewModel.syncNow() }
            )
        }

        // ========== About Section ==========
        item {
            SectionHeader(title = "关于", icon = Icons.Default.Info)
        }

        item {
            AboutSection()
        }

        // Bottom spacing
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
