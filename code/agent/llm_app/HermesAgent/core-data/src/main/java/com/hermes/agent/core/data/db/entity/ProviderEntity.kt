package com.hermes.agent.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hermes.agent.core.data.model.ProviderConfig
import com.hermes.agent.core.data.model.ProviderType

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val type: String,              // ProviderType name
    val baseUrl: String?,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val modelsJson: String = "[]", // JSON-serialized List<ModelInfo>
    val syncVersion: Long = 0
)

fun ProviderEntity.toDomain(models: List<com.hermes.agent.core.data.model.ModelInfo>) = ProviderConfig(
    id = id,
    displayName = displayName,
    type = ProviderType.valueOf(type),
    baseUrl = baseUrl,
    isActive = isActive,
    sortOrder = sortOrder,
    models = models
)

fun ProviderConfig.toEntity(modelsJson: String) = ProviderEntity(
    id = id,
    displayName = displayName,
    type = type.name,
    baseUrl = baseUrl,
    isActive = isActive,
    sortOrder = sortOrder,
    modelsJson = modelsJson
)
