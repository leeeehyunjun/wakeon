package com.wakeon.app.core

import com.wakeon.app.config.WakeonConfig
import com.wakeon.app.config.WakeonConfig.Companion.MINUTE_MS
import com.wakeon.app.core.WakeonState.S01
import com.wakeon.app.core.WakeonState.S02
import com.wakeon.app.core.WakeonState.S03
import com.wakeon.app.core.WakeonState.S04
import com.wakeon.app.core.WakeonState.S05
import com.wakeon.app.core.WakeonState.S06
import com.wakeon.app.core.WakeonState.S07
import com.wakeon.app.core.WakeonState.S08
import com.wakeon.app.core.WakeonState.S09
import com.wakeon.app.core.WakeonState.S11
import com.wakeon.app.core.WakeonState.S12
import com.wakeon.app.core.WakeonState.S14
import com.wakeon.app.core.WakeonState.S16
import com.wakeon.app.nav.DemoRoute
import com.wakeon.app.nav.RestArea
import com.wakeon.app.nav.RestAreaType
import com.wakeon.app.vehicle.Gear
import com.wakeon.app.vehicle.TurnSignal
import com.wakeon.app.vehicle.VehicleSignals
import com.wakeon.app.voice.Line
import com.wakeon.app.voice.QuestionBank
import com.wakeon.app.voice.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 상태 머신을 가짜 시계·가짜 음성으로 돌리는 도구. */
private class Harness(consented: Boolean = true) {
    var now = 1_000_000L
    var minute = 21 * 60 + 4
    val logs = mutableListOf<Pair<String, Map<String, Any?>>>()
    val fx = FakeEffects()
    val route = DemoRoute(
        "경부고속도로 · 부산 방향", 400.0,
        listOf(
            RestArea("○○졸음쉼터", RestAreaType.DROWSY_SHELTER, 164.0, "오른쪽"),
            RestArea("△△휴게소", RestAreaType.SERVICE_AREA, 179.0, "오른쪽"),
            RestArea("다음 졸음쉼터", RestAreaType.DROWSY_SHELTER, 202.0, "오른쪽"),
        ),
    )
    val m = WakeonStateMachine(WakeonConfig(), { now }, { minute }, fx, { n, p -> logs += n to p }, route, consented)

    val state get() = m.state
    val snap get() = m.snapshot.value

    fun tick(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(200L, left)
            now += step
            left -= step
            m.tick()
        }
    }

    /** 기어 잔떨림 확정(1초)까지 기다린다. */
    fun vehicle(gear: Gear, speed: Float = 0f, brake: Boolean = false, turn: TurnSignal = TurnSignal.OFF) {
        m.onVehicle(VehicleSignals(gear, speed, brake, turn, navVoice = false))
        tick(1_100)
    }

    fun finishSpeech() {
        val (t, _) = fx.speech ?: return
        fx.speech = null
        m.onSpeechDone(t)
    }

    fun finishSound() {
        val (t, _) = fx.sound ?: return
        fx.sound = null
        m.onSoundDone(t)
    }

    /** 말과 알림음을 끝내고 시간을 보내며 듣기 창이 열릴 때까지. */
    fun untilListening(maxMs: Long = 30 * MINUTE_MS): Line? {
        var waited = 0L
        while (fx.listening == null) {
            when {
                fx.speech != null -> finishSpeech()
                fx.sound != null -> finishSound()
                else -> {
                    tick(200)
                    waited += 200
                    check(waited < maxMs) { "듣기 창이 열리지 않음 (상태 ${m.state})" }
                }
            }
        }
        return fx.lastLines.lastOrNull()
    }

    fun answer(text: String, latencyMs: Long) {
        untilListening()
        val t = fx.listening!!
        fx.listening = null
        tick(latencyMs)
        m.onHeard(t, text, latencyMs)
    }

    /** 지금 질문에 맞는 대답을 한다(셈·선택·끝말잇기). */
    fun answerCorrectly(latencyMs: Long) {
        val line = untilListening()!!
        val q = QuestionBank.all.first { it.text == line.text }
        val text = when (q.type) {
            QuestionType.ARITH -> q.answer.toString()
            QuestionType.CHOICE -> q.options.first()
            QuestionType.WORD_CHAIN -> "${q.seed!!.last()}리"
            else -> "모름"
        }
        answer(text, latencyMs)
    }

    fun noAnswer() {
        untilListening()
        val t = fx.listening!!
        fx.listening = null
        tick(5_000)
        m.onNoAnswer(t)
    }

    fun drain() {
        while (fx.speech != null || fx.sound != null) {
            if (fx.speech != null) finishSpeech() else finishSound()
        }
    }

    fun spokenIds() = fx.spoken.map { it.id }
    fun states() = logs.filter { it.first == "state" }.map { it.second["to"] as String }
}

private class FakeEffects : Effects {
    var speech: Pair<Long, List<Line>>? = null
    var listening: Long? = null
    var sound: Pair<Long, Sound>? = null
    var waypoint: Pair<Long, RestArea>? = null
    val spoken = mutableListOf<Line>()
    val sounds = mutableListOf<Sound>()
    var lastLines: List<Line> = emptyList()
    var stopSpeakingCalls = 0

    override fun speak(token: Long, lines: List<Line>, louder: Boolean) {
        speech = token to lines
        lastLines = lines
        spoken += lines
    }

    override fun stopSpeaking() {
        speech = null
        stopSpeakingCalls++
    }

    override fun listen(token: Long, windowMs: Long) {
        listening = token
    }

    override fun stopListening() {
        listening = null
    }

    override fun playSound(token: Long, sound: Sound) {
        this.sound = token to sound
        sounds += sound
    }

    override fun stopSound() {
        sound = null
    }

    override fun addWaypoint(token: Long, rest: RestArea) {
        waypoint = token to rest
    }
}

class StateMachineTest {

    @Test
    fun `시연 23장면 경로 - 출발 전 체크부터 재출발까지 한 번에 돈다`() {
        val h = Harness()
        h.m.demoTimeScale(60f)
        h.m.demoRestSeconds(30)
        h.vehicle(Gear.P)

        // 장면 1~4: S-01 → S-02 → S-01, 기준선 1.0초 확정
        h.m.demoReset()
        assertEquals(S01, h.state)
        assertEquals("V-02", h.untilListening()?.id)
        h.answer("응, 부산까지.", 1_000)
        assertEquals(S02, h.state)
        assertEquals("V-03", h.untilListening()?.id)
        h.answer("삼.", 1_000)
        assertEquals("7 더하기 5는요?", h.untilListening()?.text)
        h.answer("십이.", 1_000)
        h.answer("김치찌개요", 1_000)
        h.answer("네 시간 반이요", 1_000)
        assertEquals(S01, h.state)
        assertEquals(PrecheckSummary(kss = 3, correct = 3, total = 3, baselineMs = 1_000), h.snap.precheck)
        assertEquals("V-06", h.fx.lastLines.single().id)
        h.drain()

        // 장면 5: D, 98km/h, 22:46, 연속 1시간 42분 → S-03, "18분 뒤"
        h.vehicle(Gear.D, 98f)
        assertEquals(S03, h.state)
        h.minute = 22 * 60 + 46
        h.m.demoDriveMinutes(102)
        h.tick(1_000)
        assertEquals(S03, h.state)
        // 화면은 분 단위로 올려서 보여 준다(17분 59초 → "18분 뒤")
        assertEquals(18L, (h.snap.nextTalkInMs!! + MINUTE_MS - 1) / MINUTE_MS)

        // 장면 6: 연속 2시간 4분(C1) → S-04. 1·2번 질문은 앱이 묻고 편집에서 자른다.
        h.minute = 23 * 60 + 8
        h.m.demoDriveMinutes(124)
        h.tick(200)
        assertEquals(S04, h.state)
        h.answer("구", 1_000)
        h.answer("강아지", 1_000)
        assertEquals("V-10", h.untilListening()?.id)
        assertEquals(3, h.snap.questionNo)
        h.answer("김치찌개요.", 1_200)
        // 장면 7: W-05b 인식 결과 + "맞아요." 뒤 끝말잇기
        assertEquals("김치찌개요.", h.snap.heard)
        assertEquals("V-11", h.fx.lastLines.single().id)
        assertEquals("V-12", h.untilListening()?.id)
        h.answer("차표.", 1_700)

        // 장면 8: 브레이크 + 방향지시등 → S-05, 꺼지고 10초 뒤 새 문항으로 재개
        h.vehicle(Gear.D, 84f, brake = true, turn = TurnSignal.RIGHT)
        assertEquals(S05, h.state)
        h.vehicle(Gear.D, 99f)
        assertEquals(S05, h.state)
        h.tick(10_000)
        assertEquals(S04, h.state)

        // 장면 9: 2.8초 → 최근 3개 중 2개 > 1.5초 → S1 → S-06
        assertEquals("아까 몇 시간 간다고 하셨죠?", h.untilListening()?.text)
        h.answer("…네 시간… 반?", 2_800)
        assertEquals(S06, h.state)
        assertEquals("V-15", h.untilListening()?.id)

        // 장면 10: "아니, 괜찮아." → S-04 (문항 간격 3분)
        h.answer("아니, 괜찮아.", 1_500)
        assertEquals(S04, h.state)

        // 장면 11~12: 사과! 무응답 → S-06 "제 말 들리세요?" → 다시 무응답
        assertEquals("끝말잇기 해요. 사과!", h.untilListening()?.text)
        assertEquals(6, h.snap.questionNo)
        h.noAnswer()
        assertEquals(S06, h.state)
        assertEquals("V-14", h.untilListening()?.id)
        assertEquals(1, h.snap.noAnswerCount)
        h.m.onRoutePosition(152.0)
        h.noAnswer()

        // 장면 13: 알림음 1→3단계(5초 간격) 뒤 V-20 → S-08
        assertEquals(S07, h.state)
        h.untilListening()
        assertEquals(listOf(1, 2, 3), h.fx.sounds.filterIsInstance<Sound.Warn>().map { it.level })
        assertTrue("V-20" in h.spokenIds())

        // 장면 14: "12km 앞 ○○졸음쉼터를 경유지로 넣을까요?" → "어… 응." → S-09
        assertEquals(S08, h.state)
        assertEquals("12km 앞 ○○졸음쉼터를 경유지로 넣을까요?", h.fx.lastLines.single().text)
        assertEquals("△△휴게소", h.snap.offer?.next?.name)
        h.answer("어… 응.", 1_300)
        assertEquals(S09, h.state)

        // 장면 15: 경유지 추가 성공 → V-24
        val (token, rest) = h.fx.waypoint!!
        assertEquals("○○졸음쉼터", rest.name)
        h.m.onWaypointResult(token, ok = true)
        assertEquals("○○졸음쉼터를 경유지로 넣었어요. 8분 뒤 도착해요.", h.fx.lastLines.single().text)
        h.drain()

        // 장면 16: 1km 전 안내
        h.m.onRoutePosition(163.0)
        assertEquals("1km 앞 ○○졸음쉼터예요. 오른쪽으로 빠지세요.", h.fx.lastLines.single().text)
        h.drain()

        // 장면 17: P · 0km/h + 도착 → S-11, 두 문장 → "응, 잘게." → S-12
        h.vehicle(Gear.P, 0f)
        h.m.onArriveSignal()
        assertEquals(S11, h.state)
        h.untilListening()
        assertEquals(listOf("도착했어요. 15분 쉬어 갈까요?", "커피가 있으면 지금 드세요."), h.fx.lastLines.map { it.text })
        h.answer("응, 잘게.", 1_200)
        assertEquals(S12, h.state)
        assertEquals(listOf("V-34", "V-33"), h.fx.lastLines.map { it.id })

        // 장면 18~19: 30초 휴식 → 5초 전 "등받이를 세워 주세요" → 알람 + V-35 → "일어났어" → S-14
        h.answer("으음… 일어났어.", 2_000)
        assertTrue(h.logs.any { it.first == "rest_completed" })
        assertTrue(h.fx.sounds.contains(Sound.Alarm(1)))
        assertEquals(S14, h.state)

        // 장면 20: 등받이 안내 → V-50 → 3문항 → 평균 ≤ B×1.5 → S-16
        assertEquals("12 빼기 4는요?", h.untilListening()?.text)
        assertTrue(h.spokenIds().takeLast(3).containsAll(listOf("V-34", "V-50")))
        h.answer("팔.", 1_100)
        h.answer("십삼", 1_000)
        h.answer("육", 1_000)
        assertEquals(S16, h.state)
        assertEquals("다음 쉼터는 38km 앞이에요. 졸리면 바로 쉬세요.", h.fx.lastLines.single().text)
        assertEquals(SessionSummary(rests = 1, restMinutes = h.snap.summary.restMinutes, warns = 1), h.snap.summary)
        h.drain()

        // 장면 22: D → S-03, 연속 주행 0분부터
        h.vehicle(Gear.D, 92f)
        assertEquals(S03, h.state)
        assertTrue(h.snap.driveMs < MINUTE_MS)

        assertEquals(
            listOf("S-01", "S-02", "S-01", "S-03", "S-04", "S-05", "S-04", "S-06", "S-04", "S-06", "S-07", "S-08", "S-09", "S-11", "S-12", "S-14", "S-16", "S-03"),
            h.states(),
        )
    }

    @Test
    fun `정답형 유효 응답이 3개 미만이면 V-07과 임시 기준`() {
        val h = Harness()
        h.vehicle(Gear.P)
        h.m.demoReset()
        h.answer("응", 1_000)
        h.answer("삼", 1_000)
        h.noAnswer()
        h.noAnswer()
        h.answer("네 시간", 1_000)
        assertEquals(S01, h.state)
        assertEquals("V-07", h.fx.lastLines.single().id)
        assertEquals(null, h.snap.precheck?.baselineMs)
    }

    @Test
    fun `체크 중 D로 바뀌면 말을 멈추고 S-03`() {
        val h = Harness()
        h.vehicle(Gear.P)
        h.m.demoReset()
        h.answer("응", 1_000)
        h.untilListening()
        val stops = h.fx.stopSpeakingCalls
        h.m.onVehicle(VehicleSignals(Gear.D, 10f))
        h.tick(1_100)
        assertEquals(S03, h.state)
        assertTrue(h.fx.stopSpeakingCalls >= stops)
        assertEquals(null, h.fx.listening)
    }

    @Test
    fun `고부하 중에는 트리거가 와도 대화를 시작하지 않는다`() {
        val h = Harness()
        h.vehicle(Gear.P)
        h.m.demoReset()
        h.drain()
        h.vehicle(Gear.D, 90f, brake = true)
        h.m.demoDriveMinutes(110)
        h.m.demoDriveMinutes(121)
        h.tick(2_000)
        assertEquals(S03, h.state)
        h.vehicle(Gear.D, 90f)
        h.tick(200)
        assertEquals(S04, h.state)
    }

    @Test
    fun `말하는 도중 고부하가 켜지면 바로 멈추고 진행 중 문항을 버린다`() {
        val h = Harness()
        h.vehicle(Gear.P)
        h.m.demoReset()
        h.drain()
        h.vehicle(Gear.D, 90f)
        h.m.demoDriveMinutes(121)
        h.tick(200)
        assertNotNull(h.fx.speech)
        h.m.onVehicle(VehicleSignals(Gear.D, 90f, brake = true))
        assertEquals(S05, h.state)
        assertEquals(null, h.fx.speech)
    }

    @Test
    fun `대화 중 "그만"은 이번 라운드만 멈춘다`() {
        val h = Harness()
        h.vehicle(Gear.P)
        h.m.demoReset()
        h.drain()
        h.vehicle(Gear.D, 90f)
        h.m.demoDriveMinutes(121)
        h.tick(200)
        h.answer("그만해", 1_000)
        assertEquals(S03, h.state)
        assertTrue(h.logs.any { it.first == "talk_stop" })
    }

    @Test
    fun `6문항을 마치면 대기로 돌아가고 라운드 간격 뒤에 다시 묻는다`() {
        val h = Harness()
        h.m.demoTimeScale(60f)
        h.vehicle(Gear.P)
        h.m.demoReset()
        h.drain()
        h.vehicle(Gear.D, 90f)
        h.minute = 16 * 60
        h.m.demoDriveMinutes(121)
        h.tick(200)
        repeat(6) { h.answerCorrectly(1_000) }
        h.drain()
        h.tick(3_000)
        assertEquals(S03, h.state)
        h.tick(25_000)
        assertEquals("C1이 계속 참이면 20분(배속 60 → 20초) 뒤 다음 라운드", S04, h.state)
    }

    @Test
    fun `경유지 추가 실패면 음성 길안내 V-29`() {
        val h = Harness()
        h.vehicle(Gear.P)
        h.m.demoReset()
        h.drain()
        h.vehicle(Gear.D, 90f)
        h.m.onRoutePosition(152.0)
        h.m.demoDriveMinutes(121)
        h.tick(200)
        h.answer("졸려", 1_000)
        assertEquals(S08, h.state)
        h.answer("응", 1_000)
        val (token, _) = h.fx.waypoint!!
        h.m.onWaypointResult(token, ok = false)
        assertEquals(S09, h.state)
        assertEquals("경유지를 넣지 못했어요. 12km 앞에서 오른쪽으로 빠지세요.", h.fx.lastLines.single().text)
    }

    @Test
    fun `동의 전에는 D가 되어도 S-00에 머문다`() {
        val h = Harness(consented = false)
        h.vehicle(Gear.P)
        h.vehicle(Gear.D, 30f)
        assertEquals(WakeonState.S00, h.state)
    }
}
