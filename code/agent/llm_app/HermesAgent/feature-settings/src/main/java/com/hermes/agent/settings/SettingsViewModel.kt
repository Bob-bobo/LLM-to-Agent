package com.hermes.agent.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.agent.core.data.keystore.ApiKeyStore
import com.hermes.agent.core.data.model.AgentPlanConfig
import com.hermes.agent.core.data.model.ModelInfo
import com.hermes.agent.core.data.model.PlanStrategy
import com.hermes.agent.core.data.model.ProviderConfig
import com.hermes.agent.core.data.model.ProviderType
import com.hermes.agent.core.data.model.SandboxType
import com.hermes.agent.core.data.prefs.UserPrefs
import com.hermes.agent.core.data.repository.AgentPlanRepository
import com.hermes.agent.core.data.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SettingsUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val activeProviderId: String = "",
    val activeModelId: String = "",
    val agentPlan: AgentPlanConfig = AgentPlanConfig(),
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val autoSync: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncTime: String? = null,
    val apiKeyVisibility: Map<String, Boolean> = emptyMap(), // providerId -> visible
    val customEndpointUrl: String = "",
    val customEndpointName: String = "",
    // Local storage stats
    val apiKeyCount: Int = 0,
    val conversationCount: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val providerRepo: ProviderRepository,
    private val agentPlanRepo: AgentPlanRepository,
    private val apiKeyStore: ApiKeyStore,
    private val userPrefs: UserPrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Load persisted active provider/model selection
        val savedProviderId = userPrefs.activeProviderId
        val savedModelId = userPrefs.activeModelId
        if (savedProviderId.isNotEmpty()) {
            _uiState.update {
                it.copy(activeProviderId = savedProviderId, activeModelId = savedModelId)
            }
        }

        // Load providers
        viewModelScope.launch {
            try {
                providerRepo.getAllProviders().collect { providers ->
                    _uiState.update { current ->
                        // If we have a saved providerId but it's not in the current state yet,
                        // keep the saved values. If no saved provider, auto-select the first.
                        val activeId = if (current.activeProviderId.isNotEmpty()) {
                            current.activeProviderId
                        } else if (providers.isNotEmpty()) {
                            providers.first().id
                        } else {
                            ""
                        }
                        val activeModel = if (current.activeModelId.isNotEmpty()) {
                            current.activeModelId
                        } else {
                            providers.find { it.id == activeId }?.models?.firstOrNull()?.id ?: ""
                        }
                        it.copy(
                            providers = providers,
                            activeProviderId = activeId,
                            activeModelId = activeModel
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load providers")
            }
        }

        // Load agent plan
        viewModelScope.launch {
            try {
                agentPlanRepo.observePlan().collect { plan ->
                    _uiState.update { it.copy(agentPlan = plan) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load agent plan")
            }
        }
    }

    // ========== Provider Settings ==========

    fun setActiveProvider(providerId: String) {
        // When switching provider, auto-select the first model and clear old model
        val provider = _uiState.value.providers.find { it.id == providerId }
        val firstModelId = provider?.models?.firstOrNull()?.id ?: ""
        _uiState.update {
            it.copy(
                activeProviderId = providerId,
                activeModelId = firstModelId
            )
        }
        // Persist selection
        userPrefs.activeProviderId = providerId
        userPrefs.activeModelId = firstModelId
    }

    fun setActiveModel(modelId: String) {
        _uiState.update { it.copy(activeModelId = modelId) }
        // Persist selection
        userPrefs.activeModelId = modelId
    }

    fun getApiKey(providerId: String): String? = try {
        apiKeyStore.getKey(providerId)
    } catch (e: Exception) {
        Timber.e(e, "Failed to get API key")
        null
    }

    fun setApiKey(providerId: String, key: String) = try {
        apiKeyStore.setKey(providerId, key)
    } catch (e: Exception) {
        Timber.e(e, "Failed to set API key")
    }

    fun removeApiKey(providerId: String) = try {
        apiKeyStore.removeKey(providerId)
    } catch (e: Exception) {
        Timber.e(e, "Failed to remove API key")
    }

    fun hasApiKey(providerId: String): Boolean = try {
        apiKeyStore.hasKey(providerId)
    } catch (e: Exception) {
        Timber.e(e, "Failed to check API key")
        false
    }

    /** Count how many providers have API keys configured */
    fun getApiKeyCount(): Int = try {
        apiKeyStore.getAllProviderIds().size
    } catch (e: Exception) {
        Timber.e(e, "Failed to count API keys")
        0
    }

    fun toggleApiKeyVisibility(providerId: String) {
        _uiState.update {
            it.copy(apiKeyVisibility = it.apiKeyVisibility + (
                providerId to !(it.apiKeyVisibility[providerId] ?: false)
            ))
        }
    }

    fun addCustomProvider(name: String, baseUrl: String, models: List<ModelInfo> = emptyList()) {
        viewModelScope.launch {
            try {
                val id = "custom_${System.currentTimeMillis()}"
                providerRepo.upsertProvider(ProviderConfig(
                    id = id,
                    displayName = name,
                    type = ProviderType.CUSTOM,
                    baseUrl = baseUrl,
                    models = models
                ))
            } catch (e: Exception) {
                Timber.e(e, "Failed to add custom provider")
            }
        }
    }

    fun updateProvider(provider: ProviderConfig) {
        viewModelScope.launch {
            try {
                providerRepo.upsertProvider(provider)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update provider")
            }
        }
    }

    fun deleteProvider(providerId: String) {
        viewModelScope.launch {
            try {
                apiKeyStore.removeKey(providerId)
                providerRepo.deleteProvider(providerId)
                // If deleting the active provider, clear selection
                if (_uiState.value.activeProviderId == providerId) {
                    val firstRemaining = _uiState.value.providers.firstOrNull { it.id != providerId }
                    _uiState.update {
                        it.copy(
                            activeProviderId = firstRemaining?.id ?: "",
                            activeModelId = firstRemaining?.models?.firstOrNull()?.id ?: ""
                        )
                    }
                    userPrefs.activeProviderId = firstRemaining?.id ?: ""
                    userPrefs.activeModelId = firstRemaining?.models?.firstOrNull()?.id ?: ""
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete provider")
            }
        }
    }

    // ========== Agent Plan Settings ==========

    fun setStrategy(strategy: PlanStrategy) {
        viewModelScope.launch {
            try {
                val current = _uiState.value.agentPlan
                agentPlanRepo.savePlan(current.copy(strategy = strategy))
            } catch (e: Exception) {
                Timber.e(e, "Failed to set strategy")
            }
        }
    }

    fun setMaxSteps(maxSteps: Int) {
        viewModelScope.launch {
            try {
                val current = _uiState.value.agentPlan
                agentPlanRepo.savePlan(current.copy(maxSteps = maxSteps.coerceIn(1, 30)))
            } catch (e: Exception) {
                Timber.e(e, "Failed to set max steps")
            }
        }
    }

    fun toggleTool(toolName: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val current = _uiState.value.agentPlan
                val tools = if (enabled) {
                    current.enabledTools + toolName
                } else {
                    current.enabledTools - toolName
                }
                agentPlanRepo.savePlan(current.copy(enabledTools = tools))
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle tool")
            }
        }
    }

    fun setCodeSandbox(sandbox: SandboxType) {
        viewModelScope.launch {
            try {
                val current = _uiState.value.agentPlan
                agentPlanRepo.savePlan(current.copy(codeSandbox = sandbox))
            } catch (e: Exception) {
                Timber.e(e, "Failed to set code sandbox")
            }
        }
    }

    fun setReflectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val current = _uiState.value.agentPlan
                agentPlanRepo.savePlan(current.copy(reflectionEnabled = enabled))
            } catch (e: Exception) {
                Timber.e(e, "Failed to set reflection")
            }
        }
    }

    // ========== Sync Settings ==========

    fun setWebDavUrl(url: String) = _uiState.update { it.copy(webDavUrl = url) }
    fun setWebDavUsername(username: String) = _uiState.update { it.copy(webDavUsername = username) }
    fun setWebDavPassword(password: String) = _uiState.update { it.copy(webDavPassword = password) }
    fun setAutoSync(enabled: Boolean) = _uiState.update { it.copy(autoSync = enabled) }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            // TODO: Implement sync
            _uiState.update { it.copy(isSyncing = false) }
        }
    }
}
