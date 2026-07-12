package com.hermes.agent.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hermes.agent.core.data.db.converter.Converters
import com.hermes.agent.core.data.db.dao.AgentPlanDao
import com.hermes.agent.core.data.db.dao.ConversationDao
import com.hermes.agent.core.data.db.dao.MessageDao
import com.hermes.agent.core.data.db.dao.ProviderDao
import com.hermes.agent.core.data.db.entity.AgentPlanEntity
import com.hermes.agent.core.data.db.entity.ConversationEntity
import com.hermes.agent.core.data.db.entity.MessageEntity
import com.hermes.agent.core.data.db.entity.ProviderEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ProviderEntity::class,
        AgentPlanEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HermesDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun providerDao(): ProviderDao
    abstract fun agentPlanDao(): AgentPlanDao

    companion object {
        const val DATABASE_NAME = "hermes_agent.db"
    }
}
