package com.hermes.agent.core.data.db.converter

import androidx.room.TypeConverter
import com.hermes.agent.core.common.util.HermesJson
import com.hermes.agent.core.data.model.ContentBlock
import com.hermes.agent.core.data.model.ModelInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import java.time.Instant

class Converters {
    // Instant <-> Long (epoch millis)
    @TypeConverter
    fun fromInstant(instant: Instant): Long = instant.toEpochMilli()

    @TypeConverter
    fun toInstant(millis: Long): Instant = Instant.ofEpochMilli(millis)

    // ContentBlock list <-> JSON String
    @TypeConverter
    fun fromContentBlocks(blocks: List<ContentBlock>): String =
        HermesJson.encodeToString(blocks)

    @TypeConverter
    fun toContentBlocks(json: String): List<ContentBlock> =
        try {
            HermesJson.decodeFromString<List<ContentBlock>>(json)
        } catch (e: Exception) {
            // Fallback: treat as plain text
            listOf(ContentBlock.Text(json))
        }

    // ModelInfo list <-> JSON String
    @TypeConverter
    fun fromModelInfos(models: List<ModelInfo>): String =
        HermesJson.encodeToString(models)

    @TypeConverter
    fun toModelInfos(json: String): List<ModelInfo> =
        try {
            HermesJson.decodeFromString<List<ModelInfo>>(json)
        } catch (e: Exception) {
            emptyList()
        }

    // Set<String> <-> JSON String (for enabled tools)
    @TypeConverter
    fun fromStringSet(set: Set<String>): String =
        HermesJson.encodeToString(buildJsonArray {
            set.forEach { add(JsonPrimitive(it)) }
        })

    @TypeConverter
    fun toStringSet(json: String): Set<String> =
        try {
            HermesJson.decodeFromString<JsonArray>(json)
                .mapNotNull { (it as? JsonPrimitive)?.content }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
}
