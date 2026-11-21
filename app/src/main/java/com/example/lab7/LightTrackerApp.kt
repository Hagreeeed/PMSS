package com.example.lab7

import android.app.Application
import com.example.lab7.di.AppContainer

class LightTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
