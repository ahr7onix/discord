package com.chronos.app

import android.app.Application
import com.chronos.app.notifications.NotificationHelper

/** Application entry point: owns the [AppContainer] and creates channels. */
class ChronosApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannels(this)
    }
}

/** Convenience accessor from any Context. */
val android.content.Context.chronosContainer: AppContainer
    get() = (applicationContext as ChronosApp).container
