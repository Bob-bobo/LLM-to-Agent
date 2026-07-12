package com.hermes.agent.core.common.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Serializer for java.time.Instant using epoch milliseconds.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilli())
    override fun deserialize(decoder: Decoder): Instant = Instant.ofEpochMilli(decoder.decodeLong())
}

/**
 * Shared Json instance with lenient configuration for parsing LLM responses.
 */
val HermesJson = Json {
    ignoreUnknownKeys = true       // LLM APIs may add new fields
    isLenient = true               // Tolerate minor formatting issues
    encodeDefaults = true          // Include default values in serialization
    coerceInputValues = true       // Coerce null inputs to defaults where possible
    prettyPrint = false            // Compact output for network
}

/**
 * Strict Json instance for internal data (Room entity serialization, etc.)
 */
val StrictJson = Json {
    ignoreUnknownKeys = false
    isLenient = false
    encodeDefaults = true
    coerceInputValues = false
    prettyPrint = false
}
