package com.wakeon.app.runtime

import android.util.Log
import com.wakeon.app.TAG
import com.wakeon.app.core.EventLog

/**
 * 이벤트 로그를 Logcat(`adb logcat -s Wakeon`)에 남긴다. 1차 제출은 기기 로그만 쓴다(SPEC 9장).
 * 형식: `이벤트이름 키=값 … demo=true`
 */
class LogcatEventLog(private val demo: () -> Boolean) : EventLog {
    override fun event(name: String, params: Map<String, Any?>) {
        val body = params.entries.filter { it.value != null }.joinToString(" ") { "${it.key}=${it.value}" }
        val demoTag = if (demo()) " demo=true" else ""
        Log.i(TAG, "$name $body$demoTag".replace("  ", " ").trim())
    }
}
