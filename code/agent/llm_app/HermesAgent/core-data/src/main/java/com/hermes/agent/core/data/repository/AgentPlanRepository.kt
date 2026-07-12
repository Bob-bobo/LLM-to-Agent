package com.hermes.agent.core.data.repository

import com.hermes.agent.core.common.util.HermesJson
import com.hermes.agent.core.data.db.dao.AgentPlanDao
import com.hermes.agent.core.data.db.entity.toDomain
import com.hermes.agent.core.data.db.entity.toEntity
import com.hermes.agent.core.data.model.AgentPlanConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentPlanRepository @Inject constructor(
    private val agentPlanDao: AgentPlanDao
) {
    fun observePlan(id: String = "default"): Flow<AgentPlanConfig> =
        agentPlanDao.observeById(id).map { entity ->
            if (entity != null) {
                val enabledTools = parseEnabledTools(entity.enabledToolsJson)
                entity.toDomain(enabledTools)
            } else {
                AgentPlanConfig() // Return default config when no plan exists
            }
        }

    suspend fun getPlan(id: String = "default"): AgentPlanConfig? {
        val entity = agentPlanDao.getById(id) ?: return null
        val enabledTools = parseEnabledTools(entity.enabledToolsJson)
        return entity.toDomain(enabledTools)
    }

    suspend fun getPlanOrDefault(id: String = "default"): AgentPlanConfig {
        return getPlan(id) ?: AgentPlanConfig()
    }

    suspend fun savePlan(plan: AgentPlanConfig) {
        val enabledToolsJson = HermesJson.encodeToString(plan.enabledTools.toList())
        agentPlanDao.insert(plan.toEntity(enabledToolsJson))
    }

    private fun parseEnabledTools(json: String): Set<String> {
        return try {
            HermesJson.decodeFromString<List<String>>(json).toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
}
