package com.wakeon.app.ui.screens

/**
 * 화면에만 쓰는 고정 문구(소리 내어 말하지 않는 것). 소리 내어 말하는 문장은 VoiceLines(대사 카탈로그)를 그대로 쓴다.
 * 시안 문구가 기본이고, 고친 곳은 docs/DESIGN_DIFF.md에 있다. "**굵게**" 표기는 [rich]가 굵은 글자로 바꾼다.
 * 금지 표현·길이 검사는 [all]을 쓴다(ScreenStringsTest).
 */
object ScreenStrings {
    // ── 주행 화면 (세로 창)
    const val HINT_WAKE = "언제든 **“웨이크온 켜줘”**라고 말하세요"
    const val DRIVE_ELAPSED = "주행 경과"
    fun nextTalk(min: Int) = "${min}분 뒤 먼저 말을 걸게요"
    const val HINT_STOP = "**“그만”**이라고 하면 멈춰요"
    const val ASKING = "질문중"
    const val LISTENING = "듣고 있어요"
    const val PAUSED = "대화 멈춤"
    const val GUIDING = "안내중"
    const val HINT_RESUME = "**“10초 뒤”** 이어서 말할게요"
    const val QUIET = "지금은 조용히 있을게요"
    const val REASON_BRAKE = "브레이크"
    const val REASON_TURN = "방향지시등"
    const val REASON_NAV = "내비 안내"
    const val REASON_GLEO = "글레오"
    const val REASON_CALL = "통화"
    const val HINT_ASK_AGAIN = "**대답이 없으면** 한 번 더 물어요"
    const val HINT_ALERT = "**대답이 없으면** 알림음이 울려요"
    const val ASK_AGAIN = "대답이 없어서 한 번 더 여쭤봐요"
    const val HINT_SNOOZE = "**“아니”**라고 하면 15분 뒤 다시 말해요"
    fun warnLevel(n: Int) = "경고신호  ·  ${n}단계"
    fun nextRest(name: String, km: String) = "**다음 쉼터 · $name ${km}km**"
    const val ADD_WAYPOINT = "경유지 추가"
    fun addWaypoint(name: String) = "$name 경유지 추가"
    fun about(min: Int) = "약 ${min}분"
    const val ASK_WAYPOINT = "경유지로 넣을까요?"
    const val NEAREST_SHELTER = "가장 가까운 졸음쉼터예요"
    const val NEAREST_SERVICE_AREA = "가장 가까운 휴게소예요"
    const val WAYPOINT_ADDED = "경유지로 넣었어요"
    fun arriveAt(clock: String) = "$clock 도착"
    const val HELP_REST = "도착하면\n쉬는 걸 도와드릴게요"
    const val HINT_APPROACH = "**3km · 1km** 남았을 때 한 번씩 알려요"
    const val HINT_NAV_OVERLAP = "**내비 안내와 겹치면 이 말은 생략해요**"

    // ── 정차 화면 (가로 창)
    const val CONSENT_TITLE = "주행 기능 추가 모드 [동의]"
    const val CONSENT_MUST_KNOW = "꼭 알아주세요"
    /** 필수 고지 문구는 SPEC F12 그대로(문구 변경 금지). */
    const val CONSENT_NOTICE = "본 서비스는 운전자의 주의 의무를 대신하지 않습니다. 졸음을 모두 알아채거나 막지 못합니다. 졸리면 즉시 안전한 곳에서 쉬세요."
    const val CONSENT_START = "동의하고 시작"
    val PERMISSIONS = listOf(
        "마이크" to "대답 속도만 확인하며 기록하지 않아요",
        "위치" to "현재 위치를 파악하고 쉼터를 찾아요",
        "기어·속도" to "정차와 주행을 구분해요",
        "시트·조명·공조" to "정차 휴식시 등받이·조명을 조정해요",
    )
    const val HOME_DETAIL = "평소 반응 속도를 알아 두면 졸음이 올 때 더 빠르게 알아챌 수 있어요."
    const val START_CHECK = "출발 전 체크  ·  30초"
    const val SKIP = "건너뛰기"
    const val TRIGGER_C1 = "연속 2시간 주행"
    const val TRIGGER_C2 = "밤 9시~새벽 6시 · 낮 12~3시"
    const val TRIGGER_C3 = "시속 70km 이상 10분"
    const val MEASURING = "평소 반응 속도를 측정하고 있어요"
    const val SELECT = "선택"
    const val MEASURED = "평소 반응 속도 측정을 마쳤어요"
    const val STAT_KSS = "졸림 정도"
    const val STAT_CORRECT = "정답"
    const val STAT_SPEED = "속도"
    const val HINT_GEAR_D = "**기어를 D로 바꾸면** 대기 상태로 넘어가요"
    const val START_REST = "휴식 15분 시작"
    const val AUTO_START = "말이 없으면 10초 뒤 자동 시작"
    const val ROUTINE = "몸 깨우기"
    const val COFFEE_TITLE = "커피가 있으면 지금"
    const val COFFEE_BODY = "카페인은 15~20분 뒤에 효과가 나요.\n마시고 바로 자면 깰 때쯤 효과가 와요."
    /**
     * 휴식포인트 안내(F15). 한 문장 "위드라이브 앱이 있으면 15분 휴식으로 도로공사 휴식포인트를 받을 수 있어요"를 제목·본문으로 나눴다.
     * 도로공사 휴식포인트는 운전자가 휴대폰 '위드라이브' 앱으로 받는 제도다(쉼터 도착 알림을 누르고 15분 이상 휴식, 2026-12-31까지).
     * Wakeon만 써도 받는 것처럼 읽히지 않게 고쳤다(2026-11-14 PM 결정, 출처는 docs/DECISIONS.md). 화면 안내만 하고 소리 내어 말하지 않는다.
     */
    const val POINT_TITLE = "위드라이브 앱이 있으면"
    const val POINT_BODY = "15분 휴식으로 도로공사\n휴식포인트를 받을 수 있어요"
    /** 제휴한 것처럼 보이지 않게 작은 글자로 붙인다(2026-11-13 PM 결정, 2026-11-14 문구 변경). */
    const val POINT_NOTE = "(Wakeon 연동은 제휴 후)"
    fun restOf(min: Int) = "${min}분 중 · 등받이를 젖히고 눈을 붙이세요"
    const val PLUS_5 = "+  5분"
    const val WAKE_NOW = "지금 일어나기"
    const val HINT_RECHECK = "**평소보다 많이 느리면** 5분 더 쉬기를 권해요"
    const val STAT_RESTS = "오늘 휴식"
    const val STAT_REST_TIME = "쉰  시간"
    const val STAT_WARNS = "경고"
    const val HINT_BACK_TO_DRIVE = "**기어를 D로 바꾸면 대기 상태**로 돌아가요 · 연속 주행 시간 0부터"

    /** 주행 화면에 나오는 고정 문구(F1: 한 덩어리 120자·3줄 이하 검사 대상). */
    val driving: List<String> = listOf(
        HINT_WAKE, DRIVE_ELAPSED, nextTalk(18), HINT_STOP, ASKING, LISTENING, PAUSED, GUIDING, HINT_RESUME, QUIET,
        REASON_BRAKE, REASON_TURN, REASON_NAV, REASON_GLEO, REASON_CALL, HINT_ASK_AGAIN, HINT_ALERT, ASK_AGAIN,
        HINT_SNOOZE, warnLevel(2), nextRest("칠곡휴게소", "32"), ADD_WAYPOINT, addWaypoint("선기졸음쉼터"), about(8),
        ASK_WAYPOINT, NEAREST_SHELTER, NEAREST_SERVICE_AREA, WAYPOINT_ADDED, arriveAt("23:20"), HELP_REST,
        HINT_APPROACH, HINT_NAV_OVERLAP,
    )

    /** 화면 고정 문구 전부(예시 값으로 채움). */
    val all: List<String> = driving + listOf(
        CONSENT_TITLE, CONSENT_MUST_KNOW, CONSENT_NOTICE, CONSENT_START, HOME_DETAIL, START_CHECK, SKIP,
        TRIGGER_C1, TRIGGER_C2, TRIGGER_C3, MEASURING, SELECT, MEASURED, STAT_KSS, STAT_CORRECT, STAT_SPEED,
        HINT_GEAR_D, START_REST, AUTO_START, ROUTINE, COFFEE_TITLE, COFFEE_BODY, POINT_TITLE, POINT_BODY, POINT_NOTE, restOf(15),
        PLUS_5, WAKE_NOW, HINT_RECHECK,
        STAT_RESTS, STAT_REST_TIME, STAT_WARNS, HINT_BACK_TO_DRIVE,
    ) + PERMISSIONS.flatMap { listOf(it.first, it.second) } + (1..9).mapNotNull { kssName(it) }
}
