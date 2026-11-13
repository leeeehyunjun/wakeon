package com.wakeon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.wakeon.app.core.VoicePhase
import com.wakeon.app.core.WakeonSnapshot
import com.wakeon.app.nav.RestAreaType
import com.wakeon.app.ui.components.GlowNumber
import com.wakeon.app.ui.components.HintCard
import com.wakeon.app.ui.components.PillBox
import com.wakeon.app.ui.components.StatusGlyph
import com.wakeon.app.ui.components.StatusLabel
import com.wakeon.app.ui.components.VoiceWave
import com.wakeon.app.ui.components.WakeonHeader
import com.wakeon.app.ui.components.rich
import com.wakeon.app.ui.theme.WakeonColors
import com.wakeon.app.ui.theme.WakeonType
import com.wakeon.app.vehicle.Gear
import com.wakeon.app.vehicle.LoadReason
import com.wakeon.app.voice.VoiceLines

// 주행 화면 (세로 창 840 × 1184). 시안 좌표 y는 창 위에서부터이고, 헤더(64 + 75 = 139) 아래 본문 영역에 [top]으로 놓는다.
// 주행 화면 규칙(F1): 움직이는 요소 없음, 화면을 봐야 하는 과업 없음. 버튼이 없다.

private const val HEADER_BOTTOM = 139f

/** 본문 안에서 시안의 y 좌표에 놓는다. */
private fun Modifier.top(y: Float) = padding(top = (y - HEADER_BOTTOM).dp)

/** 헤더 + 본문 + 아래 안내 카드(726 × 326, 아래 여백 62). */
@Composable
fun DriveFrame(gear: Gear?, hint: AnnotatedString?, body: @Composable BoxScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(WakeonColors.Primary950)) {
        WakeonHeader(gear)
        Box(Modifier.fillMaxWidth().weight(1f)) { body() }
        if (hint != null) {
            HintCard(hint, Modifier.align(Alignment.CenterHorizontally).size(726.dp, 326.dp))
        } else {
            Spacer(Modifier.height(326.dp))
        }
        Spacer(Modifier.height(62.dp))
    }
}

/** 상태 글자: 말하는 중·듣는 중·인식 결과. 인식 결과는 2초 동안 들은 말을 그대로 보여 준다(SPEC W-05b). */
@Composable
private fun BoxScope.TalkStatus(s: WakeonSnapshot, speaking: String = ScreenStrings.ASKING) {
    val heard = s.heard
    when {
        s.phase == VoicePhase.RESULT && heard != null ->
            StatusLabel("“$heard”", StatusGlyph.Wave, WakeonColors.Point400, Modifier.align(Alignment.TopCenter).top(248f))
        s.phase == VoicePhase.LISTENING ->
            StatusLabel(ScreenStrings.LISTENING, StatusGlyph.Wave, modifier = Modifier.align(Alignment.TopCenter).top(248f))
        else -> StatusLabel(speaking, StatusGlyph.Wave, modifier = Modifier.align(Alignment.TopCenter).top(248f))
    }
}

/** 큰 문장 (headline/1, 왼쪽 56). */
@Composable
private fun BoxScope.Headline(text: String, y: Float = 601f) {
    Text(
        text, style = WakeonType.headline1, color = WakeonColors.White,
        modifier = Modifier.align(Alignment.TopStart).padding(start = 56.dp, end = 58.dp).top(y),
    )
}

/** 큰 문장 아래 한 줄 (SemiBold 40, 114:2830). */
@Composable
private fun BoxScope.SubLine(text: String, y: Float = 699f) {
    Text(
        text, style = WakeonType.title3, color = WakeonColors.White,
        modifier = Modifier.align(Alignment.TopStart).padding(start = 56.dp, end = 58.dp).top(y),
    )
}

// ════════════════════════════════════════ W-04 주행 대기 (114:2384)

@Composable
fun W04Waiting(s: WakeonSnapshot) {
    DriveFrame(s.gear, rich(ScreenStrings.HINT_WAKE)) {
        Column(Modifier.align(Alignment.TopStart).padding(start = 57.dp).top(344.33f), verticalArrangement = Arrangement.spacedBy(5.333.dp)) {
            Text(ScreenStrings.DRIVE_ELAPSED, style = WakeonType.waitLabel, color = WakeonColors.WaitText)
            Text(formatDrive(s.driveMs), style = WakeonType.display2, color = WakeonColors.White)
        }
        s.nextTalkInMs?.let { ms ->
            Text(
                ScreenStrings.nextTalk(ceilMinutes(ms)), style = WakeonType.title3, color = WakeonColors.WaitText,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 57.dp).top(579.66f),
            )
        }
    }
}

// ════════════════════════════════════════ W-05 가벼운 대화 · W-05b 대답 인식 (114:2562, 114:2495, 114:2504, 114:2533)

@Composable
fun W05Talk(s: WakeonSnapshot) {
    DriveFrame(s.gear, rich(ScreenStrings.HINT_STOP)) {
        TalkStatus(s)
        VoiceWave(modifier = Modifier.top(353.5f))
        TalkText(captionText(s))
    }
}

/**
 * 대화 문장은 큰 글자 두 줄까지 보여 준다(시안 공간). 더 길면 마지막 문장(질문)을 큰 글자로 두고,
 * 앞 문장(호응)은 그 위에 작은 글자로 먼저 보인다. 읽는 순서와 말하는 순서가 같다.
 */
@Composable
private fun BoxScope.TalkText(text: String) {
    if (text.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val maxWidth = with(LocalDensity.current) { 726.dp.roundToPx() }
    val lines = measurer.measure(text, WakeonType.headline1, constraints = Constraints(maxWidth = maxWidth)).lineCount
    val parts = rawSentences(text)
    if (lines <= 2 || parts.size < 2) {
        Headline(text)
    } else {
        Text(
            parts.dropLast(1).joinToString(" "), style = WakeonType.title3, color = WakeonColors.WaitText,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 56.dp, end = 58.dp).top(546f),
        )
        Headline(parts.last())
    }
}

// ════════════════════════════════════════ W-06 대화 멈춤 (114:2620)

@Composable
fun W06Paused(s: WakeonSnapshot) {
    DriveFrame(s.gear, rich(ScreenStrings.HINT_RESUME)) {
        StatusLabel(ScreenStrings.PAUSED, StatusGlyph.Line, modifier = Modifier.align(Alignment.TopCenter).top(248f + 4.8f))
        VoiceWave(paused = true, modifier = Modifier.top(353.5f))
        Row(
            Modifier.align(Alignment.TopStart).padding(start = 58.dp).top(473f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            for (r in LoadReason.entries.filter { it in s.loadReasons }) ReasonChip(reasonName(r))
        }
        Headline(ScreenStrings.QUIET)
    }
}

/** 멈춘 이유 칩 (114:2649): 높이 100, 둥글기 30, #2A3841. 켜진 이유만 보인다. */
@Composable
private fun ReasonChip(text: String) {
    Box(
        Modifier
            .height(100.dp)
            .widthIn(min = 183.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(WakeonColors.Chip)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = WakeonType.caption3, color = WakeonColors.White)
    }
}

private fun reasonName(r: LoadReason) = when (r) {
    LoadReason.BRAKE -> ScreenStrings.REASON_BRAKE
    LoadReason.TURN_SIGNAL -> ScreenStrings.REASON_TURN
    LoadReason.NAV_VOICE -> ScreenStrings.REASON_NAV
    LoadReason.GLEO -> ScreenStrings.REASON_GLEO
    LoadReason.CALL -> ScreenStrings.REASON_CALL
}

// ════════════════════════════════════════ W-07 의심 확인 (114:2694) · W-07b 무응답 (114:2723)

@Composable
fun W07Confirm(s: WakeonSnapshot) {
    val parts = sentences(captionText(s).ifEmpty { VoiceLines.V15.text })
    DriveFrame(s.gear, rich(ScreenStrings.HINT_ASK_AGAIN)) {
        TalkStatus(s)
        VoiceWave(modifier = Modifier.top(353.5f))
        Headline(parts.first())
        if (parts.size > 1) SubLine(parts.drop(1).joinToString(" "))
    }
}

@Composable
fun W07bNoAnswer(s: WakeonSnapshot) {
    DriveFrame(s.gear, rich(ScreenStrings.HINT_ALERT)) {
        TalkStatus(s)
        VoiceWave(modifier = Modifier.top(353.5f))
        Headline(VoiceLines.V14.text)
        SubLine(ScreenStrings.ASK_AGAIN)
    }
}

// ════════════════════════════════════════ W-08 단계적 경고 (114:2773)

@Composable
fun W08Warning(s: WakeonSnapshot) {
    val parts = sentences(VoiceLines.V20.text)
    val level = s.warnLevel.coerceIn(1, 3)
    DriveFrame(s.gear, rich(ScreenStrings.HINT_SNOOZE)) {
        StatusLabel(ScreenStrings.GUIDING, StatusGlyph.Wave, modifier = Modifier.align(Alignment.TopCenter).top(248f))
        Text(
            ScreenStrings.warnLevel(level), style = WakeonType.buttonBold34, color = WakeonColors.White,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 56.dp).top(358f),
        )
        WarnBar(level, Modifier.align(Alignment.TopStart).padding(start = 56.dp).top(424f))
        Headline(parts.first(), y = 608f)
        if (parts.size > 1) SubLine(parts[1])
    }
}

/** 경고 단계 막대: 바탕 #1B2434 726 × 28, 채움 #27D3E6 → #FF7165. 시안 2단계 = 551/726. */
@Composable
private fun WarnBar(level: Int, modifier: Modifier = Modifier) {
    val fill = (551f / 726f * level / 2f).coerceAtMost(1f)
    Box(modifier.size(726.dp, 28.dp).clip(RoundedCornerShape(3.dp)).background(WakeonColors.Secondary800)) {
        Box(
            Modifier
                .size((726f * fill).dp, 28.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(WakeonColors.WarnFrom, WakeonColors.WarnTo))),
        )
    }
}

// ════════════════════════════════════════ W-09 쉼터 제안 (114:2842)

@Composable
fun W09Offer(s: WakeonSnapshot) {
    val o = s.offer
    val hint = o?.next?.let { n -> o.nextDistanceKm?.let { rich(ScreenStrings.nextRest(n.name, formatKm(it))) } }
    DriveFrame(s.gear, hint) {
        StatusLabel(null, StatusGlyph.Wave, modifier = Modifier.align(Alignment.TopCenter).top(248f))
        if (o != null) {
            PillBox(Modifier.align(Alignment.TopCenter).top(293f).width(468.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    Text(o.rest.name, style = WakeonType.body3, color = WakeonColors.White, maxLines = 1)
                    Text(ScreenStrings.ADD_WAYPOINT, style = WakeonType.body3, color = WakeonColors.StatusText, maxLines = 1)
                }
            }
            Row(Modifier.align(Alignment.TopStart).padding(start = 58.dp).top(484f), verticalAlignment = Alignment.Bottom) {
                GlowNumber("${formatKm(o.distanceKm)}km")
                o.etaMin?.let {
                    Text(ScreenStrings.about(it), style = WakeonType.body3, color = WakeonColors.StatusText, modifier = Modifier.padding(start = 16.dp, bottom = 22.dp))
                }
            }
        }
        Headline(ScreenStrings.ASK_WAYPOINT, y = 608f)
        SubLine(if (o?.rest?.type == RestAreaType.SERVICE_AREA) ScreenStrings.NEAREST_SERVICE_AREA else ScreenStrings.NEAREST_SHELTER)
    }
}

// ════════════════════════════════════════ W-10 경유지 추가 완료 (114:2999) · W-10b 1km 전 (114:3061)

@Composable
fun W10Added(s: WakeonSnapshot) {
    val o = s.offer
    DriveFrame(s.gear, rich(ScreenStrings.HINT_APPROACH)) {
        if (o != null) {
            StatusLabel(ScreenStrings.addWaypoint(o.rest.name), StatusGlyph.Dot, modifier = Modifier.align(Alignment.TopCenter).top(214f))
        }
        Text(
            ScreenStrings.WAYPOINT_ADDED, style = WakeonType.question62, color = WakeonColors.White, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).top(304f),
        )
        if (o != null) NavCard(s, Modifier.align(Alignment.TopStart).padding(start = 57.dp).top(425f))
        Headline(ScreenStrings.HELP_REST, y = 608f)
    }
}

/** 내비 경유지 카드 (114:3060): 726 × 145, 왼쪽 1/3이 푸른빛(진행 표시). */
@Composable
private fun NavCard(s: WakeonSnapshot, modifier: Modifier = Modifier) {
    val o = s.offer ?: return
    val shape = RoundedCornerShape(23.836.dp)
    val arrive = o.etaMin?.let { formatClock(s.minuteOfDay + it) }
    Column(
        modifier
            .size(726.dp, 145.dp)
            .clip(shape)
            .background(WakeonColors.Primary800)
            .background(
                Brush.horizontalGradient(
                    0f to androidx.compose.ui.graphics.Color(0x334FD0EE), 0.3362f to androidx.compose.ui.graphics.Color(0x334FD0EE),
                    0.33736f to androidx.compose.ui.graphics.Color(0x331C1A1A), 1f to androidx.compose.ui.graphics.Color(0x331C1A1A),
                ),
            )
            .padding(start = 27.dp, end = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(o.rest.name, style = WakeonType.body2, color = WakeonColors.White, modifier = Modifier.weight(1f), maxLines = 1)
            if (arrive != null) Text(ScreenStrings.arriveAt(arrive), style = WakeonType.body2, color = WakeonColors.White)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(21.dp)) {
            Text("↗ ${formatKm1(o.distanceKm)} km", style = WakeonType.caption5, color = WakeonColors.Secondary100)
            o.etaMin?.let { Text("◔ ${it}분", style = WakeonType.caption5, color = WakeonColors.Secondary100) }
        }
    }
}

@Composable
fun W10bApproach(s: WakeonSnapshot) {
    val line = s.caption.firstOrNull { it.id == "V-25" }?.text
    val parts = line?.let { sentences(it) }.orEmpty()
    val km = line?.let { Regex("""^([\d.]+)km 앞 """).find(it)?.groupValues?.get(1) }
    DriveFrame(s.gear, rich(ScreenStrings.HINT_NAV_OVERLAP)) {
        StatusLabel(ScreenStrings.GUIDING, StatusGlyph.Wave, modifier = Modifier.align(Alignment.TopCenter).top(248f))
        if (km != null) GlowNumber("${km}km", Modifier.align(Alignment.TopStart).padding(start = 58.dp).top(484f))
        parts.firstOrNull()?.let { Headline(it.replace(Regex("""^[\d.]+km 앞 """), ""), y = 608f) }
        if (parts.size > 1) SubLine(parts[1])
    }
}

// ════════════════════════════════════════ 시연에 없는 주행 상태 (W-11 미루기 등): 같은 틀에 대사만

@Composable
fun DriveGeneric(s: WakeonSnapshot, status: String) {
    val parts = sentences(captionText(s))
    DriveFrame(s.gear, null) {
        StatusLabel(status, StatusGlyph.Wave, modifier = Modifier.align(Alignment.TopCenter).top(248f))
        parts.firstOrNull()?.let { Headline(it, y = 608f) }
        if (parts.size > 1) SubLine(parts.drop(1).joinToString(" "))
    }
}
