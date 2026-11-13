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
import com.wakeon.app.voice.UserIntent
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
            RestArea("선기졸음쉼터", RestAreaType.DROWSY_SHELTER, 238.0, "오른쪽"),
            RestArea("칠곡휴게소", RestAreaType.SERVICE_AREA, 258.0, "오른쪽"),
            RestArea("하빈졸음쉼터", RestAreaType.DROWSY_SHELTER, 270.0, "오른쪽"),
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

    /** 시연 장면 1~17을 지나 휴식(S-12)이 시작된 상태까지 간다(장면마다 확인 포함). */
    private fun toRest(): Harness {
        val h = Harness()
        h.m.demoTimeScale(60f)
        h.m.demoRestSeconds(30)
        h.vehicle(Gear.P)
        // 시안 W-02처럼 C3(70km/h 10분)는 끈 상태로 시연한다
        h.m.setTriggerEnabled(TriggerId.C3, false)

        // 장면 1~4: S-01 → S-02 → S-01, 기준선 1.0초 확정. 강아지 대답에는 짧은 호응만 하고 다음 문항
        h.m.demoReset()
        assertEquals(S01, h.state)
        assertEquals("V-02", h.untilListening()?.id)
        h.answer("응, 부산까지.", 1_000)
        assertEquals(S02, h.state)
        assertEquals("V-03", h.untilListening()?.id)
        h.answer("삼.", 1_000)
        assertEquals("7 더하기 5는요?", h.untilListening()?.text)
        h.answer("십이.", 1_000)
        assertEquals("강아지와 고양이 중에 뭐가 좋아요?", h.untilListening()?.text)
        h.answer("강아지.", 1_000)
        assertEquals("강아지 좋죠. 점심은 뭐 드셨어요?", h.untilListening()?.text)
        h.answer("김치찌개요", 1_000)
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

        // 연속 2시간 4분(C1) → S-04. 1·2번은 점심 줄기(앱이 묻고 편집에서 자른다)
        h.minute = 23 * 60 + 8
        h.m.demoDriveMinutes(124)
        h.tick(200)
        assertEquals(S04, h.state)
        assertEquals("점심에 김치찌개 드셨다고 하셨죠. 맛있었어요?", h.untilListening()?.text)
        h.answer("네, 맛있었어요.", 1_000)
        assertEquals("다행이에요. 부산 가면 제일 먼저 뭐 드실 거예요?", h.untilListening()?.text)
        h.answer("돼지국밥이요.", 1_000)
        assertEquals("돼지국밥 좋죠.", h.fx.lastLines.single().text)

        // 장면 6: 체크에서 들은 "강아지"로 시작하는 줄기 (3/6)
        assertEquals("아까 강아지가 더 좋다고 하셨죠. 키우세요?", h.untilListening()?.text)
        assertEquals(3, h.snap.questionNo)
        h.answer("네, 한 마리요.", 1_200)
        // 장면 7: 인식 결과 2초 표시 → 호응 + 이어지는 질문 (4/6)
        assertEquals("네, 한 마리요.", h.snap.heard)
        assertEquals("한 마리 키우시는구나. 이름이 뭐예요?", h.untilListening()?.text)
        assertEquals(4, h.snap.questionNo)
        h.answer("보리요.", 1_700)

        // 장면 8: 결과 표시 중 브레이크 + 방향지시등 → S-05, 꺼지고 10초 뒤 이어서
        h.vehicle(Gear.D, 84f, brake = true, turn = TurnSignal.RIGHT)
        assertEquals(S05, h.state)
        h.vehicle(Gear.D, 99f)
        assertEquals(S05, h.state)
        h.tick(10_000)
        assertEquals(S04, h.state)

        // 장면 9: "보리, 이름 예쁘네요. 보리는 몇 살이에요?" (5/6) → 2.8초 → 최근 3개 중 2개 > 1.5초 → S1 → S-06
        assertEquals("보리, 이름 예쁘네요. 보리는 몇 살이에요?", h.untilListening()?.text)
        assertEquals(5, h.snap.questionNo)
        h.answer("…세 살… 인가?", 2_800)
        assertEquals(S06, h.state)
        assertEquals("V-15", h.untilListening()?.id)

        // 장면 10~11: "아니, 괜찮아." → "알겠어요."로 받고 바로 같은 줄기의 다음 질문 (6/6)
        h.answer("아니, 괜찮아.", 1_500)
        assertEquals(S04, h.state)
        assertEquals("알겠어요. 그럼 보리 얘기 하나만 더요. 보리는 산책 좋아해요?", h.untilListening()?.text)
        assertEquals(6, h.snap.questionNo)

        // 장면 11~12: 무응답 → S-06 "제 말 들리세요?" → 다시 무응답
        h.noAnswer()
        assertEquals(S06, h.state)
        assertEquals("V-14", h.untilListening()?.id)
        assertEquals(1, h.snap.noAnswerCount)
        h.m.onRoutePosition(226.0)
        h.noAnswer()

        // 장면 13: 알림음 1→3단계(5초 간격) 뒤 V-20 → S-08
        assertEquals(S07, h.state)
        h.untilListening()
        assertEquals(listOf(1, 2, 3), h.fx.sounds.filterIsInstance<Sound.Warn>().map { it.level })
        assertTrue("V-20" in h.spokenIds())

        // 장면 14: "12km 앞 선기졸음쉼터를 경유지로 넣을까요?" → "어… 응." → S-09
        assertEquals(S08, h.state)
        assertEquals("12km 앞 선기졸음쉼터를 경유지로 넣을까요?", h.fx.lastLines.single().text)
        assertEquals("칠곡휴게소", h.snap.offer?.next?.name)
        h.answer("어… 응.", 1_300)
        assertEquals(S09, h.state)

        // 장면 15: 경유지 추가 성공 → V-24
        val (token, rest) = h.fx.waypoint!!
        assertEquals("선기졸음쉼터", rest.name)
        h.m.onWaypointResult(token, ok = true)
        assertEquals("선기졸음쉼터를 경유지로 넣었어요. 8분 뒤 도착해요.", h.fx.lastLines.single().text)
        h.drain()

        // 장면 16: 1km 전 안내
        h.m.onRoutePosition(237.0)
        assertEquals("1km 앞 선기졸음쉼터예요. 오른쪽으로 빠지세요.", h.fx.lastLines.single().text)
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
        return h
    }

    @Test
    fun `시연 23장면 경로 - 출발 전 체크부터 재출발까지 한 번에 돈다`() {
        val h = toRest()

        // 장면 18~19: 30초 휴식 → 5초 전 "등받이를 세워 주세요" → 알람 + V-35 → "일어났어" → S-14
        // 시연 배속(60배)에서는 알람 간격 30초가 0.5초가 된다. 그래도 1단계 소리와 V-35가 끊기지 않아야 한다(2026-11-13).
        while (!h.fx.sounds.contains(Sound.Alarm(1))) {
            h.finishSpeech()
            h.tick(200)
        }
        h.tick(2_000) // 알람 1단계 소리가 나는 중
        assertTrue(Sound.Alarm(2) !in h.fx.sounds)
        h.finishSound()
        assertEquals("V-35", h.fx.lastLines.single().id)
        h.tick(2_000) // V-35를 말하는 중
        assertTrue(Sound.Alarm(2) !in h.fx.sounds)
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
        assertEquals("다음 쉼터는 32km 앞이에요. 졸리면 바로 쉬세요.", h.fx.lastLines.single().text)
        assertEquals(SessionSummary(rests = 1, restMinutes = h.snap.summary.restMinutes, warns = 1), h.snap.summary)
        h.drain()

        // 장면 22: D → S-03, 연속 주행 0분부터. 23:43 야간이라 다음 말 걸기는 30분 뒤(C2)
        h.minute = 23 * 60 + 43
        h.vehicle(Gear.D, 92f)
        assertEquals(S03, h.state)
        assertTrue(h.snap.driveMs < MINUTE_MS)
        assertEquals(30L, (h.snap.nextTalkInMs!! + MINUTE_MS - 1) / MINUTE_MS)

        assertEquals(
            listOf("S-01", "S-02", "S-01", "S-03", "S-04", "S-05", "S-04", "S-06", "S-04", "S-06", "S-07", "S-08", "S-09", "S-11", "S-12", "S-14", "S-16", "S-03"),
            h.states(),
        )
    }

    @Test
    fun `휴식 연장은 5분씩 최대 3회, 4회째는 V-36 한 번`() {
        val h = toRest()
        h.drain()
        assertEquals(15 * MINUTE_MS, h.snap.restNominalMs)
        // 시연 휴식 30초 = 화면 15분. 5분 연장 = 실제 10초
        h.m.onTap(UserIntent.SNOOZE_5)
        assertEquals(20 * MINUTE_MS, h.snap.restNominalMs)
        assertEquals(40_000L, h.snap.restTotalMs)
        assertEquals("V-33", h.fx.lastLines.single().id)
        h.drain()
        // 알람이 울리는 중에 "5분만" → 알람을 멈추고 지금부터 5분
        while (h.snap.alarmLevel == 0) {
            h.finishSpeech()
            h.tick(200)
        }
        h.untilListening()
        h.answer("5분만", 1_000)
        assertEquals(S12, h.state)
        assertEquals(0, h.snap.alarmLevel)
        assertEquals("5분 뒤에 깨워 드릴게요. 편히 쉬세요.", h.fx.lastLines.single().text)
        h.drain()
        h.m.onTap(UserIntent.SNOOZE_5)
        h.drain()
        assertEquals(3, h.logs.count { it.first == "rest_extended" })
        // 4회째: 연장하지 않고 V-36 한 번
        val total = h.snap.restTotalMs
        h.m.onTap(UserIntent.SNOOZE_5)
        assertEquals("V-36", h.fx.lastLines.single().id)
        h.drain()
        h.m.onTap(UserIntent.SNOOZE_5)
        assertEquals(total, h.snap.restTotalMs)
        assertEquals(1, h.spokenIds().count { it == "V-36" })
        assertEquals(1, h.logs.count { it.first == "rest_completed" })
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
        h.answer("김치찌개", 1_000)
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
    fun `대화 중 그만은 이번 라운드만 멈춘다`() {
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
        repeat(6) { h.answer("네", 1_000) }
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
        h.m.onRoutePosition(226.0)
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
