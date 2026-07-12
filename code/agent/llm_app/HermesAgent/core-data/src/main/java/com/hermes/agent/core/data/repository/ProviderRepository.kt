package com.hermes.agent.core.data.repository

import com.hermes.agent.core.common.util.HermesJson
import com.hermes.agent.core.data.db.dao.ProviderDao
import com.hermes.agent.core.data.db.entity.toDomain
import com.hermes.agent.core.data.db.entity.toEntity
import com.hermes.agent.core.data.model.ModelInfo
import com.hermes.agent.core.data.model.ProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepository @Inject constructor(
    private val providerDao: ProviderDao
) {
    fun getActiveProviders(): Flow<List<ProviderConfig>> =
        providerDao.getActive().map { list -> list.map { it.toDomain(parseModels(it.modelsJson)) } }

    fun getAllProviders(): Flow<List<ProviderConfig>> =
        providerDao.getAll().map { list -> list.map { it.toDomain(parseModels(it.modelsJson)) } }

    suspend fun getProvider(id: String): ProviderConfig? {
        val entity = providerDao.getById(id) ?: return null
        return entity.toDomain(parseModels(entity.modelsJson))
    }

    suspend fun upsertProvider(provider: ProviderConfig) {
        val modelsJson = HermesJson.encodeToString(provider.models)
        providerDao.insert(provider.toEntity(modelsJson))
    }

    suspend fun upsertProviders(providers: List<ProviderConfig>) {
        val entities = providers.map { provider ->
            val modelsJson = HermesJson.encodeToString(provider.models)
            provider.toEntity(modelsJson)
        }
        providerDao.insertAll(entities)
    }

    suspend fun deleteProvider(id: String) =
        providerDao.deleteById(id)

    private fun parseModels(json: String): List<ModelInfo> {
        return try {
            HermesJson.decodeFromString<List<ModelInfo>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
