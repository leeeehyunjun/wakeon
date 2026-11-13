package com.wakeon.app.core

import com.wakeon.app.nav.RestArea
import com.wakeon.app.vehicle.Gear
import com.wakeon.app.vehicle.LoadReason
import com.wakeon.app.voice.Line

/** 음성 진행 단계. 화면의 "말하는 중 / 듣는 중" 표시에 쓴다. */
enum class VoicePhase { IDLE, SPEAKING, LISTENING, RESULT, WAITING }

/** 출발 전 체크 결과 (W-03b). 기준선 숫자는 정차 중에만 보여 준다. */
data class PrecheckSummary(
    val kss: Int?,
    val correct: Int,
    val total: Int,
    val baselineMs: Long?,
)

/** 쉼터 제안 (W-09, W-10). */
data class RestOffer(
    val rest: RestArea,
    val distanceKm: Double,
    val etaMin: Int?,
    val next: RestArea?,
    val nextDistanceKm: Double?,
)

/** 세션 요약 (W-16). */
data class SessionSummary(
    val rests: Int = 0,
    val restMinutes: Int = 0,
    val warns: Int = 0,
)

/** 화면이 그리는 데 필요한 값 전부. 화면은 이 값만 보고 그린다. */
data class WakeonSnapshot(
    val state: WakeonState,
    val phase: VoicePhase = VoicePhase.IDLE,
    /** 지금 화면에 보일 대사. TTS와 같은 문장이다. */
    val caption: List<Line> = emptyList(),
    /** 인식한 대답(W-05b). 판정이 끝나면 지운다. */
    val heard: String? = null,
    val heardCorrect: Boolean? = null,
    /** 대화·체크 진행: "대화 3 / 6", "1 / 4", "질문 2 / 3" */
    val questionNo: Int = 0,
    val questionTotal: Int = 0,
    val gear: Gear? = null,
    val speedKmh: Float? = null,
    val loadReasons: Set<LoadReason> = emptySet(),
    val driveMs: Long = 0,
    /** 다음에 먼저 말을 걸 때까지 (W-04) */
    val nextTalkInMs: Long? = null,
    val minuteOfDay: Int = 0,
    val precheck: PrecheckSummary? = null,
    val baselineSamples: Int = 0,
    val baselineMs: Long? = null,
    /** 연속 무응답 횟수 (W-07b "무응답 1회") */
    val noAnswerCount: Int = 0,
    val warnLevel: Int = 0,
    val offer: RestOffer? = null,
    val waypointAdded: Boolean? = null,
    /** 듣기 창이 닫히는 시각(단조 ms). W-12의 10초 카운트다운. */
    val listenEndsAtMs: Long? = null,
    val restEndsAtMs: Long? = null,
    val restTotalMs: Long = 0,
    val alarmLevel: Int = 0,
    val arrivedMinuteOfDay: Int? = null,
    val summary: SessionSummary = SessionSummary(),
    /** 켜진 트리거 (W-02 토글). C1은 늘 켜짐(잠금). */
    val triggersEnabled: Set<TriggerId> = setOf(TriggerId.C1, TriggerId.C2, TriggerId.C3),
    val nowMs: Long = 0,
)
