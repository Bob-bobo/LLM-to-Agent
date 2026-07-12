package com.hermes.agent.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hermes.agent.core.data.model.Conversation
import java.time.Instant

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val providerId: String,
    val modelId: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val isPinned: Boolean = false,
    val syncVersion: Long = 0
) {
    fun toDomain() = Conversation(
        id = id,
        title = title,
        providerId = providerId,
        modelId = modelId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isPinned = isPinned,
        syncVersion = syncVersion
    )
}

fun Conversation.toEntity() = ConversationEntity(
    id = id,
    title = title,
    providerId = providerId,
    modelId = modelId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isPinned = isPinned,
    syncVersion = syncVersion
)
