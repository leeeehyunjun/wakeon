package com.wakeon.app.core

import com.wakeon.app.nav.RestArea
import com.wakeon.app.voice.Line

/** 단조 시계(ms). 응답 시간과 타이머는 모두 이 시계로 잰다(SPEC F5). */
fun interface MonoClock {
    fun nowMs(): Long
}

/** 하루 중 분(0~1439). 시연에서는 `clock` 신호로 바뀐다. */
fun interface WallClock {
    fun minuteOfDay(): Int
}

/** 알림음 (F7 경고 1~3단계, F10 기상 알람 1~3단계). */
sealed interface Sound {
    val level: Int

    data class Warn(override val level: Int) : Sound
    data class Alarm(override val level: Int) : Sound
}

/**
 * 상태 머신이 바깥에 시키는 일. 결과는 같은 [token]을 붙여 상태 머신의 on… 함수로 돌려준다.
 * 상태 머신은 이 요청 말고는 바깥을 건드리지 않는다.
 */
interface Effects {
    /** 대사를 순서대로 말한다(사이 0.5초). 끝나면 onSpeechDone(token). */
    fun speak(token: Long, lines: List<Line>, louder: Boolean)
    /** 0.25초 안에 멈춘다. 멈춘 발화의 onSpeechDone은 보내지 않아도 된다. */
    fun stopSpeaking()
    /** 듣기 창을 연다. 결과는 onHeard / onNoAnswer / onRecognitionFailed. */
    fun listen(token: Long, windowMs: Long)
    fun stopListening()
    /** 알림음. 끝나면 onSoundDone(token). */
    fun playSound(token: Long, sound: Sound)
    fun stopSound()
    /** 경유지 추가. 결과는 onWaypointResult(token, ok). */
    fun addWaypoint(token: Long, rest: RestArea)
}

/** 이벤트 로그 (SPEC 10장). 음성 원본·인식 문장은 넣지 않는다. */
fun interface EventLog {
    fun event(name: String, params: Map<String, Any?>)
}
