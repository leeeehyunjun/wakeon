package com.wakeon.app.core

import com.wakeon.app.config.WakeonConfig
import com.wakeon.app.config.WakeonConfig.Companion.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineTest {
    private val config = WakeonConfig()

    private fun ok(ms: Long) = Response(ms, correct = true, valid = true)

    @Test
    fun `출발 전 체크 유효 응답 3개면 중앙값으로 확정한다`() {
        val b = Baseline(config)
        var ready: Pair<Long, Int>? = null
        b.add(ok(900), "precheck") { ms, n -> ready = ms to n }
        b.add(ok(1_000), "precheck") { ms, n -> ready = ms to n }
        assertFalse(b.confirmed)
        assertEquals(config.suspectTempMs, b.thresholdMs)
        b.add(ok(1_300), "precheck") { ms, n -> ready = ms to n }
        assertTrue(b.confirmed)
        assertEquals(1_000L, b.baselineMs)
        assertEquals(1_000L to 3, ready)
        assertEquals("precheck", b.source)
        assertEquals(1_500L, b.thresholdMs)
    }

    @Test
    fun `확정 뒤에도 5개까지 더해 중앙값을 갱신하고 그 뒤로는 그대로 둔다`() {
        val b = Baseline(config)
        listOf(1_000L, 1_000L, 1_000L).forEach { b.add(ok(it), "precheck") }
        assertTrue(b.add(ok(2_000), "drive"))
        assertEquals(1_000L, b.baselineMs)
        assertTrue(b.add(ok(2_000), "drive"))
        assertEquals(5, b.count)
        assertEquals(1_000L, b.baselineMs)
        assertFalse(b.add(ok(5_000), "drive"))
        assertEquals(1_000L, b.baselineMs)
    }

    @Test
    fun `3개 미만이면 임시 기준 2000ms를 쓰고 주행 대화로 채워 확정한다`() {
        val b = Baseline(config)
        b.add(ok(1_200), "precheck")
        b.add(ok(1_400), "precheck")
        assertNull(b.baselineMs)
        assertEquals(2_000L, b.thresholdMs)
        b.add(ok(1_000), "drive")
        assertEquals(1_200L, b.baselineMs)
        assertEquals("drive", b.source)
    }

    @Test
    fun `제외 대상 응답은 기준선에 들어가지 않는다`() {
        val b = Baseline(config)
        // 고부하 중 응답·인식 실패·무응답·자가 보고는 valid=false로 들어온다.
        assertFalse(b.add(Response(400, correct = true, valid = false), "precheck"))
        listOf(1_000L, 1_100L, 1_200L).forEach { b.add(ok(it), "precheck") }
        assertEquals(3, b.count)
        assertEquals(1_100L, b.baselineMs)
    }

    @Test
    fun `짝수 개의 중앙값은 가운데 둘의 평균`() {
        assertEquals(1_050L, Baseline.median(listOf(1_000, 1_200, 900, 1_100)))
    }
}

class SuspicionTest {
    private val config = WakeonConfig()

    @Test
    fun `S1 - 시연 장면 6·7·9의 1점2·1점7·2점8초는 B 1초에서 의심이다`() {
        val threshold = (1_000 * config.suspectLatencyRatio).toLong()
        assertTrue(Suspicion.s1Of(listOf(1_200, 1_700, 2_800), threshold, config))

        val s = Suspicion(config)
        listOf(1_200L, 1_700L).forEach { s.record(Response(it, true, true)) }
        assertFalse("장면 7까지는 아직 1개만 넘음", Suspicion.s1Of(listOf(1_000, 1_200, 1_700), threshold, config))
        s.record(Response(2_800, true, true))
        assertTrue(s.s1(threshold))
    }

    @Test
    fun `S1 - 무효 응답은 최근 3개에 넣지 않는다`() {
        val s = Suspicion(config)
        s.record(Response(1_700, true, true))
        s.record(Response(9_000, true, valid = false))
        s.record(Response(1_000, true, true))
        assertFalse(s.s1(1_500))
    }

    @Test
    fun `S2 - 같은 라운드 오답 2회`() {
        val s = Suspicion(config)
        s.record(Response(1_000, correct = false, valid = true))
        assertFalse(s.s2())
        s.record(Response(1_000, correct = false, valid = true))
        assertTrue(s.s2())
        s.newRound()
        assertFalse(s.s2())
    }
}

class DriveTriggersTest {
    private val config = WakeonConfig()
    private val night = 22 * 60 + 46

    @Test
    fun `C1 - 연속 주행 2시간을 넘는 순간 라운드 간격과 상관없이 시작한다`() {
        val t = DriveTriggers(config)
        t.jumpDriveTo(102 * MINUTE_MS, night, speedKmh = 98f)
        assertNull(t.due(night, gapOver = false))
        t.jumpDriveTo(124 * MINUTE_MS, night, speedKmh = 98f)
        assertEquals(TriggerId.C1, t.due(night, gapOver = false))
        assertNull("한 번 넘은 뒤에는 간격을 따른다", t.due(night, gapOver = false))
    }

    @Test
    fun `시연으로 시간을 건너뛰면 이미 참이던 C2는 새로 시작하지 않고, 다음 대화는 C1까지 남은 시간이다`() {
        val t = DriveTriggers(config)
        t.jumpDriveTo(102 * MINUTE_MS, night, speedKmh = 98f)
        assertTrue(t.isTrue(TriggerId.C2, night))
        assertNull(t.due(night, gapOver = true))
        assertEquals(18 * MINUTE_MS, t.nextTalkInMs(night, gapRemainingMs = 0))
    }

    @Test
    fun `C2 - 야간 30분 주행이 되는 순간 시작한다`() {
        val t = DriveTriggers(config)
        t.advance(29 * MINUTE_MS, driving = true, speedKmh = 60f)
        assertNull(t.due(night, gapOver = true))
        t.advance(MINUTE_MS, driving = true, speedKmh = 60f)
        assertEquals(TriggerId.C2, t.due(night, gapOver = true))
        assertNull("낮 4시는 시간대 밖", DriveTriggers(config).also { it.advance(40 * MINUTE_MS, true, 50f) }.due(16 * 60, true))
    }

    @Test
    fun `C3 - 70km 이상 10분 연속, 끄면 동작하지 않는다`() {
        val t = DriveTriggers(config)
        t.advance(10 * MINUTE_MS, driving = true, speedKmh = 98f)
        assertEquals(TriggerId.C3, t.due(16 * 60, gapOver = true))
        val off = DriveTriggers(config).apply { enabled -= TriggerId.C3 }
        off.advance(10 * MINUTE_MS, driving = true, speedKmh = 98f)
        assertNull(off.due(16 * 60, gapOver = true))
    }

    @Test
    fun `정차 중에는 연속 주행 시간이 늘지 않고 리셋하면 0이 된다`() {
        val t = DriveTriggers(config)
        t.advance(5 * MINUTE_MS, driving = true, speedKmh = 50f)
        t.advance(5 * MINUTE_MS, driving = false, speedKmh = 0f)
        assertEquals(5 * MINUTE_MS, t.driveMs)
        t.reset()
        assertEquals(0L, t.driveMs)
    }
}
