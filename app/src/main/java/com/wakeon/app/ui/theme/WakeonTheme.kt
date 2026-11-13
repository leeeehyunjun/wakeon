package com.wakeon.app.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wakeon.app.R

/**
 * 디자인 시스템 색 16개(피그마 Design 페이지 168:5537, DESIGN.md 2장)와 시안에 직접 쓰인 색.
 * 시안 밖의 색을 새로 만들지 않는다.
 */
object WakeonColors {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Primary950 = Color(0xFF111111)
    val Primary900 = Color(0xFF161616)
    val Primary800 = Color(0xFF1C2124)
    val Secondary800 = Color(0xFF1B2434)
    val Secondary500 = Color(0xFF394041)
    val Secondary300 = Color(0xFF5D727F)
    val Secondary100 = Color(0xFF97ADBC)
    val Point800 = Color(0xFF437783)
    val Point600 = Color(0xFF6BBCD0)
    val Point400 = Color(0xFF4FD0EE)
    val Neutral700 = Color(0xFF2F2F2F)
    val Neutral500 = Color(0xFF474747)
    val Neutral300 = Color(0xFF8A8A8A)
    val Neutral100 = Color(0xFFD8D8D8)

    // ── 시안 값 그대로(토큰에 없는 색)
    /** 안내 카드 바탕 (114:2575) */
    val Card = Color(0xFF131313)
    /** 설정 카드 바탕 (101:1651, 113:2264) */
    val Panel = Color(0xFF1C1C1C)
    /** 보조 버튼 바탕 (101:1633, "+ 5분") */
    val ButtonDark = Color(0xFF101010)
    /** 상태 글자 "질문중"·"듣고 있어요" (114:2590) */
    val StatusText = Color(0xFF4C585F)
    /** W-04 "주행 경과"·"N분 뒤 먼저 말을 걸게요" (114:2390) */
    val WaitText = Color(0xFF7A95A6)
    /** 멈춘 이유 칩 (114:2649) */
    val Chip = Color(0xFF2A3841)
    /** W-13 타이머 숫자 */
    val Timer = Color(0xFFF2F2F2)
    /** W-13·W-16 보조 문장 */
    val SubText = Color(0xFFBDBDBD)
    /** W-12 오른쪽 설명 */
    val InfoText = Color(0xFFA4AAAE)
    /** W-01 권한 설명 */
    val PermissionText = Color(0xFF707A7D)
    /** W-12 카운트다운 테두리 */
    val CountBorder = Color(0xFF334047)
    /** W-16 경고 칸 이름 */
    val WarnLabel = Color(0xFF818283)
    /** W-12 아이콘 자리(시안의 빈 회색 칸) */
    val IconSlot = Color(0xFF303030)
    /** 경고 막대 (114:2834) */
    val WarnFrom = Color(0xFF27D3E6)
    val WarnTo = Color(0xFFFF7165)
    /** 안내 카드 빛 (Rectangle 12 원형 그라데이션) */
    val GlowCenter = Color(0xFF2AB8FF)
    val GlowMid = Color(0xFF2B343B)
    val GlowEdge = Color(0xFF446182)
    /** W-16 경고 칸 그라데이션 끝 */
    val WarnPillEnd = Color(0xFF3C2D2B)
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_thin, FontWeight.Thin),
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private fun style(weight: FontWeight, size: Int, lineHeight: TextUnit = TextUnit.Unspecified) = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    // 한국어를 글자 단위가 아니라 띄어쓰기(어절) 단위로 줄바꿈한다(시안의 word-break: break-word). API 33부터 적용된다.
    lineBreak = LineBreak(LineBreak.Strategy.HighQuality, LineBreak.Strictness.Normal, LineBreak.WordBreak.Phrase),
)

/** 글자 스타일 20개 (DESIGN.md 2장, 단위 px = dp). 크기는 시안 그대로이고 창 배율은 [DesignFrame]이 맞춘다. */
object WakeonType {
    val display1 = style(FontWeight.Medium, 280, 1.0.em)
    val display2 = style(FontWeight.Bold, 128, 1.15.em)
    val display3 = style(FontWeight.SemiBold, 100, 1.3.em)
    val display4 = style(FontWeight.SemiBold, 80, 214.sp)
    val headline1 = style(FontWeight.Bold, 65, 1.15.em)
    val headline2 = style(FontWeight.Bold, 60, 1.3.em)
    val headline3 = style(FontWeight.SemiBold, 55, 1.3.em)
    val title1 = style(FontWeight.Bold, 50, 1.15.em)
    val title2 = style(FontWeight.Bold, 45, 1.3.em)
    val title3 = style(FontWeight.SemiBold, 40, 1.3.em)
    val body1 = style(FontWeight.Normal, 40, 1.3.em)
    val body2 = style(FontWeight.SemiBold, 35, 1.3.em)
    val body3 = style(FontWeight.Bold, 35, 1.3.em)
    val body4 = style(FontWeight.Normal, 34, 1.45.em)
    val caption1 = style(FontWeight.Normal, 30, 1.45.em)
    val caption2 = style(FontWeight.Medium, 30)
    val caption3 = style(FontWeight.SemiBold, 28, 1.3.em)
    val caption4 = style(FontWeight.Bold, 24, 1.3.em)
    val caption5 = style(FontWeight.Normal, 24)

    /** 시안에만 있는 크기(스타일 표에 없음): 질문 62 Bold(W-03·W-10), 큰 문장 Thin 60(W-03 들은 숫자) */
    val question62 = style(FontWeight.Bold, 62, 1.3.em)
    val heardThin = style(FontWeight.Thin, 60, 1.3.em)
    val headlineBold60 = style(FontWeight.Bold, 60, 1.3.em)
    val gear = style(FontWeight.Medium, 30)
    val buttonBold34 = style(FontWeight.Bold, 34, 1.3.em)
    val infoBold50 = title1
    val waitLabel = style(FontWeight.Normal, 33, 1.3.em)
}

/** 창 방향. 정차 화면은 받은 창 폭이 1200dp 이상이면 가로 시안, 아니면 세로로 쌓는다(DESIGN.md 3장). */
enum class FrameKind { Portrait, Landscape }

val LocalFrameKind = staticCompositionLocalOf { FrameKind.Portrait }

/**
 * 시안 좌표(세로 840 × 1184, 가로 1700 × 1184)를 받은 창에 맞춘다. 창 크기는 기기가 정하므로 고정 px를 쓰지 않고,
 * 안쪽에서는 `1.dp = 시안 1px × 배율`이 되도록 밀도를 바꾼다. 글자 배율(fontScale)은 1로 두어 시안 크기를 지킨다.
 * 그래서 화면 코드는 시안 값을 그대로 dp·sp로 쓰고, 남는 높이는 weight로 나눈다.
 *
 * [portraitOnly](주행 화면)이면 창이 넓어도 세로 시안(840)을 가운데 그린다. 정차 화면은 창 폭 1200dp 이상이면 가로 시안.
 */
@Composable
fun DesignFrame(portraitOnly: Boolean, background: Color = WakeonColors.Primary950, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
        val wide = maxWidth.value >= 1200f
        val kind = if (wide && !portraitOnly) FrameKind.Landscape else FrameKind.Portrait
        val designW = if (kind == FrameKind.Landscape) 1700f else 840f
        val scale = minOf(maxWidth.value / designW, maxHeight.value / 1184f)
        val outer = LocalDensity.current
        val frameWidth = if (kind == FrameKind.Portrait && wide) (designW * scale).dp else maxWidth
        Box(Modifier.width(frameWidth).fillMaxHeight()) {
            CompositionLocalProvider(
                LocalDensity provides Density(outer.density * scale, fontScale = 1f),
                LocalFrameKind provides kind,
            ) { content() }
        }
    }
}
