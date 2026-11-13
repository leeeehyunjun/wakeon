package com.wakeon.app.ui.screens

import com.wakeon.app.config.WakeonConfig.Companion.MINUTE_MS
import com.wakeon.app.core.WakeonSnapshot
import com.wakeon.app.core.WakeonStateMachine
import kotlin.math.roundToInt

// 화면 글자 도우미. 소리 내어 말하는 문장은 스냅샷의 대사(caption)를 그대로 쓰고, 문장 단위로만 나눈다.

/** 지금 말하는 대사 전체. 화면 문구 = TTS 문장. */
fun captionText(s: WakeonSnapshot): String = s.caption.joinToString(" ") { it.text }

/** 문장으로 나눈다(문장 부호는 그대로). */
fun rawSentences(text: String): List<String> =
    text.split(Regex("""(?<=[.?!])\s+""")).map { it.trim() }.filter { it.isNotEmpty() }

/** 문장으로 나눈다. 화면 큰 글자에서는 끝의 마침표를 뗀다(물음표·느낌표는 둔다). */
fun sentences(text: String): List<String> = rawSentences(text).map { it.removeSuffix(".") }

/** 연속 주행 시간 "1시간 42분" / "0분". */
fun formatDrive(ms: Long): String {
    val m = (ms / MINUTE_MS).toInt()
    return if (m >= 60) "${m / 60}시간 ${m % 60}분" else "${m}분"
}

/** 남은 분(올림). 0이면 1분으로 보인다. */
fun ceilMinutes(ms: Long): Int = ((ms + MINUTE_MS - 1) / MINUTE_MS).toInt().coerceAtLeast(1)

/** 거리: 대사와 같은 규칙(10km 이상 정수, 미만 소수 한 자리). */
fun formatKm(km: Double): String = WakeonStateMachine.formatKm(km)

/** 내비 카드 거리: 늘 소수 한 자리 ("11.6"). */
fun formatKm1(km: Double): String = ((km * 10).roundToInt() / 10.0).toString()

/** 하루 중 분 → "23:20". */
fun formatClock(minuteOfDay: Int): String {
    val m = ((minuteOfDay % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

/** 남은 시간 → "14:32" (분:초). */
fun formatTimer(ms: Long): String {
    val total = ((ms.coerceAtLeast(0) + 999) / 1000).toInt()
    return "%02d:%02d".format(total / 60, total % 60)
}

/** 졸림 정도(KSS) 이름. 홀수 칸만 이름이 있다(DEMO_SCENARIO 장면 2). */
fun kssName(n: Int): String? = when (n) {
    1 -> "아주 또렷"
    3 -> "또렷"
    5 -> "보통"
    7 -> "졸림"
    9 -> "깨어 있기 힘듦"
    else -> null
}

fun kssText(n: Int): String = kssName(n)?.let { "$n · $it" } ?: "$n"
