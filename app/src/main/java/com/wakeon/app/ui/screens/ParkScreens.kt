package com.wakeon.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wakeon.app.R
import com.wakeon.app.config.WakeonConfig
import com.wakeon.app.core.TriggerId
import com.wakeon.app.core.VoicePhase
import com.wakeon.app.core.WakeonSnapshot
import com.wakeon.app.ui.components.CardGlow
import com.wakeon.app.ui.components.HintCard
import com.wakeon.app.ui.components.PillBox
import com.wakeon.app.ui.components.RestChip
import com.wakeon.app.ui.components.StatPill
import com.wakeon.app.ui.components.StatusGlyph
import com.wakeon.app.ui.components.StatusLabel
import com.wakeon.app.ui.components.VoiceWave
import com.wakeon.app.ui.components.WakeonHeader
import com.wakeon.app.ui.components.WkButton
import com.wakeon.app.ui.components.WkToggle
import com.wakeon.app.ui.components.rich
import com.wakeon.app.ui.theme.FrameKind
import com.wakeon.app.ui.theme.LocalFrameKind
import com.wakeon.app.ui.theme.WakeonColors
import com.wakeon.app.ui.theme.WakeonType
import com.wakeon.app.voice.KoreanText
import com.wakeon.app.voice.QuestionBank
import com.wakeon.app.voice.UserIntent
import com.wakeon.app.voice.VoiceLines

// 정차 화면. 가로 창(1700 × 1184)은 시안 좌표 그대로 놓고, 세로 창(840)은 같은 요소를 세로로 쌓는다(DESIGN.md 3장).
// 세로 배치는 1차 범위 밖(CLAUDE.md 3장)이라 요소만 빠짐없이 보이게 한다.

/** 화면 버튼이 부르는 일. 같은 일을 음성으로도 할 수 있다(F1 AC-1.2). */
interface WakeonActions {
    fun consent()
    fun startPrecheck()
    fun skipPrecheck()
    fun tap(intent: UserIntent)
    fun setTrigger(id: TriggerId, on: Boolean)

    object None : WakeonActions {
        override fun consent() = Unit
        override fun startPrecheck() = Unit
        override fun skipPrecheck() = Unit
        override fun tap(intent: UserIntent) = Unit
        override fun setTrigger(id: TriggerId, on: Boolean) = Unit
    }
}

@Composable
private fun landscape() = LocalFrameKind.current == FrameKind.Landscape

private fun Modifier.at(x: Float, y: Float) = padding(start = x.dp, top = y.dp)

private val semiBold29 = WakeonType.body2.copy(fontSize = 29.sp)

/** 정차 화면 바탕. [panelWidth]가 있으면 왼쪽에 #161616 판을 깐다(W-01·W-02·W-12). */
@Composable
private fun ParkFrame(s: WakeonSnapshot, panelWidth: Dp? = null, background: Color = WakeonColors.Primary950, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(background)) {
        if (panelWidth != null && landscape()) Box(Modifier.width(panelWidth).fillMaxHeight().background(WakeonColors.Primary900))
        content()
        WakeonHeader(s.gear)
    }
}

/** 세로 창: 헤더 아래에 요소를 위에서부터 쌓는다. */
@Composable
private fun BoxScope.PortraitStack(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(start = 56.dp, end = 58.dp, top = 180.dp, bottom = 62.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) { content() }
}

// ════════════════════════════════════════ W-01 동의와 고지 (113:2251)

@Composable
fun W01Consent(s: WakeonSnapshot, actions: WakeonActions) {
    ParkFrame(s, panelWidth = 813.dp) {
        val title = @Composable { Text(ScreenStrings.CONSENT_TITLE, style = WakeonType.headline2, color = WakeonColors.White) }
        val notice = @Composable {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(ScreenStrings.CONSENT_MUST_KNOW, style = WakeonType.body2, color = WakeonColors.White)
                Text(ScreenStrings.CONSENT_NOTICE, style = WakeonType.caption1, color = WakeonColors.White, modifier = Modifier.width(755.dp))
            }
        }
        val button = @Composable { m: Modifier -> WkButton(ScreenStrings.CONSENT_START, actions::consent, m) }
        if (landscape()) {
            Box(Modifier.at(80f, 267f)) { title() }
            Box(Modifier.at(80f, 427f)) { notice() }
            PermissionCard(Modifier.at(878f, 236f).width(773.dp))
            button(Modifier.at(878f, 960f).width(773.dp))
        } else {
            PortraitStack {
                title()
                notice()
                PermissionCard(Modifier.fillMaxWidth())
                Spacer(Modifier.weight(1f))
                button(Modifier.fillMaxWidth())
            }
        }
    }
}

/** 권한 카드 (113:2264). 필수 3종은 켜짐 고정, 시트·조명·공조는 차량 제어가 열리기 전이라 꺼짐 고정. */
@Composable
private fun PermissionCard(modifier: Modifier = Modifier) {
    // 필수 3종(마이크·위치·기어/속도)은 켜짐, 시트·조명·공조는 꺼짐
    val rows = ScreenStrings.PERMISSIONS.mapIndexed { i, (name, desc) -> Triple(name, desc, i < 3) }
    Column(
        modifier.clip(RoundedCornerShape(30.dp)).background(WakeonColors.Panel).padding(start = 36.dp, end = 35.dp, top = 36.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp),
    ) {
        for ((name, desc, on) in rows) {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(Modifier.fillMaxWidth().height(73.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = WakeonType.body2, color = WakeonColors.White, modifier = Modifier.weight(1f))
                    WkToggle(on, null)
                }
                Text(desc, style = WakeonType.body4, color = WakeonColors.PermissionText)
            }
        }
    }
}

// ════════════════════════════════════════ W-02 홈 · 출발 전 (101:1572)

@Composable
fun W02Home(s: WakeonSnapshot, actions: WakeonActions) {
    val parts = sentences(VoiceLines.V02.text)
    ParkFrame(s, panelWidth = 914.dp) {
        val intro = @Composable {
            Column(verticalArrangement = Arrangement.spacedBy(25.dp)) {
                Text(parts.first(), style = WakeonType.headline2, color = WakeonColors.White)
                Text(
                    "${parts.drop(1).joinToString(" ")}. ${ScreenStrings.HOME_DETAIL}",
                    style = WakeonType.body4, color = WakeonColors.White, modifier = Modifier.width(755.dp),
                )
            }
        }
        val buttons = @Composable { m: Modifier ->
            Row(m, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                WkButton(ScreenStrings.START_CHECK, actions::startPrecheck, Modifier.width(475.dp))
                WkButton(ScreenStrings.SKIP, actions::skipPrecheck, Modifier.width(265.dp), color = WakeonColors.ButtonDark, style = WakeonType.caption3)
            }
        }
        val hint = rich(ScreenStrings.HINT_WAKE)
        if (landscape()) {
            Box(Modifier.at(80f, 267f)) { intro() }
            buttons(Modifier.at(80f, 845f))
            HintCard(hint, Modifier.at(976f, 236f).size(660.dp, 326.dp), textWidth = 660.dp)
            TriggerCard(s, actions, Modifier.at(976f, 590f).width(660.dp))
        } else {
            PortraitStack {
                intro()
                HintCard(hint, Modifier.fillMaxWidth().height(160.dp))
                TriggerCard(s, actions, Modifier.fillMaxWidth())
                Spacer(Modifier.weight(1f))
                buttons(Modifier)
            }
        }
    }
}

/** 먼저 말을 거는 때 (101:1651). 연속 2시간(C1)은 끌 수 없다(잠금, AC-3.3). */
@Composable
private fun TriggerCard(s: WakeonSnapshot, actions: WakeonActions, modifier: Modifier = Modifier) {
    val rows = listOf(
        Triple(TriggerId.C1, ScreenStrings.TRIGGER_C1, false),
        Triple(TriggerId.C2, ScreenStrings.TRIGGER_C2, true),
        Triple(TriggerId.C3, ScreenStrings.TRIGGER_C3, true),
    )
    Column(
        modifier.clip(RoundedCornerShape(30.dp)).background(WakeonColors.Panel).padding(start = 36.dp, end = 35.dp, top = 36.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        for ((id, name, editable) in rows) {
            Row(Modifier.fillMaxWidth().height(104.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(name, style = semiBold29, color = WakeonColors.White, modifier = Modifier.weight(1f))
                val on = id in s.triggersEnabled
                WkToggle(on, if (editable) { v -> actions.setTrigger(id, v) } else null)
            }
        }
    }
}

// ════════════════════════════════════════ W-03 출발 전 체크 (103:1682) · 정답형 문항은 W-15 질문 배치(132:8076)

/** 진행 막대 4칸 (103:1788): 89 × 6, 간격 14. 지금 문항까지 흰색. */
@Composable
private fun Progress(done: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(total) { i ->
            Box(Modifier.size(89.dp, 6.dp).background(if (i < done) WakeonColors.White else WakeonColors.Neutral700))
        }
    }
}

@Composable
fun W03Precheck(s: WakeonSnapshot, actions: WakeonActions) {
    val total = QuestionBank.precheck.size
    val kss = s.questionNo <= 1
    val question = sentences(captionText(s)).joinToString(" ").ifEmpty { sentences(VoiceLines.V03.text).first() }
    val heardNumber = s.heard?.let { KoreanText.firstNumber(it) }?.takeIf { it in 1..9 }
    ParkFrame(s) {
        val skip = @Composable { m: Modifier ->
            WkButton(ScreenStrings.SKIP, actions::skipPrecheck, m.width(183.dp), height = 100.dp, style = WakeonType.caption3)
        }
        Progress(s.questionNo.coerceAtLeast(1), total, Modifier.at(if (landscape()) 80f else 56f, 159f))
        if (kss) {
            // 졸린 정도 1~9 (자가 보고)
            StatusLabel(
                ScreenStrings.MEASURING, StatusGlyph.Wave, WakeonColors.Secondary300,
                Modifier.align(Alignment.TopCenter).padding(top = 282.dp),
            )
            Text(
                question, style = WakeonType.question62, color = WakeonColors.White, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 373.dp, start = 56.dp, end = 56.dp),
            )
            VoiceWave(modifier = Modifier.padding(top = 537.5.dp))
            if (heardNumber != null) {
                Text("“ $heardNumber ”", style = WakeonType.heardThin, color = WakeonColors.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 816.dp))
                PillBox(Modifier.align(Alignment.TopCenter).padding(top = 938.dp).width(468.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        Text(kssText(heardNumber), style = WakeonType.body3, color = WakeonColors.White)
                        Text(ScreenStrings.SELECT, style = WakeonType.body3, color = WakeonColors.StatusText)
                    }
                }
            }
        } else {
            // 정답형 문항: W-15와 같은 질문 배치
            PhaseStatus(s, Modifier.align(Alignment.TopCenter).padding(top = 248.dp))
            VoiceWave(modifier = Modifier.padding(top = 416.5.dp))
            BigQuestion(question, Modifier.align(Alignment.TopCenter).padding(top = 714.dp))
        }
        if (landscape()) skip(Modifier.at(1453f, 961f)) else skip(Modifier.align(Alignment.BottomEnd).padding(end = 58.dp, bottom = 62.dp))
    }
}

/** 질문중 · 듣고 있어요 · 들은 말 */
@Composable
private fun PhaseStatus(s: WakeonSnapshot, modifier: Modifier) {
    val heard = s.heard
    when {
        s.phase == VoicePhase.RESULT && heard != null -> StatusLabel("“$heard”", StatusGlyph.Wave, WakeonColors.Point400, modifier)
        s.phase == VoicePhase.LISTENING -> StatusLabel(ScreenStrings.LISTENING, StatusGlyph.Wave, modifier = modifier)
        else -> StatusLabel(ScreenStrings.ASKING, StatusGlyph.Wave, modifier = modifier)
    }
}

/** 큰 질문 (132:8090): SemiBold 100, 폭 906, 가운데. */
@Composable
private fun BigQuestion(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = WakeonType.display3, color = WakeonColors.White, textAlign = TextAlign.Center,
        modifier = modifier.width(if (landscape()) 906.dp else 726.dp),
    )
}

// ════════════════════════════════════════ W-03b 준비 완료 (132:7854)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun W03bReady(s: WakeonSnapshot) {
    val p = s.precheck
    val title = sentences(VoiceLines.V06.text).first()
    ParkFrame(s) {
        Progress(4, 4, Modifier.at(if (landscape()) 80f else 56f, 159f))
        StatusLabel(ScreenStrings.MEASURED, StatusGlyph.Dot, WakeonColors.Secondary300, Modifier.align(Alignment.TopCenter).padding(top = 282.dp))
        Text(title, style = WakeonType.question62, color = WakeonColors.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 373.dp))
        FlowRow(
            Modifier.align(Alignment.TopCenter).padding(top = 531.dp, start = 40.dp, end = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            StatPill(ScreenStrings.STAT_KSS, p?.kss?.let { kssText(it) } ?: "—", Modifier.width(468.dp))
            StatPill(ScreenStrings.STAT_CORRECT, if (p != null) "${p.correct}/${p.total}" else "—", Modifier.width(349.dp))
            StatPill(ScreenStrings.STAT_SPEED, p?.baselineMs?.let { "%.1f초".format(it / 1000.0) } ?: "—", Modifier.width(349.dp))
        }
        // 안내 한 줄과 뒤의 푸른 빛 (132:7888, 132:7889): 카드 없이 빛만
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = if (landscape()) 836.dp else 1000.dp).size(986.dp, 141.dp),
            contentAlignment = Alignment.Center,
        ) {
            CardGlow(Modifier.fillMaxSize())
            Text(rich(ScreenStrings.HINT_GEAR_D), style = WakeonType.body1, color = WakeonColors.White, textAlign = TextAlign.Center)
        }
    }
}

// ════════════════════════════════════════ W-12 도착 · 휴식 제안 (132:1485)

@Composable
fun W12Arrived(s: WakeonSnapshot, actions: WakeonActions) {
    val count = s.listenEndsAtMs?.let { ((it - s.nowMs + 999) / 1000).toInt().coerceIn(0, 10) } ?: 10
    val title = VoiceLines.V30_A.text.replace(". ", ".\n")
    ParkFrame(s, panelWidth = 813.dp) {
        val arrived = @Composable { m: Modifier -> ArrivalCard(s, m) }
        val head = @Composable { Text(title, style = WakeonType.headline1, color = WakeonColors.White) }
        val start = @Composable { m: Modifier -> RestStartButton(count, { actions.tap(UserIntent.YES) }, m) }
        val routine = @Composable { m: Modifier -> WkButton(ScreenStrings.ROUTINE, {}, m, style = WakeonType.title2) }
        if (landscape()) {
            arrived(Modifier.at(57f, 219f).width(726.dp))
            Box(Modifier.at(80f, 412f)) { head() }
            start(Modifier.at(57f, 712f).width(726.dp))
            routine(Modifier.at(57f, 960f).width(726.dp))
            InfoBlock(R.drawable.wk_ic_coffee, ScreenStrings.COFFEE_TITLE, ScreenStrings.COFFEE_BODY, Modifier.at(925f, 299f).width(665.dp))
            InfoBlock(null, ScreenStrings.POINT_TITLE, ScreenStrings.POINT_BODY, Modifier.at(925f, 712f).width(665.dp))
        } else {
            PortraitStack {
                arrived(Modifier.fillMaxWidth())
                head()
                start(Modifier.fillMaxWidth())
                routine(Modifier.fillMaxWidth())
                InfoBlock(R.drawable.wk_ic_coffee, ScreenStrings.COFFEE_TITLE, ScreenStrings.COFFEE_BODY, Modifier.fillMaxWidth())
            }
        }
    }
}

/** 도착 카드 (132:1646): 쉼터 이름 + 도착 시각. */
@Composable
private fun ArrivalCard(s: WakeonSnapshot, modifier: Modifier) {
    Row(
        modifier.height(119.dp).clip(RoundedCornerShape(23.836.dp)).background(WakeonColors.Primary800).padding(start = 27.dp, end = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(s.offer?.rest?.name ?: "", style = WakeonType.body2, color = WakeonColors.White, modifier = Modifier.weight(1f))
        Text(formatClock(s.arrivedMinuteOfDay ?: s.minuteOfDay), style = WakeonType.body2, color = WakeonColors.White)
    }
}

/** "휴식 15분 시작" 큰 버튼 (132:1672): 시계 기호 + 두 줄 + 10초 카운트다운. */
@Composable
private fun RestStartButton(count: Int, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.height(214.dp).clip(RoundedCornerShape(30.dp)).background(WakeonColors.Primary800).clickable(onClick = onClick).padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.771.dp),
    ) {
        PieGlyph(Modifier.size(64.734.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.789.dp)) {
            Text(ScreenStrings.START_REST, style = WakeonType.title2.copy(lineHeight = 51.75.sp), color = WakeonColors.White)
            Text(ScreenStrings.AUTO_START, style = WakeonType.caption1.copy(lineHeight = 34.5.sp), color = WakeonColors.White)
        }
        Box(
            Modifier.size(103.394.dp, 98.dp).border(4.495.dp, WakeonColors.CountBorder, RoundedCornerShape(31.468.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$count", style = WakeonType.title2.copy(fontWeight = FontWeight.SemiBold, fontSize = 44.954.sp), color = WakeonColors.White)
        }
    }
}

/** 오른쪽 안내 (132:1679): 아이콘 64 + 제목 Bold 50 + 설명 Regular 40. 아이콘이 없으면 시안처럼 빈 칸. */
@Composable
private fun InfoBlock(icon: Int?, title: String, body: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (icon != null) Image(painterResource(icon), null, Modifier.size(64.dp)) else Box(Modifier.size(64.dp).background(WakeonColors.IconSlot))
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(title, style = WakeonType.title1, color = WakeonColors.White)
            Text(body, style = WakeonType.body1.copy(lineHeight = 46.sp), color = WakeonColors.InfoText)
        }
    }
}

// ════════════════════════════════════════ W-13 휴식 모드 (132:1759) · W-13b 기상 알람 (132:1902)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestLayout(
    s: WakeonSnapshot,
    actions: WakeonActions,
    top: @Composable () -> Unit,
    chips: @Composable () -> Unit,
) {
    ParkFrame(s, background = WakeonColors.Primary900) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = if (landscape()) 262.dp else 220.dp)) { top() }
        FlowRow(
            Modifier.align(Alignment.TopCenter).padding(top = if (landscape()) 644.dp else 700.dp, start = 40.dp, end = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(38.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) { chips() }
        Row(
            Modifier.align(if (landscape()) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(top = if (landscape()) 826.dp else 0.dp, bottom = if (landscape()) 0.dp else 62.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            WkButton(ScreenStrings.PLUS_5, { actions.tap(UserIntent.SNOOZE_5) }, Modifier.width(if (landscape()) 346.dp else 240.dp), color = WakeonColors.ButtonDark)
            WkButton(ScreenStrings.WAKE_NOW, { actions.tap(UserIntent.AWAKE) }, Modifier.width(if (landscape()) 773.dp else 446.dp))
        }
    }
}

@Composable
fun W13Rest(s: WakeonSnapshot, actions: WakeonActions) {
    val left = s.restEndsAtMs?.let { it - s.nowMs } ?: 0
    RestLayout(
        s, actions,
        top = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(40.dp)) {
                // 시안 132:1805: 높이 253 칸 가운데에 Medium 280, 줄 높이 100%
                Box(Modifier.height(if (landscape()) 253.dp else 190.dp), contentAlignment = Alignment.Center) {
                    Text(
                        formatTimer(left), color = WakeonColors.Timer, textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.wrapContentHeight(unbounded = true),
                        // 줄 높이를 글자 높이보다 넉넉히(1.2em) 두어 숫자 아래가 잘리지 않게 한다. 칸 높이는 시안대로 253.
                        style = (if (landscape()) WakeonType.display1 else WakeonType.display1.copy(fontSize = 200.sp)).copy(lineHeight = 1.2.em),
                    )
                }
                Text(
                    ScreenStrings.restOf(WakeonConfig().restDefaultMin), style = WakeonType.body1, color = WakeonColors.SubText,
                    textAlign = TextAlign.Center, modifier = Modifier.width(if (landscape()) 906.dp else 726.dp),
                )
            }
        },
        chips = {
            RestChip(R.drawable.wk_ic_light, true, 37f, 49f, ScreenStrings.CHIP_ALARM_SLOW, active = true)
            RestChip(R.drawable.wk_ic_timer, false, 81.677f, 81.677f, ScreenStrings.CHIP_PREWARN, active = false)
            RestChip(R.drawable.wk_ic_air, false, 81.677f, 81.677f, ScreenStrings.CHIP_WINDOW, active = false)
        },
    )
}

@Composable
fun W13bAlarm(s: WakeonSnapshot, actions: WakeonActions) {
    val parts = sentences(VoiceLines.V35.text)
    RestLayout(
        s, actions,
        top = {
            Column(Modifier.padding(top = if (landscape()) 72.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(parts.first(), style = WakeonType.display3, color = WakeonColors.White, textAlign = TextAlign.Center, modifier = Modifier.width(if (landscape()) 906.dp else 726.dp))
                if (parts.size > 1) Text(parts[1], style = WakeonType.body1, color = WakeonColors.SubText, textAlign = TextAlign.Center)
            }
        },
        chips = {
            RestChip(R.drawable.wk_ic_bell, true, 44f, 44f, ScreenStrings.CHIP_ALARM_MAX, active = true)
            RestChip(R.drawable.wk_ic_timer, false, 81.677f, 81.677f, ScreenStrings.restDone(WakeonConfig().restDefaultMin), active = false)
        },
    )
}

// ════════════════════════════════════════ W-15 재출발 체크 (132:8076)

@Composable
fun W15Recheck(s: WakeonSnapshot) {
    val q = s.caption.lastOrNull()?.text?.let { sentences(it).joinToString(" ") }.orEmpty()
    ParkFrame(s) {
        PhaseStatus(s, Modifier.align(Alignment.TopCenter).padding(top = 248.dp))
        VoiceWave(modifier = Modifier.padding(top = 416.5.dp))
        BigQuestion(q, Modifier.align(Alignment.TopCenter).padding(top = 714.dp))
        BottomHint(rich(ScreenStrings.HINT_RECHECK))
    }
}

/** 가로 창 아래 넓은 안내 카드 (132:8169): 1602 × 126, 아래 여백 62. */
@Composable
private fun BoxScope.BottomHint(text: androidx.compose.ui.text.AnnotatedString) {
    HintCard(
        text,
        Modifier.align(Alignment.BottomCenter).padding(bottom = 62.dp).size(if (landscape()) 1602.dp else 726.dp, if (landscape()) 126.dp else 180.dp),
        textWidth = if (landscape()) 1001.dp else 676.dp,
    )
}

// ════════════════════════════════════════ W-16 출발 · 다음 쉼터 (132:8173)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun W16Depart(s: WakeonSnapshot) {
    val line = s.caption.firstOrNull { it.id == "V-52" }?.text
    val parts = line?.split(Regex("""(?<=[.?!])\s+""")).orEmpty()
    ParkFrame(s) {
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 334.dp, start = 40.dp, end = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            parts.firstOrNull()?.let { Text(it, style = WakeonType.display3, color = WakeonColors.White, textAlign = TextAlign.Center) }
            if (parts.size > 1) Text(parts.drop(1).joinToString(" "), style = WakeonType.body1, color = WakeonColors.SubText, textAlign = TextAlign.Center)
        }
        FlowRow(
            Modifier.align(Alignment.TopCenter).padding(top = if (landscape()) 592.dp else 700.dp, start = 40.dp, end = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            StatPill(ScreenStrings.STAT_RESTS, "${s.summary.rests}회", Modifier.width(468.dp))
            StatPill(ScreenStrings.STAT_REST_TIME, "${s.summary.restMinutes}분", Modifier.width(398.dp))
            StatPill(ScreenStrings.STAT_WARNS, "${s.summary.warns}회", Modifier.width(349.dp), warn = true)
        }
        BottomHint(rich(ScreenStrings.HINT_BACK_TO_DRIVE))
    }
}

// ════════════════════════════════════════ 시연에 없는 정차 상태 (W-14·W-17·W-18): 같은 틀에 대사만

@Composable
fun ParkGeneric(s: WakeonSnapshot) {
    val parts = sentences(captionText(s))
    ParkFrame(s) {
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 334.dp, start = 40.dp, end = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            parts.firstOrNull()?.let { Text(it, style = WakeonType.display3, color = WakeonColors.White, textAlign = TextAlign.Center) }
            if (parts.size > 1) Text(parts.drop(1).joinToString(" "), style = WakeonType.body1, color = WakeonColors.SubText, textAlign = TextAlign.Center)
        }
    }
}

/** 시안 132:1663의 "◔" 기호(Regular 72, 흰 빛). Pretendard에 없는 기호라 같은 모양을 직접 그린다: 원 테두리 + 오른쪽 위 4분의 1 채움. */
@Composable
private fun PieGlyph(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val r = size.minDimension / 2f * 0.82f
        val c = center
        val glow = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) 1 else 0
        repeat(glow + 1) { pass ->
            val color = if (pass < glow) Color(0x66FFFFFF) else Color.White
            val extra = if (pass < glow) 6f else 0f
            drawCircle(color, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.16f + extra))
            drawArc(color, -90f, 90f, useCenter = true, topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r))
        }
    }
}
