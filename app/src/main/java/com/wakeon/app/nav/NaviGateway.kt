package com.wakeon.app.nav

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.wakeon.app.TAG

/** 경유지 추가 결과. [reason]은 실패 이유(로그용). */
data class NaviResult(val ok: Boolean, val reason: String)

/** 내비 연결. 실패하면 앱 안 표시·음성 길안내로 대신한다(SPEC F8, E-5). */
interface NaviGateway {
    fun addWaypoint(rest: RestArea, onResult: (NaviResult) -> Unit)
    fun release()
}

/**
 * Pleos NaviHelper로 경유지를 넣는다(docs/PLEOS_NOTES.md 6장).
 * `addWaypoint()`는 반환값이 없어서 `onWaypointChanged` / `onError`를 [timeoutMs] 동안 기다린다.
 */
class PleosNaviGateway(
    context: Context,
    private val timeoutMs: Long,
    private val onArrived: () -> Unit,
) : NaviGateway {
    private val main = Handler(Looper.getMainLooper())
    private val helper = NaviHelper(context.applicationContext)
    private var connected = false
    private var pending: ((NaviResult) -> Unit)? = null

    private val listener = object : NaviHelperEventListener {
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            Log.i(TAG, "navi waypoint_changed")
            finish(NaviResult(true, "waypoint_changed"))
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            Log.i(TAG, "navi waypoint_arrived")
            main.post(onArrived)
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error=$errorCode")
            finish(NaviResult(false, "error_$errorCode"))
        }
    }

    init {
        try {
            helper.initialize(
                onConnected = {
                    connected = true
                    helper.addListener(listener)
                    Log.i(TAG, "navi connected")
                },
                onFailed = { t -> Log.w(TAG, "navi connect_failed ${t.message}") },
            )
        } catch (e: Exception) {
            Log.w(TAG, "navi init_failed ${e.javaClass.simpleName} ${e.message}")
        }
    }

    override fun addWaypoint(rest: RestArea, onResult: (NaviResult) -> Unit) {
        val lat = rest.latitude
        val lng = rest.longitude
        when {
            !connected -> return onResult(NaviResult(false, "not_connected"))
            lat == null || lng == null -> return onResult(NaviResult(false, "no_coordinates"))
        }
        pending = onResult
        main.postDelayed(timeout, timeoutMs)
        try {
            helper.addWaypoint(
                RequestWaypointInfo(
                    latitude = lat!!,
                    longitude = lng!!,
                    poiName = rest.name,
                    waypointIndex = WaypointIndex.FIRST,
                    poiId = "",
                    address = "",
                    poiSubId = "0",
                ),
            )
            Log.i(TAG, "navi add_waypoint_requested")
        } catch (e: Exception) {
            finish(NaviResult(false, "exception_${e.javaClass.simpleName}"))
        }
    }

    private val timeout = Runnable { finish(NaviResult(false, "timeout")) }

    private fun finish(result: NaviResult) {
        main.post {
            val callback = pending ?: return@post
            pending = null
            main.removeCallbacks(timeout)
            callback(result)
        }
    }

    override fun release() {
        try {
            helper.removeListener(listener)
            helper.release()
        } catch (_: Exception) {
        }
    }
}
