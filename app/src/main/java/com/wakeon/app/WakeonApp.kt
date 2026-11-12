package com.wakeon.app

import android.app.Application
import android.content.Context
import com.wakeon.app.runtime.WakeonRuntime

class WakeonApp : Application() {
    lateinit var runtime: WakeonRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        runtime = WakeonRuntime(this).also { it.start() }
    }

    companion object {
        fun runtime(context: Context): WakeonRuntime = (context.applicationContext as WakeonApp).runtime
    }
}
