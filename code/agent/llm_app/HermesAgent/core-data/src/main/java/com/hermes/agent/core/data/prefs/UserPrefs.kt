package com.hermes.agent.core.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple SharedPreferences wrapper for persisting user selections
 * that need to survive ViewModel destruction (active provider, active model).
 */
@Singleton
class UserPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        try {
            context.getSharedPreferences("hermes_user_prefs", Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Timber.e(e, "Failed to init UserPrefs")
            // Return an in-memory empty prefs as fallback
            context.getSharedPreferences("hermes_user_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    var activeProviderId: String
        get() = try { prefs.getString(KEY_ACTIVE_PROVIDER, "") ?: "" } catch (_: Exception) { "" }
        set(value) = try { prefs.edit().putString(KEY_ACTIVE_PROVIDER, value).apply() } catch (_: Exception) {}

    var activeModelId: String
        get() = try { prefs.getString(KEY_ACTIVE_MODEL, "") ?: "" } catch (_: Exception) { "" }
        set(value) = try { prefs.edit().putString(KEY_ACTIVE_MODEL, value).apply() } catch (_: Exception) {}

    companion object {
        private const val KEY_ACTIVE_PROVIDER = "active_provider_id"
        private const val KEY_ACTIVE_MODEL = "active_model_id"
    }
}
