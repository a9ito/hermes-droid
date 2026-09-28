package com.a9ito.hermesagent

import android.app.Application

/** Application entry point: wires the manual DI container once. */
class HermesAgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
