package com.wakeon.app

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeon.app.config.WakeonConfig.Companion.MINUTE_MS
import com.wakeon.app.core.WakeonSnapshot
import com.wakeon.app.core.WakeonState

const val TAG = "Wakeon"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runtime = WakeonApp.runtime(this)
        setContent {
            val snapshot by runtime.snapshot.collectAsStateWithLifecycle()
            StatusScreen(snapshot, windowBoundsPx = ::windowBoundsPx)
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

/**
 * 임시 확인 화면: 상태와 차량 값, 지금 대사를 보여 준다. 단계 5에서 피그마 시안 화면으로 바꾼다.
 * 시연용 버튼은 두지 않는다.
 */
@Composable
fun StatusScreen(s: WakeonSnapshot, windowBoundsPx: () -> String = { "?" }) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val config = LocalConfiguration.current
        val density = LocalDensity.current.density
        val w = maxWidth.value.toInt()
        val h = maxHeight.value.toInt()

        LaunchedEffect(w, h) {
            Log.i(
                TAG,
                "window content=${w}x${h}dp config=${config.screenWidthDp}x${config.screenHeightDp}dp " +
                    "bounds=${windowBoundsPx()}px density=$density",
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Wakeon", color = Color.White, fontSize = 61.sp, fontWeight = FontWeight.Bold)
            Text("${s.state.id} ${s.state.title}", color = Color(0xFF4FD0EE), fontSize = 45.sp, fontWeight = FontWeight.Bold)
            Text(
                "기어 ${s.gear ?: "?"} · ${s.speedKmh?.toInt() ?: "?"}km/h · 연속 ${s.driveMs / MINUTE_MS}분",
                color = Color.White,
                fontSize = 33.sp,
            )
            s.caption.lastOrNull()?.let { Text(it.text, color = Color.White, fontSize = 33.sp) }
            Text("$w × $h dp", color = Color(0xFF8A8A8A), fontSize = 24.sp)
        }
    }
}

@Preview(widthDp = 840, heightDp = 1184)
@Composable
private fun StatusScreenPreview() = StatusScreen(WakeonSnapshot(WakeonState.S03))
