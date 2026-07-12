package com.hermes.agent.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hermes.agent.core.data.model.AgentPlanConfig
import com.hermes.agent.core.data.model.PlanStrategy
import com.hermes.agent.core.data.model.SandboxType

@Entity(tableName = "agent_plans")
data class AgentPlanEntity(
    @PrimaryKey val id: String = "default",
    val strategy: String,              // "REACT" or "FUNCTION_CALLING"
    val maxSteps: Int = 10,
    val enabledToolsJson: String,      // JSON array of tool names
    val codeSandbox: String,           // "RHINO", "WEBVIEW", "OFF"
    val reflectionEnabled: Boolean = true,
    val syncVersion: Long = 0
)

fun AgentPlanEntity.toDomain(enabledTools: Set<String>) = AgentPlanConfig(
    id = id,
    strategy = PlanStrategy.valueOf(strategy),
    maxSteps = maxSteps,
    enabledTools = enabledTools,
    codeSandbox = SandboxType.valueOf(codeSandbox),
    reflectionEnabled = reflectionEnabled
)

fun AgentPlanConfig.toEntity(enabledToolsJson: String) = AgentPlanEntity(
    id = id,
    strategy = strategy.name,
    maxSteps = maxSteps,
    enabledToolsJson = enabledToolsJson,
    codeSandbox = codeSandbox.name,
    reflectionEnabled = reflectionEnabled
)
