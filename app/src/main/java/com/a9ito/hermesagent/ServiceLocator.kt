package com.a9ito.hermesagent

import android.content.Context
import com.a9ito.hermesagent.data.HermesRepository
import com.a9ito.hermesagent.data.SettingsRepository

/**
 * Manual dependency injection. A 3-screen app does not need Hilt/Dagger or Koin;
 * this holds the two singletons (settings + networking) the ViewModels share.
 * Initialized once from [HermesAgentApp.onCreate].
 */
object ServiceLocator {

    @Volatile
    private var settingsRepository: SettingsRepository? = null

    @Volatile
    private var hermesRepository: HermesRepository? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        if (settingsRepository == null) {
            val settings = SettingsRepository(appContext)
            settingsRepository = settings
            hermesRepository = HermesRepository(settings)
        }
    }

    fun settings(): SettingsRepository =
        settingsRepository ?: error("ServiceLocator not initialized")

    fun hermes(): HermesRepository =
        hermesRepository ?: error("ServiceLocator not initialized")
}
