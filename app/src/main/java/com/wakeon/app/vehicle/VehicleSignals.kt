package com.wakeon.app.vehicle

enum class Gear { P, R, N, D }

enum class TurnSignal { OFF, LEFT, RIGHT }

/** 고부하 이유 (SPEC 1장 용어 · W-06 이유 칩 5개). */
enum class LoadReason { BRAKE, TURN_SIGNAL, NAV_VOICE, GLEO, CALL }

/**
 * 한 순간의 차량 신호. null은 "읽지 못함"이다.
 * 주행 중 = 기어가 P가 아니거나 속도 > 0. 기어를 모르면 속도만 보고, 둘 다 모르면 주행 중으로 본다(F1).
 */
data class VehicleSignals(
    val gear: Gear? = null,
    val speedKmh: Float? = null,
    val brake: Boolean? = null,
    val turn: TurnSignal? = null,
    val navVoice: Boolean? = null,
    val gleo: Boolean? = null,
    val call: Boolean? = null,
) {
    val driving: Boolean
        get() = when {
            gear == null && speedKmh == null -> true
            gear == null -> speedKmh!! > 0f
            else -> gear != Gear.P || (speedKmh ?: 0f) > 0f
        }

    val stopped: Boolean get() = gear == Gear.P && (speedKmh ?: 0f) <= 0f

    val loadReasons: Set<LoadReason>
        get() = buildSet {
            if (brake == true) add(LoadReason.BRAKE)
            if (turn != null && turn != TurnSignal.OFF) add(LoadReason.TURN_SIGNAL)
            if (navVoice == true) add(LoadReason.NAV_VOICE)
            if (gleo == true) add(LoadReason.GLEO)
            if (call == true) add(LoadReason.CALL)
        }

    val highLoad: Boolean get() = loadReasons.isNotEmpty()
}
