package com.hermes.agent.core.data.repository

import com.hermes.agent.core.common.util.HermesJson
import com.hermes.agent.core.data.db.dao.ConversationDao
import com.hermes.agent.core.data.db.dao.MessageDao
import com.hermes.agent.core.data.db.entity.ConversationEntity
import com.hermes.agent.core.data.db.entity.MessageEntity
import com.hermes.agent.core.data.db.entity.toDomain
import com.hermes.agent.core.data.db.entity.toEntity
import com.hermes.agent.core.data.model.ChatMessage
import com.hermes.agent.core.data.model.ContentBlock
import com.hermes.agent.core.data.model.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    // ========== Conversations ==========

    fun getAllConversations(): Flow<List<Conversation>> =
        conversationDao.getAll().map { list -> list.map { it.toDomain() } }

    suspend fun getConversation(id: Long): Conversation? =
        conversationDao.getById(id)?.toDomain()

    fun observeConversation(id: Long): Flow<Conversation> =
        conversationDao.observeById(id).map { it.toDomain() }

    suspend fun createConversation(
        title: String,
        providerId: String,
        modelId: String
    ): Long = conversationDao.insert(
        ConversationEntity(
            title = title,
            providerId = providerId,
            modelId = modelId
        )
    )

    suspend fun updateTitle(id: Long, title: String) =
        conversationDao.updateTitle(id, title)

    suspend fun togglePin(id: Long, isPinned: Boolean) =
        conversationDao.updatePinned(id, isPinned)

    suspend fun deleteConversation(id: Long) =
        conversationDao.deleteById(id)

    // ========== Messages ==========

    fun getMessages(conversationId: Long): Flow<List<ChatMessage>> =
        messageDao.getByConversation(conversationId).map { list ->
            list.map { entity ->
                val contentBlocks = parseContentBlocks(entity.contentJson)
                entity.toDomain(contentBlocks)
            }
        }

    suspend fun getMessageList(conversationId: Long): List<ChatMessage> =
        messageDao.getByConversationList(conversationId).map { entity ->
            val contentBlocks = parseContentBlocks(entity.contentJson)
            entity.toDomain(contentBlocks)
        }

    suspend fun addMessage(message: ChatMessage): Long {
        val contentJson = HermesJson.encodeToString(message.content)
        return messageDao.insert(message.toEntity(contentJson))
    }

    suspend fun updateMessage(message: ChatMessage) {
        val contentJson = HermesJson.encodeToString(message.content)
        messageDao.update(message.toEntity(contentJson))
    }

    suspend fun deleteMessages(conversationId: Long) =
        messageDao.deleteByConversation(conversationId)

    suspend fun getMessageCount(conversationId: Long): Int =
        messageDao.countByConversation(conversationId)

    // ========== Helpers ==========

    private fun parseContentBlocks(json: String): List<ContentBlock> {
        return try {
            HermesJson.decodeFromString<List<ContentBlock>>(json)
        } catch (e: Exception) {
            listOf(ContentBlock.Text(json))
        }
    }
}
