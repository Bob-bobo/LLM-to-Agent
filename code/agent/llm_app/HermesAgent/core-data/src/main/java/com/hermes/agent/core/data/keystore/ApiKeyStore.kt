package com.hermes.agent.core.data.keystore

/**
 * Interface for storing and retrieving API keys securely.
 */
interface ApiKeyStore {
    fun getKey(providerId: String): String?
    fun setKey(providerId: String, key: String)
    fun removeKey(providerId: String)
    fun hasKey(providerId: String): Boolean
    fun getAllProviderIds(): Set<String>
}
