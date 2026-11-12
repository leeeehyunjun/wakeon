package com.wakeon.app.core

import com.wakeon.app.config.WakeonConfig
import com.wakeon.app.config.WakeonConfig.Companion.MINUTE_MS

/** 위험 맥락 트리거 (SPEC 5-3). C4("웨이크온 켜줘")는 음성 의도로 따로 받는다. */
enum class TriggerId { C1, C2, C3 }

/**
 * 연속 주행 시간(5-2)과 트리거 C1~C3 판단.
 *
 * 시연 신호로 연속 주행 시간을 건너뛰면([jumpDriveTo]) 건너뛴 동안 참이 된 C2·C3는
 * "이미 대화한 것"으로 보고, 한 번 거짓이 되었다가 다시 참이 될 때까지 새 라운드를 열지 않는다.
 * C1(연속 2시간)은 넘는 순간 라운드 간격과 상관없이 대화를 시작한다(휴식을 권하는 최저선, A1).
 */
class DriveTriggers(private val config: WakeonConfig) {
    var driveMs: Long = 0
        private set

    val enabled = mutableSetOf(TriggerId.C1, TriggerId.C2, TriggerId.C3)

    private var highSpeedMs: Long = 0
    private var lastC1 = false
    private val suppressed = mutableSetOf<TriggerId>()

    /** 주행 중인 동안 시간을 더한다. */
    fun advance(elapsedMs: Long, driving: Boolean, speedKmh: Float?) {
        if (driving) driveMs += elapsedMs
        highSpeedMs = if ((speedKmh ?: 0f) >= config.triggerHighSpeedKmh) highSpeedMs + elapsedMs else 0
    }

    fun reset() {
        driveMs = 0
        lastC1 = false
        suppressed.clear()
    }

    /**
     * 시연: 연속 주행 시간을 바로 정한다. 건너뛴 동안은 지금 속도로 달린 것으로 본다(70km/h 이상이면 C3도 참).
     * 2시간을 넘기는 건너뛰기는 C1을 넘은 것으로 본다.
     */
    fun jumpDriveTo(ms: Long, minuteOfDay: Int, speedKmh: Float?) {
        val skipped = (ms - driveMs).coerceAtLeast(0)
        driveMs = ms
        if ((speedKmh ?: 0f) >= config.triggerHighSpeedKmh) highSpeedMs += skipped
        suppressed.clear()
        suppressed += active(minuteOfDay).filter { it != TriggerId.C1 }
    }

    /** 주행 대기(S-03)가 아닐 때 부른다. 대화 중에 C1을 넘으면 그 라운드가 C1 몫을 한 것으로 본다. */
    fun observe(minuteOfDay: Int) {
        lastC1 = isTrue(TriggerId.C1, minuteOfDay)
        suppressed.retainAll(active(minuteOfDay).toSet())
    }

    fun isTrue(id: TriggerId, minuteOfDay: Int): Boolean = when (id) {
        TriggerId.C1 -> driveMs >= config.triggerContinuousMin * MINUTE_MS
        TriggerId.C2 -> inNightWindow(minuteOfDay) && driveMs >= config.triggerNightMinDriveMin * MINUTE_MS
        TriggerId.C3 -> highSpeedMs >= config.triggerHighSpeedMin * MINUTE_MS
    }

    /** 켜져 있고 지금 참인 트리거. */
    fun active(minuteOfDay: Int): List<TriggerId> =
        TriggerId.entries.filter { (it == TriggerId.C1 || it in enabled) && isTrue(it, minuteOfDay) }

    /**
     * 이번 검사에서 대화를 열 트리거. [gapOver]는 라운드 간격이 지났는지.
     * C1을 막 넘었으면 간격과 상관없이 C1을 돌려준다.
     */
    fun due(minuteOfDay: Int, gapOver: Boolean): TriggerId? {
        val active = active(minuteOfDay)
        suppressed.retainAll(active.toSet())
        val c1 = TriggerId.C1 in active
        val c1Edge = c1 && !lastC1
        lastC1 = c1
        if (c1Edge) return TriggerId.C1
        if (!gapOver) return null
        return active.firstOrNull { it !in suppressed }
    }

    fun inNightWindow(minuteOfDay: Int) = config.triggerNightWindows.any { minuteOfDay in it }

    /**
     * 다음에 먼저 말을 걸 때까지 남은 시간(ms) 추정. 화면 W-04의 "18분 뒤" 예고용.
     * 이미 참이면 [gapRemainingMs], 알 수 없으면 null.
     */
    fun nextTalkInMs(minuteOfDay: Int, gapRemainingMs: Long): Long? {
        val candidates = mutableListOf<Long>()
        val c1Left = config.triggerContinuousMin * MINUTE_MS - driveMs
        candidates += if (c1Left > 0) c1Left else gapRemainingMs
        if (TriggerId.C2 in enabled && TriggerId.C2 !in suppressed && inNightWindow(minuteOfDay)) {
            val left = config.triggerNightMinDriveMin * MINUTE_MS - driveMs
            candidates += maxOf(left, gapRemainingMs)
        }
        if (TriggerId.C3 in enabled && TriggerId.C3 !in suppressed && highSpeedMs > 0) {
            val left = config.triggerHighSpeedMin * MINUTE_MS - highSpeedMs
            candidates += maxOf(left, gapRemainingMs)
        }
        return candidates.minOrNull()?.coerceAtLeast(0)
    }
}
