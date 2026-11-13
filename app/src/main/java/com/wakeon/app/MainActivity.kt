package com.wakeon.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
    private val runtime by lazy { WakeonApp.runtime(this) }

    /** 동의 화면에서 허락을 받은 뒤(허락하지 않아도) 동의를 마친다. 마이크가 없으면 무응답, 속도가 없으면 기어만 읽는다. */
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        Log.i(TAG, "permissions " + result.entries.joinToString(" ") { "${it.key.substringAfterLast('.')}=${it.value}" })
        runtime.realVehicle.subscribeSpeed()
        runtime.consent()
    }

    /** W-01 "동의하고 시작": 아직 허락받지 않은 실행 중 권한(마이크·속도)을 먼저 묻는다. */
    private fun consentWithPermissions() {
        val missing = RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) runtime.consent() else permissionRequest.launch(missing.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actions = RuntimeActions(runtime, ::consentWithPermissions)
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

/** 실행 중에 허락받는 권한. 나머지(Pleos 권한 등)는 설치할 때 허락된다(PLEOS_NOTES 7장). */
private val RUNTIME_PERMISSIONS = listOf(Manifest.permission.RECORD_AUDIO, "android.car.permission.CAR_SPEED")

private class RuntimeActions(private val runtime: WakeonRuntime, private val onConsent: () -> Unit) : WakeonActions {
    override fun consent() = onConsent()
    override fun startPrecheck() = runtime.startPrecheck()
    override fun skipPrecheck() = runtime.skipPrecheck()
    override fun tap(intent: UserIntent) = runtime.tap(intent)
    override fun setTrigger(id: TriggerId, on: Boolean) = runtime.setTrigger(id, on)
}
