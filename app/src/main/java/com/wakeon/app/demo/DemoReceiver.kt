package com.wakeon.app.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wakeon.app.TAG

/**
 * 시연 신호 수신기. 형식은 docs/DEMO_SCENARIO.md 3장.
 * 단계 1에서는 받은 값을 로그로만 남긴다. 값 처리는 단계 3에서 붙인다.
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val extras = intent.extras ?: return
        // 값의 타입이 extra마다 달라서 타입 없는 get()으로 로그만 남긴다.
        @Suppress("DEPRECATION")
        val values = extras.keySet().sorted().joinToString(" ") { "$it=${extras.get(it)}" }
        Log.i(TAG, "demo $values")
    }

    companion object {
        const val ACTION = "com.wakeon.app.DEMO"
    }
}
