package com.a9ito.hermesagent

import android.content.Context
import com.a9ito.hermesagent.data.AppearanceRepository
import com.a9ito.hermesagent.data.HermesRepository
import com.a9ito.hermesagent.data.SettingsRepository

/**
 * Manual dependency injection. A small app does not need Hilt/Dagger or Koin;
 * this holds the singletons (settings + appearance + networking) the ViewModels
 * share. Initialized once from [HermesAgentApp.onCreate].
 */
object ServiceLocator {

    @Volatile
    private var settingsRepository: SettingsRepository? = null

    @Volatile
    private var hermesRepository: HermesRepository? = null

    @Volatile
    private var appearanceRepository: AppearanceRepository? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        if (settingsRepository == null) {
            val settings = SettingsRepository(appContext)
            settingsRepository = settings
            hermesRepository = HermesRepository(settings)
            appearanceRepository = AppearanceRepository(appContext)
        }
    }

    fun settings(): SettingsRepository =
        settingsRepository ?: error("ServiceLocator not initialized")

    fun hermes(): HermesRepository =
        hermesRepository ?: error("ServiceLocator not initialized")

    fun appearance(): AppearanceRepository =
        appearanceRepository ?: error("ServiceLocator not initialized")
}
