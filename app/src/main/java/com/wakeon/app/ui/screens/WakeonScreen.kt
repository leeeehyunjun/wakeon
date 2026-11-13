package com.wakeon.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.wakeon.app.config.WakeonConfig.Companion.MINUTE_MS
import com.wakeon.app.core.PrecheckSummary
import com.wakeon.app.core.RestOffer
import com.wakeon.app.core.SessionSummary
import com.wakeon.app.core.VoicePhase
import com.wakeon.app.core.WakeonSnapshot
import com.wakeon.app.core.WakeonState
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
import com.wakeon.app.core.TriggerId
import com.wakeon.app.nav.RestArea
import com.wakeon.app.nav.RestAreaType
import com.wakeon.app.ui.theme.DesignFrame
import com.wakeon.app.ui.theme.WakeonColors
import com.wakeon.app.vehicle.Gear
import com.wakeon.app.vehicle.LoadReason
import com.wakeon.app.voice.Line
import com.wakeon.app.voice.VoiceLines

/** 화면 ID (SPEC 8장). 상태 하나에 화면 하나, 같은 상태 안의 갈래는 스냅샷 값으로 고른다. */
enum class ScreenId(val id: String, val driving: Boolean) {
    W01("W-01", false), W02("W-02", false), W03("W-03", false), W03B("W-03b", false),
    W04("W-04", true), W05("W-05", true), W06("W-06", true), W07("W-07", true), W07B("W-07b", true),
    W08("W-08", true), W09("W-09", true), W10("W-10", true), W10B("W-10b", true), W11("W-11", true),
    W12("W-12", false), W13("W-13", false), W13B("W-13b", false), W14("W-14", false), W15("W-15", false),
    W16("W-16", false), W17("W-17", false), W18("W-18", false),
}

fun screenOf(s: WakeonSnapshot): ScreenId = when (s.state) {
    S00 -> ScreenId.W01
    // 출발 전 체크를 마치면 S-01로 돌아오지만, D로 바꿀 때까지 결과(W-03b)를 보여 준다.
    S01 -> if (s.precheck != null) ScreenId.W03B else ScreenId.W02
    S02 -> ScreenId.W03
    S03 -> ScreenId.W04
    S04 -> ScreenId.W05
    S05 -> ScreenId.W06
    S06 -> if (s.caption.any { it.id == VoiceLines.V14.id }) ScreenId.W07B else ScreenId.W07
    S07 -> ScreenId.W08
    S08 -> ScreenId.W09
    S09 -> if (s.caption.any { it.id == "V-25" }) ScreenId.W10B else ScreenId.W10
    S10 -> ScreenId.W11
    S11 -> ScreenId.W12
    S12 -> if (s.alarmLevel > 0) ScreenId.W13B else ScreenId.W13
    S13 -> ScreenId.W14
    S14 -> ScreenId.W15
    S15 -> ScreenId.W17
    S16 -> ScreenId.W16
    S17 -> ScreenId.W18
}

/** 앱 창 전체. 화면은 스냅샷만 보고 그린다. */
@Composable
fun WakeonScreen(s: WakeonSnapshot, actions: WakeonActions) {
    val screen = screenOf(s)
    val bg = if (screen == ScreenId.W13 || screen == ScreenId.W13B) WakeonColors.Primary900 else WakeonColors.Primary950
    DesignFrame(portraitOnly = screen.driving, background = bg) {
        when (screen) {
            ScreenId.W01 -> W01Consent(s, actions)
            ScreenId.W02 -> W02Home(s, actions)
            ScreenId.W03 -> W03Precheck(s, actions)
            ScreenId.W03B -> W03bReady(s)
            ScreenId.W04 -> W04Waiting(s)
            ScreenId.W05 -> W05Talk(s)
            ScreenId.W06 -> W06Paused(s)
            ScreenId.W07 -> W07Confirm(s)
            ScreenId.W07B -> W07bNoAnswer(s)
            ScreenId.W08 -> W08Warning(s)
            ScreenId.W09 -> W09Offer(s)
            ScreenId.W10 -> W10Added(s)
            ScreenId.W10B -> W10bApproach(s)
            ScreenId.W11 -> DriveGeneric(s, "안내중")
            ScreenId.W12 -> W12Arrived(s, actions)
            ScreenId.W13 -> W13Rest(s, actions)
            ScreenId.W13B -> W13bAlarm(s, actions)
            ScreenId.W15 -> W15Recheck(s)
            ScreenId.W16 -> W16Depart(s)
            ScreenId.W14, ScreenId.W17, ScreenId.W18 -> ParkGeneric(s)
        }
    }
}

// ════════════════════════════════════════ 미리보기 (시안 크기). 값은 시연 장면(DEMO_SCENARIO 2장)과 같게.

private val shelter = RestArea("선기졸음쉼터", RestAreaType.DROWSY_SHELTER, 238.0, "오른쪽")
private val serviceArea = RestArea("칠곡휴게소", RestAreaType.SERVICE_AREA, 258.0, "오른쪽")
private val offer = RestOffer(shelter, 12.0, 8, serviceArea, 32.0)

internal fun sample(state: WakeonState, gear: Gear = Gear.D, vararg lines: Line, phase: VoicePhase = VoicePhase.SPEAKING) =
    WakeonSnapshot(state = state, gear = gear, phase = phase, caption = lines.toList(), minuteOfDay = 23 * 60 + 12)

@Composable
private fun P(s: WakeonSnapshot) = WakeonScreen(s, WakeonActions.None)

@Preview(name = "W-01 동의", widthDp = 1700, heightDp = 1184) @Composable
private fun PW01() = P(sample(S00, Gear.P, VoiceLines.V01))

@Preview(name = "W-02 홈", widthDp = 1700, heightDp = 1184) @Composable
private fun PW02() = P(sample(S01, Gear.P, VoiceLines.V02).copy(triggersEnabled = setOf(TriggerId.C1, TriggerId.C2)))

@Preview(name = "W-02 홈 (세로 창)", widthDp = 840, heightDp = 1184) @Composable
private fun PW02V() = PW02()

@Preview(name = "W-03 체크 1/4", widthDp = 1700, heightDp = 1184) @Composable
private fun PW03() = P(sample(S02, Gear.P, VoiceLines.V03, phase = VoicePhase.RESULT).copy(questionNo = 1, questionTotal = 4, heard = "칠."))

@Preview(name = "W-03 체크 2/4 (정답형)", widthDp = 1700, heightDp = 1184) @Composable
private fun PW03Q() = P(sample(S02, Gear.P, Line("Q-P1", "7 더하기 5는요?")).copy(questionNo = 2, questionTotal = 4))

@Preview(name = "W-03b 준비 완료", widthDp = 1700, heightDp = 1184) @Composable
private fun PW03b() = P(sample(S01, Gear.P, VoiceLines.V06).copy(precheck = PrecheckSummary(3, 3, 3, 1000)))

@Preview(name = "W-04 주행 대기", widthDp = 840, heightDp = 1184) @Composable
private fun PW04() = P(sample(S03).copy(driveMs = 102 * MINUTE_MS, nextTalkInMs = 18 * MINUTE_MS))

@Preview(name = "W-05 대화 · 말하는 중", widthDp = 840, heightDp = 1184) @Composable
private fun PW05() = P(sample(S04, Gear.D, VoiceLines.V10))

@Preview(name = "W-05 대화 · 긴 문장", widthDp = 840, heightDp = 1184) @Composable
private fun PW05L() = P(sample(S04, Gear.D, VoiceLines.V11_OK, Line("V-12", "그럼 보리 얘기 하나만 더요. 보리는 산책 좋아해요?"), phase = VoicePhase.LISTENING))

@Preview(name = "W-05b 대답 인식", widthDp = 840, heightDp = 1184) @Composable
private fun PW05b() = P(sample(S04, Gear.D, VoiceLines.V10, phase = VoicePhase.RESULT).copy(heard = "네, 한 마리요."))

@Preview(name = "W-06 대화 멈춤", widthDp = 840, heightDp = 1184) @Composable
private fun PW06() = P(sample(S05).copy(loadReasons = setOf(LoadReason.BRAKE, LoadReason.TURN_SIGNAL)))

@Preview(name = "W-07 의심 확인", widthDp = 840, heightDp = 1184) @Composable
private fun PW07() = P(sample(S06, Gear.D, VoiceLines.V15))

@Preview(name = "W-07b 무응답", widthDp = 840, heightDp = 1184) @Composable
private fun PW07b() = P(sample(S06, Gear.D, VoiceLines.V14).copy(noAnswerCount = 1))

@Preview(name = "W-08 단계적 경고", widthDp = 840, heightDp = 1184) @Composable
private fun PW08() = P(sample(S07, Gear.D, VoiceLines.V20).copy(warnLevel = 2))

@Preview(name = "W-09 쉼터 제안", widthDp = 840, heightDp = 1184) @Composable
private fun PW09() = P(sample(S08, Gear.D, VoiceLines.v21("12", "선기졸음쉼터")).copy(offer = offer))

@Preview(name = "W-10 경유지 추가", widthDp = 840, heightDp = 1184) @Composable
private fun PW10() = P(sample(S09, Gear.D, VoiceLines.v24("선기졸음쉼터", 8)).copy(offer = offer.copy(distanceKm = 11.6), waypointAdded = true))

@Preview(name = "W-10b 1km 전", widthDp = 840, heightDp = 1184) @Composable
private fun PW10b() = P(sample(S09, Gear.D, VoiceLines.v25("1", "선기졸음쉼터", "오른쪽")).copy(offer = offer.copy(distanceKm = 1.0)))

@Preview(name = "W-12 도착", widthDp = 1700, heightDp = 1184) @Composable
private fun PW12() = P(sample(S11, Gear.P, VoiceLines.V30_A, phase = VoicePhase.LISTENING).copy(offer = offer, arrivedMinuteOfDay = 23 * 60 + 21))

@Preview(name = "W-13 휴식", widthDp = 1700, heightDp = 1184) @Composable
private fun PW13() = P(sample(S12, Gear.P, VoiceLines.v33(15)).copy(restEndsAtMs = 872_000, nowMs = 0))

@Preview(name = "W-13b 기상 알람", widthDp = 1700, heightDp = 1184) @Composable
private fun PW13b() = P(sample(S12, Gear.P, VoiceLines.V35).copy(alarmLevel = 2))

@Preview(name = "W-15 재출발 체크", widthDp = 1700, heightDp = 1184) @Composable
private fun PW15() = P(sample(S14, Gear.P, VoiceLines.V50, Line("Q-R1", "12 빼기 4는요?"), phase = VoicePhase.LISTENING))

@Preview(name = "W-16 출발", widthDp = 1700, heightDp = 1184) @Composable
private fun PW16() = P(sample(S16, Gear.P, VoiceLines.v52("32")).copy(summary = SessionSummary(1, 18, 1)))
