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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp

const val TAG = "Wakeon"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WindowSizeScreen(windowBoundsPx = ::windowBoundsPx) }
    }

    /** 이 창이 실제로 받은 영역(px). API 30 미만은 알 수 없다. */
    private fun windowBoundsPx(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.let { "${it.width()}x${it.height()}" }
        } else {
            "?"
        }
}

/** 단계 1 확인용 화면: 앱 이름과 받은 창 크기(dp)를 보여 주고 로그로도 남긴다. */
@Composable
fun WindowSizeScreen(windowBoundsPx: () -> String = { "?" }) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val config = LocalConfiguration.current
        val density = LocalDensity.current.density
        val contentW = maxWidth.value.toInt()
        val contentH = maxHeight.value.toInt()
        val configW = config.screenWidthDp
        val configH = config.screenHeightDp

        LaunchedEffect(contentW, contentH, configW, configH) {
            Log.i(
                TAG,
                "window content=${contentW}x${contentH}dp config=${configW}x${configH}dp " +
                    "bounds=${windowBoundsPx()}px density=$density",
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Wakeon", color = Color.White, fontSize = 61.sp, fontWeight = FontWeight.Bold)
            Text("${contentW} × ${contentH} dp", color = Color.White, fontSize = 33.sp)
        }
    }
}

@Preview(widthDp = 840, heightDp = 1184)
@Composable
private fun WindowSizeScreenPortraitPreview() = WindowSizeScreen()

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun WindowSizeScreenLandscapePreview() = WindowSizeScreen()
