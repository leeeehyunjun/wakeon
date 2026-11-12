package com.wakeon.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTest {

    /** 주제를 [topic]까지 넘긴 계획기 */
    private fun plannerAt(topic: Topic, memory: MutableMap<String, String>): Pair<ConversationPlanner, Question> {
        val p = ConversationPlanner(memory)
        repeat(Topic.entries.size) {
            val q = p.startNextThread()
            if (p.topic == topic) return p to q
            p.dropThread()
        }
        error("주제 없음: $topic")
    }

    /** 대답을 하고 "호응 + 질문"을 한 문장으로 */
    private fun say(p: ConversationPlanner, answer: String): String {
        val s = p.onAnswer(answer)
        return listOfNotNull(s.ack, s.question?.text).joinToString(" ")
    }

    private val all = mutableListOf<String>()

    private fun check(text: String) {
        all += text
        assertTrue("금지 표현: $text", ForbiddenExpressions.find(text).isEmpty())
        assertTrue("120자 초과: $text", text.length <= 120)
    }

    @Test
    fun `강아지 줄기 - 시연 장면 6·7·9·11의 문장 그대로`() {
        val memory = mutableMapOf(Slot.PET_PREF to "강아지", Slot.LUNCH to "김치찌개", Slot.DESTINATION to "부산")
        val (p, q1) = plannerAt(Topic.PET, memory)
        assertEquals("아까 강아지가 더 좋다고 하셨죠. 키우세요?", q1.text)
        assertEquals("한 마리 키우시는구나. 이름이 뭐예요?", say(p, "네, 한 마리요.").also(::check))
        assertEquals("보리, 이름 예쁘네요. 보리는 몇 살이에요?", say(p, "보리요.").also(::check))
        assertEquals("보리", memory[Slot.PET_NAME])
        assertEquals("세 살이면 한창 뛰어놀 때죠. 보리는 산책 좋아해요?", say(p, "…세 살… 인가?").also(::check))
        assertEquals("세 살", memory[Slot.PET_AGE])
        assertEquals("보리", p.subject)
        val last = p.onAnswer("네, 좋아해요.")
        assertEquals("좋겠네요.", last.ack)
        assertNull("4턴으로 줄기 끝", last.question)
    }

    @Test
    fun `고양이·안 키움·모름 갈래`() {
        val cat = mutableMapOf(Slot.PET_PREF to "고양이")
        val (p, q) = plannerAt(Topic.PET, cat)
        assertEquals("아까 고양이가 더 좋다고 하셨죠. 키우세요?", q.text)
        assertEquals("두 마리 키우시는구나. 이름이 뭐예요?", say(p, "네 두 마리요"))
        assertEquals("콩, 이름 예쁘네요. 콩은 몇 살이에요?", say(p, "콩이요").also(::check))
        assertEquals("다섯 살이면 한창 장난칠 때죠. 콩은 장난감 놀이 좋아해요?", say(p, "다섯 살이요").also(::check))

        val (p2, _) = plannerAt(Topic.PET, mutableMapOf(Slot.PET_PREF to "강아지"))
        assertEquals("그럼 키운다면 이름을 뭐로 지을래요?", say(p2, "아니요, 안 키워요.").also(::check))
        assertEquals("초코, 이름 예쁘네요. 초코랑 제일 먼저 뭐 하고 싶어요?", say(p2, "초코요").also(::check))

        val (p3, _) = plannerAt(Topic.PET, mutableMapOf(Slot.PET_PREF to "강아지"))
        assertEquals("그렇군요. 어릴 때 강아지 키워 본 적 있어요?", say(p3, "글쎄요").also(::check))

        val (p4, q4) = plannerAt(Topic.PET, mutableMapOf())
        assertEquals("혹시 키우는 동물 있어요?", q4.text)
        assertEquals("키우시는구나. 이름이 뭐예요?", say(p4, "네").also(::check))
    }

    @Test
    fun `음식·목적지·가족·음악 줄기`() {
        val m = mutableMapOf(Slot.LUNCH to "김치찌개", Slot.DESTINATION to "부산")
        val (food, f1) = plannerAt(Topic.FOOD, m)
        assertEquals("점심에 김치찌개 드셨다고 하셨죠. 맛있었어요?", f1.text)
        assertEquals("다행이에요. 부산 가면 제일 먼저 뭐 드실 거예요?", say(food, "네, 맛있었어요.").also(::check))
        assertEquals("돼지국밥 좋죠.", say(food, "돼지국밥이요.").also(::check))
        assertEquals("돼지국밥", m[Slot.DINNER])

        val (dest, d1) = plannerAt(Topic.DESTINATION, m)
        assertEquals("부산은 자주 가세요?", d1.text)
        assertEquals("오랜만이시겠어요. 부산 가면 꼭 들르는 곳 있어요?", say(dest, "아니요, 오랜만이에요.").also(::check))
        assertEquals("해운대 좋죠.", say(dest, "해운대요").also(::check))

        val (fam, a1) = plannerAt(Topic.FAMILY, m)
        assertEquals("부산에 누구 만나러 가세요?", a1.text)
        assertEquals("부모님 만나러 가시는구나. 부모님은 잘 지내세요?", say(fam, "부모님이요").also(::check))
        assertEquals("다행이에요.", say(fam, "네, 잘 지내세요.").also(::check))

        val (music, m1) = plannerAt(Topic.MUSIC, m)
        assertEquals("요즘 자주 듣는 노래 있어요?", m1.text)
        assertEquals("그렇군요. 신나는 노래랑 잔잔한 노래 중에 뭐가 좋아요?", say(music, "아니요, 딱히 없어요.").also(::check))
        assertEquals("잔잔한 노래 좋죠.", say(music, "잔잔한 거요").also(::check))

        // 체크에서 들은 답이 없을 때의 첫 질문
        val (food2, f2) = plannerAt(Topic.FOOD, mutableMapOf())
        assertEquals("오늘 점심은 뭐 드셨어요?", f2.text)
        assertEquals("비빔밥 좋죠. 저녁은 뭐 드실 거예요?", say(food2, "비빔밥이요").also(::check))
    }

    @Test
    fun `대답에서 슬롯 꺼내기`() {
        assertEquals("부산", Answer.destination("응, 부산까지."))
        assertEquals("한 마리", Answer.count("네, 한 마리요."))
        assertEquals("보리", Answer.name("이름은 보리예요"))
        assertEquals("보리", Answer.name("보리라고 해요"))
        assertEquals("세 살", Answer.age("…세 살… 인가?"))
        assertEquals("3살", Answer.age("3살이요"))
        assertNull(Answer.name("몰라요"))
        assertTrue(Answer.isNo("아니요, 안 키워요."))
        assertTrue(Answer.isYes("네, 한 마리요."))
    }
}
