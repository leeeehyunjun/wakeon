package com.wakeon.app

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeon.app.core.TriggerId
import com.wakeon.app.runtime.WakeonRuntime
import com.wakeon.app.ui.screens.ScreenId
import com.wakeon.app.ui.screens.WakeonActions
import com.wakeon.app.ui.screens.WakeonScreen
import com.wakeon.app.ui.screens.screenOf
import com.wakeon.app.voice.UserIntent

const val TAG = "Wakeon"

/**
 * 앱 창 하나. 화면은 상태 머신의 스냅샷만 보고 그린다(ui/screens). 시연용 버튼은 두지 않는다.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runtime = WakeonApp.runtime(this)
        val actions = RuntimeActions(runtime)
        setContent {
            val snapshot by runtime.snapshot.collectAsStateWithLifecycle()
            val screen = screenOf(snapshot)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth.value.toInt()
                val h = maxHeight.value.toInt()
                LaunchedEffect(w, h) { Log.i(TAG, "window content=${w}x${h}dp bounds=${windowBoundsPx()}px") }
                LaunchedEffect(screen) {
                    Log.i(TAG, "screen ${screen.id} state=${snapshot.state.id}")
                    restBrightness(screen == ScreenId.W13)
                }
                WakeonScreen(snapshot, actions)
            }
        }
    }

    /** W-13 휴식 모드는 다크·밝기 최저(SPEC 8장). 앱 창 밝기만 바꾸고 차량 설정은 건드리지 않는다. */
    private fun restBrightness(dim: Boolean) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (dim) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    /** 이 창이 실제로 받은 영역(px). API 30 미만은 알 수 없다. */
    private fun windowBoundsPx(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.let { "${it.width()}x${it.height()}" }
        } else {
            "?"
        }
}

private class RuntimeActions(private val runtime: WakeonRuntime) : WakeonActions {
    override fun consent() = runtime.consent()
    override fun startPrecheck() = runtime.startPrecheck()
    override fun skipPrecheck() = runtime.skipPrecheck()
    override fun tap(intent: UserIntent) = runtime.tap(intent)
    override fun setTrigger(id: TriggerId, on: Boolean) = runtime.setTrigger(id, on)
}
