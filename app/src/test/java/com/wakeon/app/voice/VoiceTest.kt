package com.wakeon.app.voice

import com.wakeon.app.core.WakeonState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanTextTest {
    @Test
    fun `한글 수사와 아라비아 숫자를 모두 읽는다`() {
        assertEquals(3, KoreanText.firstNumber("삼."))
        assertEquals(12, KoreanText.firstNumber("십이."))
        assertEquals(8, KoreanText.firstNumber("팔."))
        assertEquals(8, KoreanText.firstNumber("여덟"))
        assertEquals(12, KoreanText.firstNumber("열둘이요"))
        assertEquals(23, KoreanText.firstNumber("이십삼"))
        assertEquals(12, KoreanText.firstNumber("12요"))
        assertEquals(12, KoreanText.firstNumber("십이요"))
        assertEquals(9, KoreanText.firstNumber("구"))
        assertEquals(4, KoreanText.firstNumber("네 시간 반"))
        assertNull(KoreanText.firstNumber("김치찌개요"))
    }
}

class GrammarTest {
    @Test
    fun `시연 대답이 맞는 의도로 읽힌다`() {
        assertEquals(UserIntent.YES, Grammar.intentIn("응, 부산까지.", WakeonState.S01))
        assertEquals(UserIntent.NO, Grammar.intentIn("아니, 괜찮아.", WakeonState.S06))
        assertEquals(UserIntent.YES, Grammar.intentIn("어… 응.", WakeonState.S08))
        assertEquals(UserIntent.YES, Grammar.intentIn("응, 잘게.", WakeonState.S11))
        assertEquals(UserIntent.AWAKE, Grammar.intentIn("으음… 일어났어.", WakeonState.S12))
    }

    @Test
    fun `의도는 받는 상태에서만 쓴다`() {
        // 대화 중의 "네 시간 반"은 '네'(YES)가 아니라 질문의 대답이다.
        assertNull(Grammar.intentIn("…네 시간… 반?", WakeonState.S04))
        assertNull(Grammar.intentIn("응", WakeonState.S04))
        assertEquals(UserIntent.STOP, Grammar.intentIn("그만해", WakeonState.S04))
        // 시연 장면 21 "고마워"는 문법에 없는 말이라 반응하지 않는다.
        assertNull(Grammar.intentIn("고마워.", WakeonState.S16))
    }

    @Test
    fun `애매한 답과 졸림 표현`() {
        assertEquals(UserIntent.YES_AMBIGUOUS, Grammar.intentIn("조금요", WakeonState.S06))
        assertEquals(UserIntent.YES_AMBIGUOUS, Grammar.intentIn("모르겠어", WakeonState.S06))
        assertEquals(UserIntent.SLEEPY, Grammar.intentIn("아니 근데 좀 졸려", WakeonState.S06))
        assertEquals(UserIntent.SNOOZE_5, Grammar.intentIn("조금만 더", WakeonState.S12))
        assertFalse(UserIntent.YES in Grammar.intents("어디야"))
        assertTrue(UserIntent.YES in Grammar.intents("네요"))
    }
}

class QuestionBankTest {
    @Test
    fun `출발 전 체크 대답의 정답 판정`() {
        val memory = mutableMapOf<String, String>()
        val p = QuestionBank.precheck
        assertTrue(QuestionBank.judge(p[0], "삼.", memory))
        assertTrue(QuestionBank.judge(p[1], "십이.", memory))
        assertTrue(QuestionBank.judge(p[2], "강아지.", memory))
        assertTrue(QuestionBank.judge(p[3], "김치찌개요", memory))
        assertEquals("김치찌개", QuestionBank.memoryCore("김치찌개요"))
        assertTrue(QuestionBank.judge(QuestionBank.recheck[0], "팔.", memory))
        assertFalse(QuestionBank.judge(QuestionBank.recheck[0], "칠", memory))
    }

    @Test
    fun `시연 질문 문장이 은행에 있다`() {
        val texts = QuestionBank.all.map { it.text }
        listOf(
            "지금 졸린 정도를 1에서 9로 말해 주세요.", "7 더하기 5는요?", "강아지와 고양이 중에 뭐가 좋아요?",
            "점심은 뭐 드셨어요?", "12 빼기 4는요?",
        ).forEach { assertTrue("없음: $it", it in texts) }
    }
}

class VoiceLinesTest {
    @Test
    fun `이름 뒤 조사를 받침에 맞춘다`() {
        assertEquals("12km 앞 ○○졸음쉼터를 경유지로 넣을까요?", VoiceLines.v21("12", "○○졸음쉼터").text)
        assertEquals("12km 앞 다라마을을 경유지로 넣을까요?", VoiceLines.v21("12", "다라마을").text)
        assertEquals("27km 앞 가나휴게소를 넣을까요?", VoiceLines.v22("27", "가나휴게소").text.substringAfter("없어요. "))
        assertEquals("1km 앞 ○○졸음쉼터예요. 오른쪽으로 빠지세요.", VoiceLines.v25("1", "○○졸음쉼터", "오른쪽").text)
        assertEquals("1km 앞 다라마을이에요. 왼쪽으로 빠지세요.", VoiceLines.v25("1", "다라마을", "왼쪽").text)
        assertEquals("경유지를 넣지 못했어요. 3km 앞에서 오른쪽으로 빠지세요.", VoiceLines.v29("3", "오른쪽").text)
        assertEquals("○○졸음쉼터를 경유지로 넣었어요. 8분 뒤 도착해요.", VoiceLines.v24("○○졸음쉼터", 8).text)
        assertEquals("다음 쉼터는 38km 앞이에요. 졸리면 바로 쉬세요.", VoiceLines.v52("38").text)
        assertEquals("15분 뒤에 깨워 드릴게요. 편히 쉬세요.", VoiceLines.v33(15).text)
    }
}
