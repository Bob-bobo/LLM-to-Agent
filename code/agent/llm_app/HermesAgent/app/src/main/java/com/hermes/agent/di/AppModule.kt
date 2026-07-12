package com.hermes.agent.di

import android.content.Context
import androidx.room.Room
import com.hermes.agent.core.data.db.HermesDatabase
import com.hermes.agent.core.data.db.dao.AgentPlanDao
import com.hermes.agent.core.data.db.dao.ConversationDao
import com.hermes.agent.core.data.db.dao.MessageDao
import com.hermes.agent.core.data.db.dao.ProviderDao
import com.hermes.agent.core.data.model.DefaultProviders
import com.hermes.agent.core.data.prefs.UserPrefs
import com.hermes.agent.core.data.repository.AgentPlanRepository
import com.hermes.agent.core.data.repository.ProviderRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): HermesDatabase = Room.databaseBuilder(
        context,
        HermesDatabase::class.java,
        HermesDatabase.DATABASE_NAME
    ).build()

    @Provides
    fun provideConversationDao(db: HermesDatabase): ConversationDao =
        db.conversationDao()

    @Provides
    fun provideMessageDao(db: HermesDatabase): MessageDao =
        db.messageDao()

    @Provides
    fun provideProviderDao(db: HermesDatabase): ProviderDao =
        db.providerDao()

    @Provides
    fun provideAgentPlanDao(db: HermesDatabase): AgentPlanDao =
        db.agentPlanDao()
}

/**
 * Seeds default data (providers, agent plan) into the database on first launch.
 * Called from HermesApp.onCreate().
 */
class DatabaseSeeder(
    private val providerRepository: ProviderRepository,
    private val agentPlanRepository: AgentPlanRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun seedIfNeeded() {
        scope.launch {
            try {
                seedDefaultProviders()
                seedDefaultAgentPlan()
            } catch (e: Exception) {
                // Don't crash on seed failure - just log
                Timber.e(e, "Failed to seed data")
            }
        }
    }

    private suspend fun seedDefaultProviders() {
        try {
            // Check if providers already exist (take first emission from Flow)
            val existingProviders = providerRepository.getAllProviders().first()

            if (existingProviders.isEmpty()) {
                // Seed default providers
                providerRepository.upsertProviders(DefaultProviders.ALL)
                Timber.d("Seeded ${DefaultProviders.ALL.size} default providers")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to seed providers")
        }
    }

    private suspend fun seedDefaultAgentPlan() {
        val existing = agentPlanRepository.getPlan("default")
        if (existing == null) {
            agentPlanRepository.savePlan(com.hermes.agent.core.data.model.AgentPlanConfig())
            Timber.d("Seeded default agent plan")
        }
    }
}
