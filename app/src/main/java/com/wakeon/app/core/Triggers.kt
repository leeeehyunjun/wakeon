package com.wakeon.app.core

import com.wakeon.app.config.WakeonConfig
import com.wakeon.app.config.WakeonConfig.Companion.MINUTE_MS

/** 위험 맥락 트리거 (SPEC 5-3). C4("웨이크온 켜줘")는 음성 의도로 따로 받는다. */
enum class TriggerId { C1, C2, C3 }

/**
 * 연속 주행 시간(5-2)과 트리거 C1~C3 판단. 평소에는 조건이 참이고 라운드 간격이 지났으면 대화를 연다.
 *
 * 시연 신호로 연속 주행 시간을 건너뛸 때만([jumpDriveTo]) 다음 규칙을 쓴다(docs/DECISIONS.md, 장면 5):
 * - 건너뛴 동안 참이 된 C2·C3는 "이미 대화한 것"으로 보고, 한 번 거짓이 되었다가 다시 참이 될 때까지 새 라운드를 열지 않는다.
 * - 건너뛰면서 연속 2시간(C1)을 넘으면 라운드 간격과 상관없이 바로 대화를 시작한다.
 */
class DriveTriggers(private val config: WakeonConfig) {
    var driveMs: Long = 0
        private set

    val enabled = mutableSetOf(TriggerId.C1, TriggerId.C2, TriggerId.C3)

    private var highSpeedMs: Long = 0
    private var c1JumpPending = false
    private val suppressed = mutableSetOf<TriggerId>()

    /** 주행 중인 동안 시간을 더한다. */
    fun advance(elapsedMs: Long, driving: Boolean, speedKmh: Float?) {
        if (driving) driveMs += elapsedMs
        highSpeedMs = if ((speedKmh ?: 0f) >= config.triggerHighSpeedKmh) highSpeedMs + elapsedMs else 0
    }

    fun reset() {
        driveMs = 0
        c1JumpPending = false
        suppressed.clear()
    }

    /** 시연: 연속 주행 시간을 바로 정한다. 건너뛴 동안은 지금 속도로 달린 것으로 본다(70km/h 이상이면 C3도 참). */
    fun jumpDriveTo(ms: Long, minuteOfDay: Int, speedKmh: Float?) {
        val c1Limit = config.triggerContinuousMin * MINUTE_MS
        val skipped = (ms - driveMs).coerceAtLeast(0)
        if (driveMs < c1Limit && ms >= c1Limit) c1JumpPending = true
        driveMs = ms
        if ((speedKmh ?: 0f) >= config.triggerHighSpeedKmh) highSpeedMs += skipped
        suppressed.clear()
        suppressed += active(minuteOfDay).filter { it != TriggerId.C1 }
    }

    /** 주행 대기(S-03)가 아닐 때 부른다. 대화 중에 C1을 넘었으면 그 라운드가 C1 몫을 한 것으로 본다. */
    fun observe(minuteOfDay: Int) {
        c1JumpPending = false
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

    /** 이번 검사에서 대화를 열 트리거. [gapOver]는 라운드 간격이 지났는지. */
    fun due(minuteOfDay: Int, gapOver: Boolean): TriggerId? {
        val active = active(minuteOfDay)
        suppressed.retainAll(active.toSet())
        if (c1JumpPending && TriggerId.C1 in active) {
            c1JumpPending = false
            return TriggerId.C1
        }
        if (!gapOver) return null
        return active.firstOrNull { it !in suppressed }
    }

    fun inNightWindow(minuteOfDay: Int) = config.triggerNightWindows.any { minuteOfDay in it }

    /**
     * 다음에 먼저 말을 걸 때까지 남은 시간(ms) 추정. 화면 W-04의 "18분 뒤", "30분 뒤" 예고용.
     * 라운드 간격이 남아 있으면 그보다 빠르지 않다.
     */
    fun nextTalkInMs(minuteOfDay: Int, gapRemainingMs: Long): Long? {
        val candidates = mutableListOf<Long>()
        candidates += maxOf(config.triggerContinuousMin * MINUTE_MS - driveMs, gapRemainingMs)
        if (TriggerId.C2 in enabled && TriggerId.C2 !in suppressed && inNightWindow(minuteOfDay)) {
            candidates += maxOf(config.triggerNightMinDriveMin * MINUTE_MS - driveMs, gapRemainingMs)
        }
        if (TriggerId.C3 in enabled && TriggerId.C3 !in suppressed && highSpeedMs > 0) {
            candidates += maxOf(config.triggerHighSpeedMin * MINUTE_MS - highSpeedMs, gapRemainingMs)
        }
        return candidates.minOrNull()?.coerceAtLeast(0)
    }
}
