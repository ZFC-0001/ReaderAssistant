package com.example.localtaskassistant.bootstrap

import android.app.Application

class AssistantApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
    override fun onCreate() { super.onCreate(); container.start() }
}
