package com.hermes.agent

import android.app.Application
import com.hermes.agent.core.data.repository.AgentPlanRepository
import com.hermes.agent.core.data.repository.ProviderRepository
import com.hermes.agent.di.DatabaseSeeder
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class HermesApp : Application() {

    @Inject lateinit var providerRepository: ProviderRepository
    @Inject lateinit var agentPlanRepository: AgentPlanRepository

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.d("HermesAgent initialized")

        // Seed default data on first launch
        seedDefaultData()
    }

    private fun seedDefaultData() {
        val seeder = DatabaseSeeder(providerRepository, agentPlanRepository)
        seeder.seedIfNeeded()
    }
}
