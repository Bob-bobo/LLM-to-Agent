package com.hermes.agent.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hermes.agent.core.data.db.entity.ProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers WHERE isActive = 1 ORDER BY sortOrder ASC")
    fun getActive(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun getById(id: String): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: ProviderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(providers: List<ProviderEntity>)

    @Update
    suspend fun update(provider: ProviderEntity)

    @Delete
    suspend fun delete(provider: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun deleteById(id: String)
}
