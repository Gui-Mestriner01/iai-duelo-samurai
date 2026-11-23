package com.gui.iai

import android.app.Application
import com.gui.iai.core.AppContainer

class IaiApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.soundEngine.prepare()
    }
}
