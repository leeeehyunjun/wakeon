package com.wakeon.app.core

import com.wakeon.app.config.WakeonConfig
import com.wakeon.app.config.WakeonConfig.Companion.MINUTE_MS
import com.wakeon.app.config.WakeonConfig.Companion.SECOND_MS
import com.wakeon.app.core.WakeonState.S00
import com.wakeon.app.core.WakeonState.S01
import com.wakeon.app.core.WakeonState.S02
import com.wakeon.app.core.WakeonState.S03
import com.wakeon.app.core.WakeonState.S04
import com.wakeon.app.core.WakeonState.S05
import com.wakeon.app.core.WakeonState.S06
import com.wakeon.app.core.WakeonState.S07
import com.wakeon.app.core.WakeonState.S08
import com.wakeon.app.core.WakeonState.S09
import com.wakeon.app.core.WakeonState.S10
import com.wakeon.app.core.WakeonState.S11
import com.wakeon.app.core.WakeonState.S12
import com.wakeon.app.core.WakeonState.S13
import com.wakeon.app.core.WakeonState.S14
import com.wakeon.app.core.WakeonState.S15
import com.wakeon.app.core.WakeonState.S16
import com.wakeon.app.core.WakeonState.S17
import com.wakeon.app.nav.DemoRoute
import com.wakeon.app.nav.RestArea
import com.wakeon.app.nav.RestAreaType
import com.wakeon.app.vehicle.Gear
import com.wakeon.app.vehicle.VehicleSignals
import com.wakeon.app.voice.Answer
import com.wakeon.app.voice.ConversationPlanner
import com.wakeon.app.voice.Grammar
import com.wakeon.app.voice.KoreanText
import com.wakeon.app.voice.Line
import com.wakeon.app.voice.Question
import com.wakeon.app.voice.QuestionBank
import com.wakeon.app.voice.QuestionType
import com.wakeon.app.voice.Slot
import com.wakeon.app.voice.TalkStep
import com.wakeon.app.voice.Topic
import com.wakeon.app.voice.UserIntent
import com.wakeon.app.voice.VoiceLines
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** 듣기 결과. */
sealed interface ListenResult {
    /** [latencyMs]: 질문 음성 종료 → 첫 발화 시작. 버튼이면 null. [retried]: 되물은 뒤의 대답. */
    data class Answer(val text: String, val latencyMs: Long?, val retried: Boolean = false) : ListenResult
    data object None : ListenResult
    data object Failed : ListenResult
}

/**
 * Wakeon 상태 머신. 판단과 상태 전이는 여기서만 한다(CLAUDE.md 5장).
 *
 * - 바깥 신호는 on… 함수로 들어오고, 바깥에 시킬 일은 [Effects]로 나간다.
 * - 같은 스레드에서만 부른다. [tick]은 0.2초 정도마다 부른다.
 * - 전이 표(SPEC 4-2) 중 1차 시연 경로에 필요한 행을 구현했다. 나머지 행은 `TODO(4-2)` 주석으로 남겼다.
 */
class WakeonStateMachine(
    private val config: WakeonConfig,
    private val clock: MonoClock,
    private val wallClock: WallClock,
    private val effects: Effects,
    private val eventLog: EventLog,
    private val route: DemoRoute?,
    consented: Boolean,
) {
    var state: WakeonState = if (consented) S01 else S00
        private set

    private val _snapshot = MutableStateFlow(WakeonSnapshot(state))
    val snapshot: StateFlow<WakeonSnapshot> = _snapshot.asStateFlow()

    // ── 차량
    private var signals = VehicleSignals()
    private var gear: Gear? = null
    private var gearKnown = false
    private var pendingGear: Gear? = null
    private var pendingGearSet = false
    private var pendingGearSinceMs = 0L
    private var parkedSinceMs: Long? = null
    private var lastTickMs = clock.nowMs()
    private val driving get() = VehicleSignals(gear = if (gearKnown) gear else null, speedKmh = signals.speedKmh).driving
    private val stopped get() = gear == Gear.P && (signals.speedKmh ?: 0f) <= 0f

    // ── 음성 요청과 이어서 할 일
    private var tokenSeq = 0L
    private var speechToken = NONE
    private var afterSpeech: (() -> Unit)? = null
    private var listenToken = NONE
    private var onListen: ((ListenResult) -> Unit)? = null
    private var soundToken = NONE
    private var afterSound: (() -> Unit)? = null
    /** 지금 묻고 있는 질문의 대답 처리기. 말하는 도중에 버튼을 눌러도 대답으로 받는다. */
    private var pendingAnswerHandler: ((ListenResult) -> Unit)? = null
    private var waypointToken = NONE
    private var waypointRequestedAtMs = 0L
    private var phase = VoicePhase.IDLE
    private var caption: List<Line> = emptyList()
    private var listenEndsAtMs: Long? = null

    // ── 상태 안 타이머 (상태가 바뀌면 모두 취소)
    private class Timer(val atMs: Long, val action: () -> Unit)
    private val timers = linkedMapOf<String, Timer>()

    // ── 시연 설정
    private var timeScale = 1f
    private var restSecOverride: Int? = null

    // ── 세션 값 (기억 질문의 답은 메모리에만, 세션이 끝나면 지운다)
    private val baseline = Baseline(config)
    private val suspicion = Suspicion(config)
    private val triggers = DriveTriggers(config)
    private val memory = mutableMapOf<String, String>()
    private val planner = ConversationPlanner(memory)
    private var summary = SessionSummary()

    // 출발 전 체크
    private var precheckIndex = 0
    private var kss: Int? = null
    private var precheckCorrect = 0
    private var precheckTotal = 0
    private var precheckSummary: PrecheckSummary? = null
    /** 출발 전 체크에서 다음 문항 앞에 붙일 짧은 호응("강아지 좋죠.") */
    private var precheckAck: String? = null

    // 대화
    private var questionNo = 0
    private var questionTotal = 0
    private var loadDuringQuestion = false
    private var shortInterval = false
    private var nextRoundAtMs = 0L
    private var heard: String? = null
    private var heardCorrect: Boolean? = null
    private var pausedFrom: WakeonState? = null
    private var pausedAtMs = 0L
    /** 아직 말하지 않은 "호응 + 이어지는 질문". 결과 표시 중·멈춤 중에도 남아 있다가 이어서 말한다. */
    private var pendingStep: TalkStep? = null
    /** 대화 질문을 말하거나 듣는 중(멈추면 그 문항은 버린다) */
    private var talkInProgress = false

    // 의심 확인
    private enum class ConfirmMode { ASK, RECALL }
    private var confirmMode = ConfirmMode.ASK
    private var noAnswerStreak = 0
    private var noStreak = 0

    // 경고·쉼터
    private var warnLevel = 0
    private var positionKm: Double? = null
    private var offer: RestArea? = null
    private var nextCandidate: RestArea? = null
    private var offerAsks = 0
    private var snoozeCount = 0
    private var waypointAdded: Boolean? = null
    private var arriveSignal = false
    private val announcedKm = mutableSetOf<Double>()
    private var arrivedMinute: Int? = null

    // 휴식·재출발
    private var restStartedAtMs = 0L
    private var restTotalMs = 0L
    /** 휴식의 화면·대사 기준 길이(15분 + 연장). 시연(30초 휴식)에서도 화면은 이 길이로 보인다. */
    private var restNominalMs = 0L
    private var restExtensions = 0
    private var restRefusedSaid = false
    private var restEndsAtMs: Long? = null
    private var alarmLevel = 0
    private var moreRestCount = 0
    private var recheckIndex = 0
    private val recheckLatencies = mutableListOf<Long>()

    init {
        eventLog.event("state", mapOf("to" to state.id, "reason" to "start"))
        if (state == S01) enterHome(sayInvite = false)
        publish()
    }

    // ════════════════════════════════════════ 바깥 신호

    fun onVehicle(next: VehicleSignals) {
        val now = clock.nowMs()
        signals = next
        if (!gearKnown) {
            commitGear(next.gear, now)
        } else if (next.gear != gear) {
            if (!pendingGearSet || next.gear != pendingGear) {
                pendingGear = next.gear
                pendingGearSet = true
                pendingGearSinceMs = now
            }
        } else {
            pendingGearSet = false
        }
        handleLoad()
        checkArrival()
        publish()
    }

    fun tick() {
        val now = clock.nowMs()
        val elapsed = (now - lastTickMs).coerceAtLeast(0)
        lastTickMs = now
        if (pendingGearSet && now - pendingGearSinceMs >= config.gearDebounceMs) {
            pendingGearSet = false
            commitGear(pendingGear, now)
        }
        triggers.advance(elapsed, driving, signals.speedKmh)
        checkLongPark(now)
        if (state == S03) checkTriggers(now) else triggers.observe(minute())
        runDueTimers(now)
        publish()
    }

    fun onSpeechDone(token: Long) {
        if (token != speechToken) return
        speechToken = NONE
        if (phase == VoicePhase.SPEAKING) phase = VoicePhase.IDLE
        val next = afterSpeech
        afterSpeech = null
        next?.invoke()
        publish()
    }

    fun onHeard(token: Long, text: String, latencyMs: Long) =
        deliverListen(token, ListenResult.Answer(text, latencyMs))

    fun onNoAnswer(token: Long) = deliverListen(token, ListenResult.None)

    fun onRecognitionFailed(token: Long) = deliverListen(token, ListenResult.Failed)

    fun onSoundDone(token: Long) {
        if (token != soundToken) return
        soundToken = NONE
        val next = afterSound
        afterSound = null
        next?.invoke()
        publish()
    }

    fun onWaypointResult(token: Long, ok: Boolean) {
        if (token != waypointToken) return
        waypointToken = NONE
        val o = offer ?: return
        log("waypoint_added", "ok" to ok, "ms" to clock.nowMs() - waypointRequestedAtMs)
        waypointAdded = ok
        val pos = positionKm
        if (ok) {
            say(VoiceLines.v24(o.name, etaMinutes(o) ?: 0))
        } else {
            // E-5: 2초 안에 음성 길안내
            val km = pos?.let { formatKm(o.km - it) } ?: "?"
            say(VoiceLines.v29(km, o.exit))
        }
        publish()
    }

    /** 내비 `onWayPointArrived` 또는 시연 `arrive`. */
    fun onArriveSignal() {
        arriveSignal = true
        checkArrival()
        publish()
    }

    fun onRoutePosition(km: Double) {
        positionKm = km
        if (state == S09) announceApproach()
        checkArrival()
        publish()
    }

    fun onConsent() {
        if (state != S00) return
        log("consent_granted")
        go(S01, "consent")
        enterHome(sayInvite = true)
    }

    /** 홈의 "출발 전 체크" 버튼. */
    fun onStartPrecheck() {
        if (state == S01) startPrecheck()
    }

    /** 홈의 "건너뛰기" 버튼. 기능을 줄이지 않고 주행 초반 대화로 채운다(F2). */
    fun onSkipPrecheck() {
        if (state != S01 && state != S02) return
        log("precheck_skip")
        if (state == S02) go(S01, "skip")
        cancelVoice()
        publish()
    }

    /** 화면 버튼. 같은 일을 음성으로도 할 수 있다. */
    fun onTap(intent: UserIntent) {
        when {
            state == S11 && intent == UserIntent.YES -> startRest("tap", config.restDefaultMin)
            state == S12 && intent == UserIntent.AWAKE -> awake()
            state == S12 && intent == UserIntent.SNOOZE_5 -> extendRest("tap")
            onListen != null -> deliverListen(listenToken, ListenResult.Answer(tapWord(intent), null))
            afterSpeech != null && (state == S06 || state == S08) -> {
                // 질문을 말하는 도중에 누름: 말을 멈추고 대답으로 처리
                val handler = pendingAnswerHandler ?: return
                cancelVoice()
                handler(ListenResult.Answer(tapWord(intent), null))
            }
        }
        publish()
    }

    /** 트리거 설정(W-02 토글). 정차 중에만 바꿀 수 있고, C1은 끌 수 없다(AC-3.3, 3.4). */
    fun setTriggerEnabled(id: TriggerId, on: Boolean) {
        if (id == TriggerId.C1 || driving) return
        if (on) triggers.enabled += id else triggers.enabled -= id
        log("trigger_toggle", "type" to id.name, "on" to on)
        publish()
    }

    // ── 시연 신호 (DEMO_SCENARIO 3장)

    fun demoDriveMinutes(minutes: Int) {
        triggers.jumpDriveTo(minutes * MINUTE_MS, minute(), signals.speedKmh)
        log("demo_drive_min", "min" to minutes)
        publish()
    }

    fun demoTimeScale(scale: Float) {
        timeScale = scale.coerceAtLeast(0.01f)
        log("demo_time_scale", "scale" to timeScale)
    }

    fun demoRestSeconds(seconds: Int) {
        restSecOverride = seconds.takeIf { it > 0 }
        log("demo_rest_s", "s" to seconds)
    }

    /**
     * 시연: 주행 중 대화의 첫 주제를 정한다(예: PET → 첫 질문이 "아까 강아지가 더 좋다고 하셨죠. 키우세요?").
     * 시연 설정이라 [demoReset] 뒤에도 유지된다. 기본은 음식(점심) 줄기부터.
     */
    fun demoTalkFrom(topic: Topic) {
        planner.startFrom(topic)
        log("demo_talk_from", "topic" to topic.name)
    }

    /** 세션·기준선·카운터 초기화, S-01로. 시연 설정(배속·휴식 길이·첫 주제)은 그대로 둔다. */
    fun demoReset() {
        cancelVoice()
        timers.clear()
        baseline.clear()
        suspicion.clear()
        triggers.reset()
        memory.clear()
        planner.reset()
        pendingStep = null
        talkInProgress = false
        summary = SessionSummary()
        precheckSummary = null
        precheckAck = null
        kss = null
        precheckCorrect = 0
        precheckTotal = 0
        questionNo = 0
        questionTotal = 0
        shortInterval = false
        nextRoundAtMs = 0
        noAnswerStreak = 0
        noStreak = 0
        warnLevel = 0
        positionKm = null
        offer = null
        nextCandidate = null
        snoozeCount = 0
        waypointAdded = null
        arriveSignal = false
        announcedKm.clear()
        arrivedMinute = null
        restEndsAtMs = null
        alarmLevel = 0
        moreRestCount = 0
        heard = null
        heardCorrect = null
        loadDuringQuestion = false
        go(S01, "reset")
        enterHome(sayInvite = true)
    }

    // ════════════════════════════════════════ 전역 행

    private fun commitGear(next: Gear?, now: Long) {
        val from = gear
        val wasKnown = gearKnown
        gear = next
        gearKnown = true
        parkedSinceMs = if (next == Gear.P) (parkedSinceMs ?: now) else null
        if (wasKnown && from != next) log("gear_changed", "from" to from?.name, "to" to next?.name)
        val driveStart = (from == Gear.P || from == null) && next != null && next != Gear.P
        if (wasKnown && driveStart) onDriveStart()
        checkArrival()
    }

    /** 전역: 주행 시작(P→D). */
    private fun onDriveStart() {
        when (state) {
            S12, S13 -> {
                // E-3: 1초 안에 연출 중지, V-37, 20분 뒤 제안 예약
                log("rest_aborted", "remaining_min" to ((restEndsAtMs ?: clock.nowMs()) - clock.nowMs()) / MINUTE_MS)
                go(S03, "drive_during_rest")
                shortInterval = true
                say(VoiceLines.V37)
                // TODO(4-2): 20분 뒤 라운드 + 쉼터 제안 예약(A12)
            }
            S01, S02, S11, S14, S15, S16, S17 -> {
                // TODO(4-2): S-14 도중이면 남은 질문 1개만 음성(A11)
                go(S03, "drive_start")
            }
            // S-00은 동의 전이라 마이크를 열 수 없다. "정차 후 설정해 주세요"(E-4)만 보이고 머문다.
            else -> Unit
        }
    }

    /** 전역: 고부하 켜짐 → S-05, 모두 꺼지고 10초 → 이전 상태. */
    private fun handleLoad() {
        val load = signals.highLoad
        if (load) loadDuringQuestion = true
        when (state) {
            S04, S06 -> if (load) pause()
            S05 -> if (load) {
                timers.remove(T_RESUME)
            } else if (T_RESUME !in timers) {
                after(T_RESUME, config.talkResumeAfterLoadS * SECOND_MS) { resume() }
            }
            else -> Unit
        }
    }

    /** 전역: 앱 휴식이 아닌 정차에서 P 10분 → S-01, 연속 주행 시간 리셋. */
    private fun checkLongPark(now: Long) {
        val since = parkedSinceMs ?: return
        if (state !in DRIVE_STATES) return
        if (now - since < config.sessionEndParkMin * MINUTE_MS) return
        // TODO(4-2): S-03에서 P 3분 + 대화 1턴 이상이면 S-17 주행 후 평가(F19, P1)
        triggers.reset()
        // 세션이 끝나면 대화에서 들은 답(슬롯)을 지운다(SPEC 9장).
        memory.clear()
        planner.reset()
        pendingStep = null
        log("session_summary", "warns" to summary.warns, "rests" to summary.rests)
        go(S01, "park_10min")
    }

    // ════════════════════════════════════════ S-01 홈 · S-02 출발 전 체크

    private fun enterHome(sayInvite: Boolean) {
        if (!sayInvite) return
        askQuestion(VoiceLines.V02) { r ->
            if (r is ListenResult.Answer) {
                when (Grammar.intentIn(r.text, S01)) {
                    UserIntent.YES -> {
                        // "응, 부산까지." → 목적지를 세션 메모리에(대화 줄기에 씀)
                        Answer.destination(r.text)?.let { memory[Slot.DESTINATION] = it }
                        startPrecheck()
                    }
                    UserIntent.NO -> log("precheck_skip")
                    else -> Unit
                }
            }
        }
    }

    private fun startPrecheck() {
        if (gear != Gear.P) return
        log("precheck_start")
        precheckIndex = 0
        precheckAck = null
        kss = null
        precheckCorrect = 0
        precheckTotal = 0
        go(S02, "precheck")
        askPrecheck()
    }

    private fun askPrecheck() {
        val q = QuestionBank.precheck[precheckIndex]
        questionNo = precheckIndex + 1
        questionTotal = QuestionBank.precheck.size
        val text = listOfNotNull(precheckAck, q.text).joinToString(" ")
        precheckAck = null
        // 문항마다 새로 본다(대화·재출발 체크와 같게). 빠뜨리면 체크 전에 한 번 울린 내비 안내 때문에 네 문항이 모두 무효가 된다.
        loadDuringQuestion = signals.highLoad
        clearHeard()
        askQuestion(Line(q.id, text)) { r -> onPrecheckAnswer(q, r) }
    }

    private fun onPrecheckAnswer(q: Question, r: ListenResult) {
        if (r is ListenResult.Answer) {
            // 화면(W-03): 다음 문항을 묻기 전까지 들은 말을 보여 준다. 판정은 보여 주지 않는다.
            heard = r.text
            heardCorrect = null
            phase = VoicePhase.RESULT
            val correct = QuestionBank.judge(q, r.text, memory)
            remember(q, r.text)
            if (q.memoryKey == QuestionBank.KEY_PET) {
                // 강아지·고양이: 답을 메모리에 두고 짧게 호응한 뒤 다음 문항으로(판정하지 않음)
                q.options.firstOrNull { KoreanText.compact(r.text).contains(it) }?.let { memory[Slot.PET_PREF] = it }
                precheckAck = Answer.core(r.text)?.let { "$it 좋죠." } ?: ConversationPlanner.GENERIC
            }
            if (q.type == QuestionType.SELF_REPORT) {
                kss = KoreanText.firstNumber(r.text)?.takeIf { it in 1..9 }
            } else {
                precheckTotal++
                if (correct) precheckCorrect++
                baseline.add(response(q, r, correct), "precheck", ::onBaselineReady)
            }
            log("precheck_q", "idx" to precheckIndex + 1, "latency_ms" to r.latencyMs, "correct" to correct, "kss" to kss)
        } else {
            log("precheck_q", "idx" to precheckIndex + 1, "answer" to "none")
        }
        precheckIndex++
        if (precheckIndex < QuestionBank.precheck.size) {
            after(T_NEXT, config.precheckGapMs) { askPrecheck() }
        } else {
            finishPrecheck()
        }
    }

    private fun finishPrecheck() {
        log("precheck_done")
        clearHeard()
        // TODO(4-2): 졸림 7 이상이면 V-05 → 수락 시 15분 휴식 S-12 (A2)
        precheckSummary = PrecheckSummary(kss, precheckCorrect, precheckTotal, baseline.baselineMs)
        go(S01, "precheck_done")
        say(if (baseline.confirmed) VoiceLines.V06 else VoiceLines.V07)
    }

    private fun onBaselineReady(ms: Long, n: Int) {
        log("baseline_ready", "ms" to ms, "n" to n, "source" to baseline.source)
    }

    // ════════════════════════════════════════ S-03 주행 대기 · S-04 대화 · S-05 멈춤

    private fun checkTriggers(now: Long) {
        if (signals.highLoad) return
        val due = triggers.due(minute(), gapOver = now >= nextRoundAtMs) ?: return
        startRound(due)
    }

    private fun startRound(trigger: TriggerId) {
        log("drive_armed", "trigger" to trigger.name)
        questionNo = 0
        questionTotal = config.talkRoundMax
        suspicion.newRound()
        noAnswerStreak = 0
        noStreak = 0
        pendingStep = null
        go(S04, trigger.name)
        askNextTalk()
    }

    /** 이어 갈 질문이 있으면 그것을, 없으면 다음 주제의 첫 질문을 묻는다. */
    private fun askNextTalk(prefix: String? = null) {
        val step = pendingStep
        pendingStep = null
        if (step?.question != null) {
            askTalk(step.question, ack = step.ack, prefix = prefix)
        } else {
            askTalk(planner.startNextThread(), ack = null, prefix = prefix)
        }
    }

    /**
     * 대화 문항 하나. 말하는 문장 = [prefix]("알겠어요. 그럼 보리 얘기 하나만 더요.") + [ack](호응) + 질문.
     * 응답 시간은 이 문장 전체가 끝난 때부터 잰다(SPEC F4 깨우는 스몰토크 원칙).
     */
    private fun askTalk(q: Question, ack: String?, prefix: String?) {
        questionNo++
        questionTotal = config.talkRoundMax
        loadDuringQuestion = signals.highLoad
        heard = null
        heardCorrect = null
        talkInProgress = true
        val text = listOfNotNull(prefix, ack, q.text).joinToString(" ")
        askQuestion(Line(q.id, text)) { r ->
            talkInProgress = false
            onTalkAnswer(q, r)
        }
    }

    private fun onTalkAnswer(q: Question, r: ListenResult) {
        when (r) {
            is ListenResult.Answer -> {
                when (Grammar.intentIn(r.text, S04)) {
                    UserIntent.STOP -> {
                        log("talk_stop")
                        return endRound("stop")
                    }
                    UserIntent.SLEEPY -> return offerRest(warnFirst = true, reason = "S4")
                    UserIntent.FIND_REST -> return offerRest(warnFirst = false, reason = "find_rest")
                    else -> Unit
                }
                noAnswerStreak = 0
                val correct = QuestionBank.judge(q, r.text, memory)
                val resp = response(q, r, correct)
                baseline.add(resp, "drive", ::onBaselineReady)
                suspicion.record(resp)
                log("talk_q", "id" to q.id, "type" to q.type.name, "latency_ms" to r.latencyMs, "correct" to correct, "valid" to resp.valid)
                // 대답에서 슬롯을 꺼내고 호응 + 이어지는 질문을 정한다(판정하지 않음).
                val step = planner.onAnswer(r.text)
                val s1 = suspicion.s1(baseline.thresholdMs)
                val s2 = suspicion.s2()
                if (s1 || s2) {
                    // 호응은 버리고, 이어질 질문은 '아니' 뒤에 쓰려고 남겨 둔다(5-5).
                    pendingStep = step.question?.let { TalkStep(null, it) }
                    log("suspect", "signal" to if (s1) "S1" else "S2")
                    return enterConfirm(ConfirmMode.ASK, if (s1) "S1" else "S2")
                }
                // W-05b: 인식 결과를 2초 보여 준다.
                heard = r.text
                heardCorrect = correct
                phase = VoicePhase.RESULT
                val roundFull = questionNo >= config.talkRoundMax
                if (step.question != null && !roundFull) {
                    // 같은 줄기: 2초 뒤 "호응 + 이어지는 질문"을 함께 말한다.
                    pendingStep = step
                    after(T_RESULT, config.talkResultShowS * SECOND_MS) {
                        heard = null
                        heardCorrect = null
                        askNextTalk()
                    }
                } else {
                    // 줄기 끝(또는 라운드 끝): 호응만 하고, 다음 주제는 문항 간격 뒤에.
                    pendingStep = null
                    planner.dropThread()
                    step.ack?.let { say(Line("V-11", it)) }
                    after(T_RESULT, config.talkResultShowS * SECOND_MS) {
                        heard = null
                        heardCorrect = null
                        continueRound()
                    }
                }
            }
            ListenResult.None -> {
                noAnswerStreak++
                planner.dropThread()
                pendingStep = null
                log("suspect", "signal" to "S3")
                enterConfirm(ConfirmMode.RECALL, "S3")
            }
            // 인식 실패는 무응답으로 세지 않는다. 문항을 버리고 이어 간다.
            ListenResult.Failed -> {
                planner.dropThread()
                pendingStep = null
                continueRound()
            }
        }
    }

    private fun continueRound() {
        if (state != S04) go(S04, "continue")
        if (questionNo >= config.talkRoundMax) return endRound("round_done")
        phase = VoicePhase.WAITING
        val minutes = if (shortInterval) config.talkIntervalShortMin else config.talkIntervalMin
        after(T_NEXT, scaled(minutes * MINUTE_MS)) { askNextTalk() }
    }

    private fun endRound(reason: String) {
        // TODO(4-2): C1 + 야간이면 의심 신호가 없어도 라운드 끝에 쉼터 제안 1회(5-3 강제 제안)
        nextRoundAtMs = clock.nowMs() + scaled(config.talkRoundGapMin * MINUTE_MS)
        shortInterval = false
        pendingStep = null
        planner.dropThread()
        go(S03, reason)
    }

    private fun pause() {
        pausedFrom = state
        pausedAtMs = clock.nowMs()
        // 말하거나 듣던 문항은 버리고 다시 묻지 않는다(F4). 아직 말하지 않은 이어질 질문은 남긴다.
        if (talkInProgress) {
            talkInProgress = false
            planner.dropThread()
            pendingStep = null
        }
        log("talk_paused", "reason" to signals.loadReasons.joinToString(",") { it.name })
        go(S05, "high_load")
        if (!signals.highLoad) after(T_RESUME, config.talkResumeAfterLoadS * SECOND_MS) { resume() }
    }

    /** 재개: 남겨 둔 이어질 질문이 있으면 그것을, 없으면 새 주제로. 5분 넘게 멈췄으면 라운드를 처음부터. */
    private fun resume() {
        val pausedMs = clock.nowMs() - pausedAtMs
        log("talk_resumed", "after_s" to pausedMs / SECOND_MS)
        if (pausedMs > config.talkPauseResetMin * MINUTE_MS) {
            questionNo = 0
            suspicion.newRound()
            pendingStep = null
            planner.dropThread()
        }
        when (pausedFrom) {
            S06 -> {
                go(S06, "resume")
                askConfirm()
            }
            else -> {
                go(S04, "resume")
                if (questionNo >= config.talkRoundMax) endRound("round_done") else askNextTalk()
            }
        }
    }

    // ════════════════════════════════════════ S-06 의심 확인 · S-07 경고

    private fun enterConfirm(mode: ConfirmMode, reason: String) {
        confirmMode = mode
        if (state != S06) go(S06, reason) else cancelVoice()
        askConfirm()
    }

    private fun askConfirm() {
        val recall = confirmMode == ConfirmMode.RECALL
        if (recall) log("reprompt")
        val line = if (recall) VoiceLines.V14 else VoiceLines.V15
        askQuestion(line, louder = recall, secondRetry = VoiceLines.V16) { r -> onConfirmAnswer(r) }
    }

    private fun onConfirmAnswer(r: ListenResult) {
        when (r) {
            is ListenResult.Answer -> {
                noAnswerStreak = 0
                // "제 말 들리세요?"에 대답하면 의심 확인으로
                if (confirmMode == ConfirmMode.RECALL) {
                    confirmMode = ConfirmMode.ASK
                    return askConfirm()
                }
                when (Grammar.intentIn(r.text, S06)) {
                    // TODO(4-2): S-06의 "그만" 처리가 SPEC에 없다. 일단 '아니'와 같게 본다.
                    UserIntent.NO, UserIntent.STOP -> {
                        log("suspect_answer", "answer" to "no")
                        noStreak++
                        if (noStreak >= config.suspectNoRepeat && suspicion.s1(baseline.thresholdMs)) {
                            offerRest(warnFirst = false, reason = "A6")
                        } else {
                            // "알겠어요."로 받고 바로 다음 질문 하나. 그 뒤 문항 간격은 3분(5-5).
                            shortInterval = true
                            go(S04, "confirm_no")
                            if (questionNo >= config.talkRoundMax) {
                                pendingStep = null
                                say(VoiceLines.V11_OK) { endRound("round_done") }
                            } else {
                                val more = if (pendingStep?.question != null) {
                                    planner.subject?.let { VoiceLines.fill(VoiceLines.V12_MORE, "이름" to it) }
                                        ?: VoiceLines.V12_MORE_GENERIC
                                } else {
                                    null
                                }
                                askNextTalk(prefix = listOfNotNull(VoiceLines.V11_OK.text, more).joinToString(" "))
                            }
                        }
                    }
                    // '응', 애매한 답, "졸려", 알아듣지 못한 말 → 안전 쪽으로 '응'(A5)
                    else -> {
                        log("suspect_answer", "answer" to "yes")
                        offerRest(warnFirst = true, reason = "A5")
                    }
                }
            }
            ListenResult.None -> {
                noAnswerStreak++
                log("suspect_answer", "answer" to "none")
                if (noAnswerStreak >= 2) {
                    enterWarn()
                } else {
                    confirmMode = ConfirmMode.RECALL
                    askConfirm()
                }
            }
            // V-13, V-16으로도 못 알아들으면 문항을 버리고 대화로 돌아간다.
            ListenResult.Failed -> {
                pendingStep = null
                planner.dropThread()
                continueRound()
            }
        }
    }

    private fun enterWarn() {
        summary = summary.copy(warns = summary.warns + 1)
        warnLevel = 0
        go(S07, "no_answer_twice")
        warnStep()
    }

    private fun warnStep() {
        warnLevel++
        log("warn_level", "level" to warnLevel)
        val last = warnLevel >= config.warnLevels
        play(Sound.Warn(warnLevel)) {
            if (last) say(VoiceLines.V20) { offerRest(warnFirst = false, reason = "after_warn") }
        }
        if (!last) after(T_STEP, config.warnStepS * SECOND_MS) { warnStep() }
        // TODO(4-2): 경고 중 브레이크 → 1단계로 낮춤(A7). "그만" → 미루기(S-10).
    }

    // ════════════════════════════════════════ S-08 쉼터 제안 · S-09 이동 · S-10 미루기

    private fun offerRest(warnFirst: Boolean, reason: String) {
        go(S08, reason)
        val pick = positionKm?.let { route?.offer(it, config.restSearchKm) }
        offer = pick?.first
        nextCandidate = pick?.second
        offerAsks = 0
        waypointAdded = null
        announcedKm.clear()
        arriveSignal = false
        if (warnFirst) {
            warnLevel = 1
            log("warn_level", "level" to 1)
            play(Sound.Warn(1)) { askOffer() }
        } else {
            askOffer()
        }
    }

    private fun askOffer() {
        val o = offer
        val pos = positionKm
        if (o == null || pos == null) {
            // TODO(4-2): E-8 내비 검색어 안내. 쉼터 데이터 없음
            say(VoiceLines.V22B)
            return
        }
        val km = formatKm(o.km - pos)
        val line = if (o.type == RestAreaType.DROWSY_SHELTER) VoiceLines.v21(km, o.name) else VoiceLines.v22(km, o.name)
        log("rest_offer_shown", "km" to km, "type" to o.type.name)
        askQuestion(line, windowMs = config.offerWaitS * SECOND_MS) { r ->
            when (r) {
                is ListenResult.Answer -> when (Grammar.intentIn(r.text, S08)) {
                    UserIntent.YES -> {
                        log("rest_offer_answer", "answer" to "yes")
                        goToRest()
                    }
                    UserIntent.NO, UserIntent.STOP -> {
                        log("rest_offer_answer", "answer" to "no")
                        snooze()
                    }
                    else -> askOffer()
                }
                // TODO(4-2): 무응답 10초 × 2 → 자동 추가(T14 결정 전). 지금은 다시 묻기만 한다.
                ListenResult.None, ListenResult.Failed -> {
                    offerAsks++
                    log("rest_offer_answer", "answer" to "none", "count" to offerAsks)
                    askOffer()
                }
            }
        }
    }

    private fun goToRest() {
        val o = offer ?: return
        go(S09, "offer_yes")
        val t = nextToken()
        waypointToken = t
        waypointRequestedAtMs = clock.nowMs()
        effects.addWaypoint(t, o)
        announceApproach()
    }

    private fun snooze() {
        snoozeCount++
        log("rest_offer_snoozed", "count" to snoozeCount)
        go(S10, "snooze")
        say(VoiceLines.V27)
        // TODO(4-2): min(15분, 다음 쉼터 5km 전), 미루기 3회 V-28, 그동안 대화 간격 3분
        after(T_NEXT, scaled(config.snoozeMin * MINUTE_MS)) { offerRest(warnFirst = snoozeCount >= 2, reason = "snooze_over") }
    }

    /** 남은 3km·1km에서 V-25를 1회씩. 한 번에 여러 지점을 넘으면 가까운 것 하나만 말한다. 내비 안내 중이면 생략(A8). */
    private fun announceApproach() {
        val o = offer ?: return
        val pos = positionKm ?: return
        val left = o.km - pos
        val crossed = config.approachNoticeKm.filter { left <= it && it !in announcedKm }
        if (crossed.isEmpty()) return
        announcedKm += crossed
        val point = crossed.min()
        log("rest_approach", "km" to point)
        if (signals.navVoice == true) return
        say(VoiceLines.v25(formatKm(point), o.name, o.exit))
    }

    /** 도착 판정(F9): (도착 신호 또는 반경 150m 안) 그리고 기어 P 그리고 속도 0. */
    private fun checkArrival() {
        if (state != S09) return
        val o = offer ?: return
        val near = positionKm?.let { abs(o.km - it) * 1000 <= config.arriveRadiusM } ?: false
        if (!(arriveSignal || near) || !stopped) return
        log("arrived", "method" to if (arriveSignal) "callback" else "proximity")
        arrivedMinute = minute()
        positionKm = o.km
        go(S11, "arrived")
        askQuestion(listOf(VoiceLines.V30_A, VoiceLines.V30_B), config.offerWaitS * SECOND_MS) { r ->
            when {
                r is ListenResult.Answer && Grammar.intentIn(r.text, S11) == UserIntent.YES ->
                    startRest("rest", config.restDefaultMin)
                r is ListenResult.Answer -> {
                    // TODO(4-2): "몸 깨울래" → V-32 → S-13, '아니' 처리
                    startRest("auto", config.restDefaultMin)
                }
                else -> startRest("auto", config.restDefaultMin)
            }
        }
    }

    // ════════════════════════════════════════ S-11 도착 · S-12 휴식 · S-14 재출발 · S-16 출발 안내

    private fun startRest(choice: String, minutes: Int) {
        log("rest_choice", "choice" to choice)
        go(S12, "rest_$choice")
        restStartedAtMs = clock.nowMs()
        restTotalMs = restActualMs(minutes * MINUTE_MS)
        restNominalMs = minutes * MINUTE_MS
        restExtensions = 0
        restRefusedSaid = false
        restEndsAtMs = restStartedAtMs + restTotalMs
        alarmLevel = 0
        summary = summary.copy(rests = summary.rests + 1)
        log("rest_started", "min" to minutes)
        say(listOf(VoiceLines.V34_RECLINE, VoiceLines.v33(minutes)))
        after(T_REST_DONE, restTotalMs) {
            // 휴식 완료: 앱이 시작한 휴식에서 P가 휴식 길이만큼 이어짐 → 연속 주행 시간 0 (5-2). 연장과 상관없이 처음 길이에서 한 번.
            log("rest_completed", "min" to minutes, "app_induced" to true)
            triggers.reset()
        }
        scheduleRestEnd(restTotalMs)
        // TODO(4-2): 휴식 중 브레이크 → "등받이를 세워 주세요"(A14)
    }

    /** 화면·대사 기준 길이를 실제 타이머 길이로: 시연 휴식(restSec)은 15분 → 그 초, 아니면 배속만. */
    private fun restActualMs(nominalMs: Long): Long =
        restSecOverride?.let { it * SECOND_MS * nominalMs / (config.restDefaultMin * MINUTE_MS) } ?: scaled(nominalMs)

    /**
     * 지금부터 [leftMs] 뒤에 기상 알람. 종료 전 미리 알림은 하지 않는다: 운전자가 1분이라도 더 쉬게(2026-11-14 PM 결정).
     * "등받이를 세워 주세요"는 기상 알람의 V-35에서만 말한다.
     */
    private fun scheduleRestEnd(leftMs: Long) {
        after(T_REST_END, leftMs) { alarmStep() }
    }

    /**
     * "5분만" / "+ 5분" (F10 7번): 5분 연장, 최대 3회. 알람이 울리는 중이면 알람을 멈추고 지금부터 5분.
     * 4회째 요청에는 연장하지 않고 V-36을 한 번 말한다(AC-10.4).
     */
    private fun extendRest(source: String) {
        if (state != S12) return
        val ringing = alarmLevel > 0
        if (restExtensions >= config.restExtendMax) {
            log("rest_extend_refused", "source" to source)
            if (!restRefusedSaid) {
                restRefusedSaid = true
                if (listenToken != NONE) effects.stopListening()
                listenToken = NONE
                onListen = null
                say(VoiceLines.V36) { if (alarmLevel > 0) listenForAwake() }
            }
            return
        }
        restExtensions++
        log("rest_extended", "count" to restExtensions, "source" to source)
        val now = clock.nowMs()
        val addActual = restActualMs(config.restExtendMin * MINUTE_MS)
        if (ringing) {
            cancelVoice()
            timers.remove(T_STEP)
            alarmLevel = 0
        }
        val end = if (ringing) now + addActual else (restEndsAtMs ?: now) + addActual
        restEndsAtMs = end
        restTotalMs = end - restStartedAtMs
        restNominalMs = restTotalMs * config.restDefaultMin * MINUTE_MS / restActualMs(config.restDefaultMin * MINUTE_MS)
        scheduleRestEnd(end - now)
        val leftMin = (((end - now) * restNominalMs / restTotalMs + MINUTE_MS - 1) / MINUTE_MS).toInt()
        say(VoiceLines.v33(leftMin))
    }

    /**
     * 기상 알람(F10 5번): 1단계부터 30초마다 한 단계씩 커져 3단계까지. 소리 파일 자체가 단계마다 커진다(알람 천천히 커짐).
     * 3단계까지 올라도 반응이 없으면 3단계를 같은 간격으로 계속 울린다(F10 예외 "알람 3단계 유지", 2026-11-14 구현).
     */
    private fun alarmStep() {
        val first = alarmLevel == 0
        alarmLevel = (alarmLevel + 1).coerceAtMost(config.alarmLevels)
        log("wake_alarm", "level" to alarmLevel)
        // 다음 단계 간격은 이 단계의 소리(1단계는 V-35 문장까지)가 끝난 뒤부터 센다.
        // 시연 배속에서 간격이 짧아져도 알람 소리와 "일어나실 시간이에요"가 끊기지 않게 한다(2026-11-13).
        val next = { after(T_STEP, scaled(config.alarmStepS * SECOND_MS)) { alarmStep() } }
        play(Sound.Alarm(alarmLevel)) {
            if (first) {
                // V-35의 분 = 실제로 쉰 길이(기본 15, 연장 20·25·30). W-13b 화면과 같은 값(restNominalMs)을 쓴다.
                say(VoiceLines.v35(((restNominalMs + MINUTE_MS / 2) / MINUTE_MS).toInt())) {
                    listenForAwake()
                    next()
                }
            } else {
                next()
            }
        }
    }

    private fun listenForAwake() {
        listen(config.offerWaitS * SECOND_MS) { r ->
            when ((r as? ListenResult.Answer)?.let { Grammar.intentIn(it.text, S12) }) {
                UserIntent.AWAKE -> awake()
                UserIntent.SNOOZE_5 -> extendRest("voice")
                else -> listenForAwake()
            }
        }
    }

    private fun awake() {
        val elapsed = clock.nowMs() - restStartedAtMs
        val nominalMs = if (restTotalMs > 0) elapsed * restNominalMs / restTotalMs else elapsed
        summary = summary.copy(restMinutes = summary.restMinutes + (nominalMs / MINUTE_MS).toInt())
        log("rest_awake", "after_min" to nominalMs / MINUTE_MS)
        restEndsAtMs = null
        go(S14, "awake")
        startRecheck()
    }

    private fun startRecheck() {
        recheckIndex = 0
        recheckLatencies.clear()
        questionNo = 0
        questionTotal = config.recheckQuestions
        // "등받이를 세워 주세요"는 기상 알람 V-35에서 이미 말했다(그 안내 전에는 질문하지 않음, AC-11.1). 다시 말하지 않고
        // 등받이를 세울 시간 5초 뒤 질문한다(2026-11-14 PM: 이 문장은 V-35에서만).
        // 스토리보드 장면 20: "출발 전에 세 가지만 물어볼게요." + "12 빼기 4는요?"를 한 번에 말한다.
        after(T_NEXT, config.recheckBackrestWaitS * SECOND_MS) { askRecheck(intro = true) }
    }

    private fun askRecheck(intro: Boolean = false) {
        val q = QuestionBank.recheck[recheckIndex % QuestionBank.recheck.size]
        questionNo = recheckIndex + 1
        loadDuringQuestion = signals.highLoad
        clearHeard()
        val lines = listOfNotNull(VoiceLines.V50.takeIf { intro }, Line(q.id, q.text))
        askQuestion(lines, config.talkNoAnswerS * SECOND_MS) { r ->
            if (r is ListenResult.Answer) {
                // 화면(W-15): 다음 문항 전까지 들은 대답을 보여 준다(판정은 보여 주지 않음).
                heard = r.text
                heardCorrect = null
                phase = VoicePhase.RESULT
                val correct = QuestionBank.judge(q, r.text, memory)
                val resp = response(q, r, correct)
                if (resp.valid) recheckLatencies += resp.latencyMs
                val b = baseline.baselineMs ?: config.suspectTempMs
                log("recheck_q", "latency_ms" to r.latencyMs, "ratio" to r.latencyMs?.let { "%.2f".format(it.toDouble() / b) })
            }
            recheckIndex++
            if (recheckIndex < config.recheckQuestions) {
                val gap = if (r is ListenResult.Answer) config.talkResultShowS * SECOND_MS else config.precheckGapMs
                after(T_NEXT, gap) { askRecheck() }
            } else {
                finishRecheck()
            }
        }
    }

    private fun finishRecheck() {
        val b = baseline.baselineMs ?: config.suspectTempMs
        val avg = recheckLatencies.takeIf { it.isNotEmpty() }?.average()
        val pass = avg != null && avg <= b * config.recheckRatio
        log("recheck_result", "pass" to pass, "avg_ms" to avg?.toLong(), "baseline_ms" to b)
        if (pass) {
            go(S16, "recheck_pass")
            val pos = positionKm
            val next = pos?.let { route?.nextAfter(it, config.nextRestMaxKm) }
            if (pos != null && next != null) say(VoiceLines.v52(formatKm(next.km - pos)))
            // TODO(4-2): 경로 정보가 없을 때의 출발 안내 문장
        } else {
            moreRestCount++
            go(S15, "recheck_more")
            log("recheck_more_shown")
            val minutes = if (moreRestCount >= 3) config.restDefaultMin else REST_MORE_MIN
            askQuestion(VoiceLines.v51(minutes), windowMs = config.offerWaitS * SECOND_MS) { r ->
                val yesOrNone = r !is ListenResult.Answer || Grammar.intentIn(r.text, S15) == UserIntent.YES
                if (yesOrNone) startRest("more", minutes)
            }
        }
    }

    // ════════════════════════════════════════ 음성 도우미

    private fun say(line: Line, louder: Boolean = false, then: () -> Unit = {}) = say(listOf(line), louder, then)

    private fun say(lines: List<Line>, louder: Boolean = false, then: () -> Unit = {}) {
        if (speechToken != NONE) effects.stopSpeaking()
        val t = nextToken()
        speechToken = t
        afterSpeech = then
        caption = lines
        if (phase != VoicePhase.RESULT) phase = VoicePhase.SPEAKING
        effects.speak(t, lines, louder)
    }

    /** 인식 결과 표시를 지우고, 다음 말이 "말하는 중"으로 보이게 한다. */
    private fun clearHeard() {
        heard = null
        heardCorrect = null
        if (phase == VoicePhase.RESULT) phase = VoicePhase.IDLE
    }

    private fun listen(windowMs: Long, handler: (ListenResult) -> Unit) {
        val t = nextToken()
        listenToken = t
        onListen = handler
        phase = VoicePhase.LISTENING
        listenEndsAtMs = clock.nowMs() + windowMs
        effects.listen(t, windowMs)
    }

    private fun deliverListen(token: Long, result: ListenResult) {
        if (token != listenToken) return
        listenToken = NONE
        listenEndsAtMs = null
        if (phase == VoicePhase.LISTENING) phase = VoicePhase.IDLE
        val handler = onListen
        onListen = null
        pendingAnswerHandler = null
        handler?.invoke(result)
        publish()
    }

    /**
     * 묻고 듣는다. 인식 실패는 V-13으로 한 번 되묻고, [secondRetry]가 있으면 한 번 더 묻는다(E-1).
     * 되물은 뒤의 대답은 응답 시간 판정에 넣지 않는다.
     */
    private fun askQuestion(
        line: Line,
        windowMs: Long = config.talkNoAnswerS * SECOND_MS,
        louder: Boolean = false,
        secondRetry: Line? = null,
        handler: (ListenResult) -> Unit,
    ) = askQuestion(listOf(line), windowMs, louder, secondRetry, handler)

    private fun askQuestion(
        lines: List<Line>,
        windowMs: Long,
        louder: Boolean = false,
        secondRetry: Line? = null,
        handler: (ListenResult) -> Unit,
    ) {
        var fails = 0
        fun attempt(speak: List<Line>) {
            pendingAnswerHandler = handler
            say(speak, louder) {
                listen(windowMs) { r ->
                    if (r is ListenResult.Failed) {
                        fails++
                        log("stt_fail", "count" to fails)
                        val retry = when (fails) {
                            1 -> VoiceLines.V13
                            2 -> secondRetry
                            else -> null
                        }
                        if (retry != null) return@listen attempt(listOf(retry))
                    }
                    val final = if (fails > 0 && r is ListenResult.Answer) r.copy(retried = true) else r
                    handler(final)
                }
            }
        }
        attempt(lines)
    }

    private fun play(sound: Sound, then: () -> Unit = {}) {
        val t = nextToken()
        soundToken = t
        afterSound = then
        effects.playSound(t, sound)
    }

    private fun cancelVoice() {
        if (speechToken != NONE) effects.stopSpeaking()
        if (listenToken != NONE) effects.stopListening()
        if (soundToken != NONE) effects.stopSound()
        speechToken = NONE
        listenToken = NONE
        soundToken = NONE
        waypointToken = NONE
        afterSpeech = null
        onListen = null
        afterSound = null
        pendingAnswerHandler = null
        listenEndsAtMs = null
        phase = VoicePhase.IDLE
    }

    // ════════════════════════════════════════ 공통

    private fun go(next: WakeonState, reason: String) {
        val prev = state
        cancelVoice()
        timers.clear()
        state = next
        if (next != S04 && next != S05 && next != S06) {
            heard = null
            heardCorrect = null
        }
        if (next != S07 && next != S08) warnLevel = 0
        if (prev != next) eventLog.event("state", mapOf("from" to prev.id, "to" to next.id, "reason" to reason))
        publish()
    }

    private fun after(name: String, delayMs: Long, action: () -> Unit) {
        timers[name] = Timer(clock.nowMs() + delayMs, action)
    }

    private fun runDueTimers(now: Long) {
        while (true) {
            val due = timers.entries.firstOrNull { it.value.atMs <= now } ?: return
            timers.remove(due.key)
            due.value.action()
        }
    }

    /** 시연 배속은 문항 간격·라운드 간격·휴식·알람 간격에만 쓴다. 무응답 5초·경고 5초·제안 대기 10초는 그대로. */
    private fun scaled(ms: Long): Long = (ms / timeScale).toLong()

    private fun minute() = wallClock.minuteOfDay()

    private fun nextToken() = ++tokenSeq

    private fun response(q: Question, r: ListenResult.Answer, correct: Boolean) = Response(
        latencyMs = r.latencyMs ?: 0,
        correct = correct,
        valid = q.measured && r.latencyMs != null && !r.retried && !loadDuringQuestion,
    )

    private fun remember(q: Question, answer: String) {
        if (q.type == QuestionType.MEMORY_CAPTURE && q.memoryKey != null) {
            val core = QuestionBank.memoryCore(answer)
            if (core.isNotEmpty()) memory[q.memoryKey] = core
        }
    }

    private fun etaMinutes(o: RestArea): Int? {
        val pos = positionKm ?: return null
        val speed = signals.speedKmh?.takeIf { it > 1f } ?: return null
        return ceil((o.km - pos) / speed * 60).toInt()
    }

    private fun tapWord(intent: UserIntent) = when (intent) {
        UserIntent.YES -> "응"
        UserIntent.NO -> "아니"
        UserIntent.AWAKE -> "일어났어"
        else -> ""
    }

    private fun log(name: String, vararg params: Pair<String, Any?>) = eventLog.event(name, params.toMap())

    private fun publish() {
        val now = clock.nowMs()
        val o = offer
        val pos = positionKm
        _snapshot.value = WakeonSnapshot(
            state = state,
            phase = phase,
            caption = caption,
            heard = heard,
            heardCorrect = heardCorrect,
            questionNo = questionNo,
            questionTotal = questionTotal,
            gear = gear,
            speedKmh = signals.speedKmh,
            loadReasons = signals.loadReasons,
            driveMs = triggers.driveMs,
            nextTalkInMs = if (state == S03) triggers.nextTalkInMs(minute(), (nextRoundAtMs - now).coerceAtLeast(0)) else null,
            minuteOfDay = minute(),
            precheck = precheckSummary,
            baselineSamples = baseline.count,
            baselineMs = baseline.baselineMs,
            noAnswerCount = noAnswerStreak,
            warnLevel = warnLevel,
            offer = if (o != null && pos != null) {
                RestOffer(o, o.km - pos, etaMinutes(o), nextCandidate, nextCandidate?.let { it.km - pos })
            } else {
                null
            },
            waypointAdded = waypointAdded,
            listenEndsAtMs = listenEndsAtMs,
            restEndsAtMs = restEndsAtMs,
            restTotalMs = restTotalMs,
            restNominalMs = restNominalMs,
            alarmLevel = alarmLevel,
            arrivedMinuteOfDay = arrivedMinute,
            summary = summary,
            triggersEnabled = triggers.enabled.toSet(),
            nowMs = now,
        )
    }

    companion object {
        private const val NONE = -1L
        private const val REST_MORE_MIN = 5

        private const val T_NEXT = "next"
        private const val T_RESULT = "result"
        private const val T_RESUME = "resume"
        private const val T_STEP = "step"
        private const val T_REST_END = "rest_end"
        private const val T_REST_DONE = "rest_done"

        /** 주행 쪽 상태. 이 상태에서 P가 10분 이어지면 세션을 끝낸다. */
        private val DRIVE_STATES = setOf(S03, S04, S05, S06, S07, S08, S09, S10)

        /** 거리 표기: 10km 이상은 정수, 미만은 소수 한 자리(정수면 정수). */
        fun formatKm(km: Double): String {
            if (km >= 10) return km.roundToInt().toString()
            val r = (km * 10).roundToInt() / 10.0
            return if (r == r.toInt().toDouble()) r.toInt().toString() else r.toString()
        }
    }
}
