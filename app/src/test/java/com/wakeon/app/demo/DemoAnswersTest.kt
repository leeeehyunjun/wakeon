package com.wakeon.app.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DemoAnswersTest {
    @Test
    fun `시연 대답은 말 시작(응답 시간) 뒤 말하는 시간만큼 지나서 넘긴다`() {
        val q = DemoAnswers()
        q.add("…3살… 인가?", 2_800)
        q.add("8.", 1_100, 600)
        val a = q.poll()!!
        assertEquals(2_800L, a.latencyMs) // 응답 시간은 말 시작까지(SPEC 5-4)
        assertEquals(DemoAnswers.DEFAULT_SPEECH_MS, a.speechMs)
        assertEquals(2_800L + DemoAnswers.DEFAULT_SPEECH_MS, a.deliverAfterMs)
        assertEquals(1_700L, q.poll()!!.deliverAfterMs)
        assertNull(q.poll())
    }
}
