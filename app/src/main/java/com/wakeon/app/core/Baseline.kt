package com.wakeon.app.core

import com.wakeon.app.config.WakeonConfig

/** 응답 하나의 기록. 발화 내용은 담지 않는다(SPEC 9장). */
data class Response(
    val latencyMs: Long,
    val correct: Boolean,
    /** 유효 응답인가: 고부하 중 응답·인식 실패·무응답·자가 보고가 아님 (5-4) */
    val valid: Boolean,
)

/**
 * 기준선 B (SPEC 5-4, BUILD 단계 2-4).
 * - 출발 전 체크에서 정답형 유효 응답이 3개 이상이면 그 중앙값으로 확정(V-06).
 * - 3개 미만이면 임시 기준 2,000ms를 쓰고 주행 초반 대화로 채워 3개가 되면 확정.
 * - 확정 뒤에도 5개가 될 때까지 유효 응답을 더해 중앙값을 갱신한다.
 */
class Baseline(private val config: WakeonConfig) {
    private val samples = mutableListOf<Long>()

    var source: String? = null
        private set

    val count: Int get() = samples.size
    val confirmed: Boolean get() = samples.size >= config.baselineConfirmN

    /** 확정된 기준선(ms). 확정 전에는 null. */
    val baselineMs: Long? get() = if (confirmed) median(samples) else null

    /** 지연 판정 임계값: 확정 뒤 B×1.5, 확정 전 임시 기준. */
    val thresholdMs: Long
        get() = baselineMs?.let { (it * config.suspectLatencyRatio).toLong() } ?: config.suspectTempMs

    /**
     * 응답을 더한다. 기준선에 들어갔으면 true.
     * 막 확정됐으면 [onConfirmed]를 부른다(로그 `baseline_ready`).
     */
    fun add(response: Response, from: String, onConfirmed: (ms: Long, n: Int) -> Unit = { _, _ -> }): Boolean {
        if (!response.valid || samples.size >= config.baselineMaxN) return false
        val wasConfirmed = confirmed
        samples += response.latencyMs
        if (!wasConfirmed && confirmed) {
            source = from
            onConfirmed(baselineMs!!, samples.size)
        }
        return true
    }

    fun clear() {
        samples.clear()
        source = null
    }

    companion object {
        fun median(values: List<Long>): Long {
            require(values.isNotEmpty())
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        }
    }
}

/**
 * 의심 신호 S1·S2 (SPEC 5-5).
 * - S1 지연: 최근 유효 응답 3개 중 2개가 임계값 초과.
 * - S2 오답: 같은 라운드 정답형 오답 2회.
 */
class Suspicion(private val config: WakeonConfig) {
    private val recent = ArrayDeque<Long>()
    var wrongInRound = 0
        private set

    fun record(response: Response) {
        if (!response.valid) return
        recent.addLast(response.latencyMs)
        while (recent.size > config.suspectWindow) recent.removeFirst()
        if (!response.correct) wrongInRound++
    }

    fun s1(thresholdMs: Long): Boolean =
        recent.size >= config.suspectHits && recent.count { it > thresholdMs } >= config.suspectHits

    fun s2(): Boolean = wrongInRound >= config.suspectWrong

    fun newRound() {
        wrongInRound = 0
    }

    fun clear() {
        recent.clear()
        wrongInRound = 0
    }

    companion object {
        /** 단위 테스트·설명용: 최근 값 목록으로 S1을 계산한다. */
        fun s1Of(latenciesMs: List<Long>, thresholdMs: Long, config: WakeonConfig): Boolean {
            val window = latenciesMs.takeLast(config.suspectWindow)
            return window.size >= config.suspectHits && window.count { it > thresholdMs } >= config.suspectHits
        }
    }
}
