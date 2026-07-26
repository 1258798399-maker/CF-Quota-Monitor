package com.nova.cfquota

import android.app.Application
import com.nova.cfquota.core.AppContainer

class CfQuotaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
