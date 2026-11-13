package com.wakeon.app.ui.components

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wakeon.app.R
import com.wakeon.app.ui.theme.FrameKind
import com.wakeon.app.ui.theme.LocalFrameKind
import com.wakeon.app.ui.theme.WakeonColors
import com.wakeon.app.ui.theme.WakeonType
import com.wakeon.app.vehicle.Gear

// 모든 크기는 시안 px 그대로다. DesignFrame이 받은 창에 맞춰 1.dp의 크기를 바꾼다.

/** "**굵게** 보통" 표기를 굵은 글자 구간이 있는 문장으로 바꾼다. 안내 카드 문구에 쓴다. */
fun rich(markup: String): AnnotatedString = buildAnnotatedString {
    val parts = markup.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

// ════════════════════════════════════════ 헤더: 로고 + 기어 (114:2565, 114:2566)

@Composable
fun WakeonHeader(gear: Gear?, modifier: Modifier = Modifier) {
    val landscape = LocalFrameKind.current == FrameKind.Landscape
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = if (landscape) 80.dp else 56.dp, end = if (landscape) 64.dp else 56.dp, top = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.wk_logo), contentDescription = "Wakeon", modifier = Modifier.size(155.257.dp, 22.dp))
        Spacer(Modifier.weight(1f))
        GearSelector(gear)
    }
}

@Composable
fun GearSelector(gear: Gear?) {
    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        for (g in listOf(Gear.R, Gear.D, Gear.N, Gear.P)) {
            val shape = RoundedCornerShape(22.5.dp)
            Box(
                Modifier
                    .size(75.dp)
                    .clip(shape)
                    .background(WakeonColors.Primary900)
                    .then(if (g == gear) Modifier.innerShadow(Color(0xBF50596C), 27.4.dp, 22.5.dp) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(g.name, style = WakeonType.gear, color = WakeonColors.Neutral300)
            }
        }
    }
}

/** CSS `box-shadow: inset 0 0 blur color`를 흉내 낸다(테두리 안쪽으로 번지는 그림자). */
fun Modifier.innerShadow(color: Color, blur: Dp, radius: Dp) = drawWithContent {
    drawContent()
    val b = blur.toPx()
    val r = radius.toPx()
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = b
            maskFilter = BlurMaskFilter(b / 2f, BlurMaskFilter.Blur.NORMAL)
        }
        val c = canvas.nativeCanvas
        c.save()
        val clip = android.graphics.Path().apply {
            addRoundRect(0f, 0f, size.width, size.height, r, r, android.graphics.Path.Direction.CW)
        }
        c.clipPath(clip)
        c.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        c.restore()
    }
}

// ════════════════════════════════════════ 상태 표시 (114:2584): 작은 그림 + "질문중"

enum class StatusGlyph(@DrawableRes val res: Int, val w: Float, val h: Float, val box: Float) {
    /** 말하는 중·듣는 중: 물결 모양 */
    Wave(R.drawable.wk_status_wave, 50f, 11.37f, 12.701f),
    /** 대화 멈춤: 납작한 선 */
    Line(R.drawable.wk_status_line, 38.5f, 2.04f, 3.065f),
    /** 완료·추가됨: 점 */
    Dot(R.drawable.wk_status_dot, 11.37f, 11.37f, 11.67f),
}

@Composable
fun StatusLabel(text: String?, glyph: StatusGlyph, color: Color = WakeonColors.StatusText, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(glyph.w.dp, glyph.box.dp), contentAlignment = Alignment.Center) {
            val img = Modifier.size(glyph.w.dp, glyph.h.dp).rotate(-1.53f)
            // 시안의 흰 빛(그림자 0.55, 블러 7). 블러는 API 31부터라 그 전에는 빛 없이 그린다.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Image(
                    painterResource(glyph.res), null,
                    img.blur(7.dp, BlurredEdgeTreatment.Unbounded).alpha(0.55f),
                )
            }
            Image(painterResource(glyph.res), null, img)
        }
        if (text != null) {
            Text(text, style = WakeonType.body2, color = color, textAlign = TextAlign.Center)
        }
    }
}

// ════════════════════════════════════════ 점 파형 (114:2578~2583). 주행 화면이라 움직이지 않는다(F1).

/** 높이 192. 점 묶음(267.8 × 192)이 가운데, 그 위로 가운데 선(1210.5 × 10)이 위에서 91 지점을 지난다. */
@Composable
fun VoiceWave(paused: Boolean = false, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(192.dp).clipToBounds(), contentAlignment = Alignment.TopCenter) {
        Image(
            painterResource(if (paused) R.drawable.wk_wave_dots_paused else R.drawable.wk_wave_dots), null,
            Modifier.size(267.798.dp, 192.dp),
        )
        Image(
            painterResource(if (paused) R.drawable.wk_wave_line_paused else R.drawable.wk_wave_line), null,
            Modifier.offset(y = 91.dp).requiredWidth(1210.52.dp).height(10.dp),
        )
    }
}

// ════════════════════════════════════════ 안내 카드 (114:2575): 어두운 카드 + 가운데 푸른 빛 + 한 줄 안내

@Composable
fun HintCard(text: AnnotatedString, modifier: Modifier = Modifier, textWidth: Dp = 676.dp) {
    Box(
        modifier.clip(RoundedCornerShape(30.dp)).background(WakeonColors.Card),
        contentAlignment = Alignment.Center,
    ) {
        CardGlow(Modifier.matchParentSize())
        Text(text, style = WakeonType.body1, color = WakeonColors.White, textAlign = TextAlign.Center, modifier = Modifier.width(textWidth))
    }
}

/**
 * 시안 Rectangle 12: 660 × 99 렌즈 모양을 원형 그라데이션(#2AB8FF → #2B343B → #446182)으로 칠하고 투명도 0.78,
 * 가우시안 블러 81.7(표준편차). 안드로이드 블러 반지름으로는 (81.7 − 0.5) / 0.57735 ≈ 140.
 */
@Composable
fun CardGlow(modifier: Modifier = Modifier) {
    val blur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(140.dp, BlurredEdgeTreatment.Rectangle) else Modifier
    Canvas(modifier.then(blur).alpha(0.78f)) {
        val w = 660.dp.toPx()
        val h = 99.dp.toPx()
        translate(left = (size.width - w) / 2f, top = (size.height - h) / 2f) {
            // 시안 경로를 660 × 99 기준으로 옮김(원본 좌표에서 163.4를 뺌)
            val k = w / 660f
            val p = Path().apply {
                moveTo(0f, 0f)
                cubicTo(0f, 0f, 199.331f * k, 31.911f * k, 330f * k, 31.911f * k)
                cubicTo(460.669f * k, 31.911f * k, 660f * k, 0f, 660f * k, 0f)
                lineTo(660f * k, 99f * k)
                cubicTo(660f * k, 99f * k, 462.037f * k, 56.536f * k, 330f * k, 56.536f * k)
                cubicTo(197.963f * k, 56.536f * k, 0f, 99f * k, 0f, 99f * k)
                close()
            }
            // 원형 그라데이션 중심 (330, 49.5), 반지름 가로 330 · 세로 190.46 → 세로로 늘려 타원을 만든다.
            val center = Offset(330f * k, 49.5f * k)
            scale(scaleX = 1f, scaleY = 190.462f / 330f, pivot = center) {
                drawPath(
                    p,
                    Brush.radialGradient(
                        0f to WakeonColors.GlowCenter, 0.5f to WakeonColors.GlowMid, 1f to WakeonColors.GlowEdge,
                        center = center, radius = 330f * k,
                    ),
                )
            }
        }
    }
}

// ════════════════════════════════════════ 버튼

/** App/Button Primary (101:1632): 높이 146, 둥글기 30, 글자 Bold 34. */
@Composable
fun WkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = WakeonColors.Primary800,
    style: TextStyle = WakeonType.buttonBold34,
    radius: Dp = 30.dp,
    height: Dp = 146.dp,
) {
    Box(
        modifier.height(height).clip(RoundedCornerShape(radius)).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = WakeonColors.White, textAlign = TextAlign.Center)
    }
}

/** 둥근 알약 (114:2876): 높이 146, 둥글기 66. */
@Composable
fun PillBox(modifier: Modifier = Modifier, height: Dp = 146.dp, radius: Dp = 66.dp, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .height(height)
            .shadow(12.dp, RoundedCornerShape(radius), ambientColor = Color(0x26000000), spotColor = Color(0x26000000))
            .clip(RoundedCornerShape(radius))
            .background(WakeonColors.Primary800)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 이름 + 값 알약 (132:7877, W-03b·W-16): 높이 216, 둥글기 60, Bold 45. */
@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    val shape = RoundedCornerShape(60.dp)
    val bg = if (warn) {
        Modifier.background(Brush.verticalGradient(listOf(WakeonColors.Primary800, WakeonColors.WarnPillEnd)), shape)
    } else {
        Modifier.background(WakeonColors.Primary800, shape)
    }
    Row(
        modifier.height(216.dp).clip(shape).then(bg).padding(horizontal = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = WakeonType.title2, color = if (warn) WakeonColors.WarnLabel else WakeonColors.StatusText, maxLines = 1)
        Text(value, style = WakeonType.title2, color = WakeonColors.White, maxLines = 1)
    }
}

/** App/Toggle Lock (101:1641): 103 × 56 바탕 #394041, 흰 손잡이 지름 47.4. */
@Composable
fun WkToggle(on: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(103.385.dp, 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(WakeonColors.Secondary500)
            .then(if (onChange != null) Modifier.clickable { onChange(!on) } else Modifier),
    ) {
        Box(
            Modifier
                .offset(x = (if (on) 75.077f else 28.077f).dp - 23.692.dp, y = 28.dp - 23.692.dp)
                .size(47.385.dp)
                .shadow(6.dp, CircleShape)
                .background(WakeonColors.White, CircleShape),
        )
    }
}

/** 큰 거리 숫자 (114:2872): SemiBold 80, 빛 그림자 세 겹. */
@Composable
fun GlowNumber(text: String, modifier: Modifier = Modifier) {
    val shadows = listOf(
        Shadow(Color(0xBF296B87), Offset(0f, 8f), 18f),
        Shadow(Color(0x997FA6FF), Offset(-5f, -4f), 10f),
        Shadow(Color(0x99FFFFFF), Offset.Zero, 40f),
    )
    Box(modifier) {
        for (s in shadows) {
            Text(text, style = WakeonType.display4.copy(lineHeight = WakeonType.display4.fontSize, shadow = s), color = WakeonColors.White)
        }
    }
}

/**
 * 휴식 칩 (132:1819): 둥근 알약 안에 아이콘 · 글자 · 오른쪽 작은 표시.
 * 시안의 빛 반사(하드라이트·컬러닷지 겹)는 오른쪽 끝을 밝게 하는 그라데이션으로 줄여 그린다.
 */
@Composable
fun RestChip(@DrawableRes icon: Int, iconInCircle: Boolean, iconW: Float, iconH: Float, text: String, active: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(72.28.dp)
    Row(
        modifier
            .shadow(7.5.dp, shape, ambientColor = Color(0x38000000), spotColor = Color(0x38000000))
            .clip(shape)
            .background(WakeonColors.Primary800)
            .background(
                Brush.horizontalGradient(
                    0.8376f to Color(0x33111111), 0.9332f to Color(0x33FFFFFF), 1f to Color(0x33FFFFFF),
                ),
            )
            .then(if (active) Modifier.border(1.dp, WakeonColors.White, shape) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (iconInCircle) {
                Box(Modifier.size(81.677.dp).background(WakeonColors.Black, CircleShape), contentAlignment = Alignment.Center) {
                    Image(painterResource(icon), null, Modifier.size(iconW.dp, iconH.dp))
                }
            } else {
                Image(painterResource(icon), null, Modifier.size(iconW.dp, iconH.dp))
            }
            Text(text, style = WakeonType.caption4, color = WakeonColors.White, maxLines = 1)
        }
        if (active) {
            Image(painterResource(R.drawable.wk_chip_handle), null, Modifier.size(8.218.dp).rotate(-1.53f))
        } else {
            Image(painterResource(R.drawable.wk_chip_union), null, Modifier.size(33.797.dp, 8.218.dp))
        }
    }
}

/** 둥근 사각 영역의 모양 (원 그리기 도우미). */
internal fun roundRect(size: Size, r: Float) = RoundRect(0f, 0f, size.width, size.height, r, r)
