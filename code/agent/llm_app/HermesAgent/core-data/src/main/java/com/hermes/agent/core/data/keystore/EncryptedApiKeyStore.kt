package com.hermes.agent.core.data.keystore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted API key storage using Android Keystore + EncryptedSharedPreferences.
 * API keys are encrypted with AES256_GCM and stored on disk.
 *
 * All methods are wrapped in try-catch to prevent crashes when the
 * encrypted prefs fail to initialize (e.g., master key corruption,
 * device lock change, keystore hardware issue).
 */
@Singleton
class EncryptedApiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) : ApiKeyStore {

    private var _prefs: SharedPreferences? = null

    private val prefs: SharedPreferences?
        get() {
            if (_prefs == null) {
                _prefs = try {
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    EncryptedSharedPreferences.create(
                        context,
                        "hermes_api_keys",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to initialize EncryptedSharedPreferences")
                    null
                }
            }
            return _prefs
        }

    override fun getKey(providerId: String): String? = try {
        prefs?.getString(providerId, null)
    } catch (e: Exception) {
        Timber.e(e, "Failed to read API key for %s", providerId)
        null
    }

    override fun setKey(providerId: String, key: String) = try {
        prefs?.edit()?.putString(providerId, key)?.apply()
    } catch (e: Exception) {
        Timber.e(e, "Failed to store API key for %s", providerId)
    }

    override fun removeKey(providerId: String) = try {
        prefs?.edit()?.remove(providerId)?.apply()
    } catch (e: Exception) {
        Timber.e(e, "Failed to remove API key for %s", providerId)
    }

    override fun hasKey(providerId: String): Boolean = try {
        prefs?.contains(providerId) ?: false
    } catch (e: Exception) {
        Timber.e(e, "Failed to check API key for %s", providerId)
        false
    }

    override fun getAllProviderIds(): Set<String> = try {
        prefs?.all?.keys ?: emptySet()
    } catch (e: Exception) {
        Timber.e(e, "Failed to get all provider IDs")
        emptySet()
    }
}
