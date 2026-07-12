package com.hermes.agent.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hermes.agent.core.data.db.entity.AgentPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentPlanDao {
    @Query("SELECT * FROM agent_plans WHERE id = :id")
    suspend fun getById(id: String = "default"): AgentPlanEntity?

    @Query("SELECT * FROM agent_plans WHERE id = :id")
    fun observeById(id: String = "default"): Flow<AgentPlanEntity?>

    @Query("SELECT * FROM agent_plans")
    fun getAll(): Flow<List<AgentPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: AgentPlanEntity)

    @Update
    suspend fun update(plan: AgentPlanEntity)

    @Query("DELETE FROM agent_plans WHERE id = :id")
    suspend fun deleteById(id: String)
}
