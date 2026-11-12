package com.wakeon.app.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wakeon.app.WakeonApp

/**
 * 시연 신호 수신기. 형식은 docs/DEMO_SCENARIO.md 3장.
 * 화면에는 아무것도 표시하지 않는다(영상에 찍히면 안 됨). 로그에만 `demo=true`.
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val extras = intent.extras ?: return
        WakeonApp.runtime(context).handleDemo(extras)
    }

    companion object {
        const val ACTION = "com.wakeon.app.DEMO"
    }
}
