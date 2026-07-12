package com.hermes.agent.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hermes.agent.core.data.model.ChatMessage
import com.hermes.agent.core.data.model.ContentBlock
import com.hermes.agent.core.data.model.MessageRole
import java.time.Instant

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,              // "USER", "ASSISTANT", "SYSTEM", "TOOL"
    val contentJson: String,       // JSON-serialized List<ContentBlock>
    val tokenCount: Int? = null,
    val createdAt: Instant = Instant.now(),
    val syncVersion: Long = 0
)

fun MessageEntity.toDomain(contentBlocks: List<ContentBlock>) = ChatMessage(
    id = id,
    conversationId = conversationId,
    role = MessageRole.valueOf(role),
    content = contentBlocks,
    tokenCount = tokenCount,
    createdAt = createdAt,
    syncVersion = syncVersion
)

fun ChatMessage.toEntity(contentJson: String) = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name,
    contentJson = contentJson,
    tokenCount = tokenCount,
    createdAt = createdAt,
    syncVersion = syncVersion
)
