package com.wakeon.app.config

/**
 * 판단·타이머 기준값 (SPEC 5-6 기준값 일람, 4-3 타이머 일람, 7-4 타이밍).
 * 코드 안에 숫자를 직접 쓰지 않고 모두 여기서 읽는다. 주석의 `a.b`는 SPEC의 키 이름이다.
 */
data class WakeonConfig(
    // ── 위험 맥락 트리거 (5-3)
    /** trigger.continuous_min: C1 연속 주행 */
    val triggerContinuousMin: Int = 120,
    /** trigger.night_window: C2 시간대(하루 중 분, [시작, 끝)). 21:00~06:00, 12:00~15:00 */
    val triggerNightWindows: List<IntRange> = listOf(
        21 * 60 until 24 * 60,
        0 until 6 * 60,
        12 * 60 until 15 * 60,
    ),
    /** trigger.night_min_drive: C2 연속 주행 하한 */
    val triggerNightMinDriveMin: Int = 30,
    /** trigger.highspeed: C3 속도 · 지속 시간 */
    val triggerHighSpeedKmh: Int = 70,
    val triggerHighSpeedMin: Int = 10,
    /** trigger.start_within: 트리거 충족 뒤 첫 질문까지 최대 */
    val triggerStartWithinS: Int = 60,

    // ── 대화 (4-3, 5-3, 7-4)
    /** talk.interval: 문항 간격(기본 · 단축 · 소음) */
    val talkIntervalMin: Int = 4,
    val talkIntervalShortMin: Int = 3,
    val talkIntervalNoisyMin: Int = 8,
    /** talk.round_max / talk.round_gap */
    val talkRoundMax: Int = 6,
    val talkRoundGapMin: Int = 20,
    /** talk.no_answer_s: 질문 뒤 말소리 없음 판정 */
    val talkNoAnswerS: Int = 5,
    /** 말이 시작되면 끝점 검출까지 최대 (7-4) */
    val talkListenMaxS: Int = 8,
    /** talk.resume_after_load_s: 고부하가 모두 풀린 뒤 재개까지 */
    val talkResumeAfterLoadS: Int = 10,
    /** 멈춤이 이보다 길면 라운드 초기화 (F4) */
    val talkPauseResetMin: Int = 5,
    /** 인식 결과 화면 표시 (7-4) */
    val talkResultShowS: Int = 2,

    // ── 기준선과 의심 신호 (5-4, 5-5)
    /** baseline.n: 확정 3개 · 갱신 5개 */
    val baselineConfirmN: Int = 3,
    val baselineMaxN: Int = 5,
    /** suspect.latency_ratio: B의 1.5배 초과가 최근 3개 중 2개 */
    val suspectLatencyRatio: Double = 1.5,
    val suspectWindow: Int = 3,
    val suspectHits: Int = 2,
    /** suspect.temp_ms: 기준선 확정 전 임시 기준 */
    val suspectTempMs: Long = 2_000,
    /** suspect.wrong: 같은 라운드 오답 */
    val suspectWrong: Int = 2,
    /** A6: '아니' 연속 횟수 + S1 지속이면 쉼터 제안 */
    val suspectNoRepeat: Int = 3,

    // ── 경고 (F7)
    /** warn.step_s: 단계 간격 · 단계 수 */
    val warnStepS: Int = 5,
    val warnLevels: Int = 3,

    // ── 쉼터 (F8)
    /** rest.search_km: 진행 방향 탐색 거리 */
    val restSearchKm: Double = 30.0,
    /** 제안·선택 응답 대기 (7-4: S-08, S-11, S-15) */
    val offerWaitS: Int = 10,
    /** 남은 거리 안내 지점 (V-25) */
    val approachNoticeKm: List<Double> = listOf(3.0, 1.0),
    /** snooze.min: 미루기 */
    val snoozeMin: Int = 15,
    val snoozeBeforeKm: Double = 5.0,
    /** arrive.radius_m: 쉼터 반경 */
    val arriveRadiusM: Int = 150,
    /** 다음 쉼터가 이보다 멀면 휴게소로 대체 (F11 S-16) */
    val nextRestMaxKm: Double = 60.0,
    /** 경유지 추가 결과 대기 (AC-8.2) */
    val waypointTimeoutS: Int = 5,

    // ── 휴식 (F10)
    /** rest.default_min */
    val restDefaultMin: Int = 15,
    /** 종료 전 "등받이를 세워 주세요" (A10) */
    val restPrewarnS: Int = 60,
    /** 알람 단계 간격 · 단계 수 */
    val alarmStepS: Int = 30,
    val alarmLevels: Int = 3,

    // ── 재출발 체크 (F11)
    /** recheck.ratio */
    val recheckRatio: Double = 1.5,
    val recheckQuestions: Int = 3,
    /** "등받이를 세워 주세요" 뒤 질문 시작까지 */
    val recheckBackrestWaitS: Int = 5,

    // ── 출발 전 체크 (F2)
    /** 졸림 자가 보고가 이 값 이상이면 휴식 권유 (A2) */
    val precheckRestFromKss: Int = 7,
    /** 문항 사이 쉬는 시간 */
    val precheckGapMs: Long = 600,

    // ── 주행 판단 (F1)
    /** 기어 잔떨림: 마지막 값이 이만큼 유지되면 확정 */
    val gearDebounceMs: Long = 1_000,
    /** 세션 종료: 앱 휴식이 아닌 정차에서 P 유지 */
    val sessionEndParkMin: Int = 10,

    // ── 시연 (F10 데모)
    /** demo.rest_s */
    val demoRestS: Int = 30,
    /** 시연 휴식에서 "1분 전" 안내를 이만큼 전으로 */
    val demoRestPrewarnS: Int = 5,
) {
    companion object {
        const val MINUTE_MS = 60_000L
        const val SECOND_MS = 1_000L
    }
}
