package com.example

import android.app.Application
import com.guardian360.di.AppContainer

class GuardianApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationHelper.ensureChannels()
    }
}
