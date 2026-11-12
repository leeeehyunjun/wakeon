package com.wakeon.app.voice

/** 질문 유형 (SPEC F17). */
enum class QuestionType {
    /** 졸림 1~9 자가 보고. 기준선·판정에 넣지 않는다. */
    SELF_REPORT,
    /** 셈. 숫자 일치. */
    ARITH,
    /** 기억할 답을 묻는다(출발 전 체크). 알아들은 답이면 정답. */
    MEMORY_CAPTURE,
    /** 앞서 들은 답을 다시 묻는다. 핵심 단어 포함이면 정답. */
    MEMORY_RECALL,
    /** 끝말잇기. 첫 음절이 앞 말의 끝 음절과 같고 2음절 이상. */
    WORD_CHAIN,
    /** 선택지 중 하나. */
    CHOICE,
    /** 이어가기(F17): 앞 대답에서 이어지는 질문. 정답이 없고 응답 속도를 잰다. 알아들은 대답이면 정답. */
    FOLLOW_UP,
}

data class Question(
    val id: String,
    val type: QuestionType,
    val text: String,
    val answer: Int? = null,
    val memoryKey: String? = null,
    val seed: String? = null,
    val options: List<String> = emptyList(),
) {
    /** 응답 시간 기준선·의심 판정에 넣는 문항인가 (SPEC 5-4: 자가 보고 제외). */
    val measured: Boolean get() = type != QuestionType.SELF_REPORT
}

/**
 * 질문 은행. 시연(DEMO_SCENARIO 장면 2·3·20)의 문장을 포함한다.
 * 주행 중 대화는 고정 목록이 아니라 [ConversationPlanner]의 주제별 줄기로 묻는다.
 */
object QuestionBank {
    const val KEY_LUNCH = Slot.LUNCH
    const val KEY_PET = Slot.PET_PREF

    // 대사 카탈로그에 있는 문장은 대사 ID를 그대로 쓴다(V-03).
    val KSS = Question(VoiceLines.V03.id, QuestionType.SELF_REPORT, VoiceLines.V03.text)

    /**
     * 출발 전 체크: 자가 보고 1 + 정답형 3 (진행 표시 "1 / 4", 30초 안).
     * 강아지·고양이 대답에는 짧게 호응하고("강아지 좋죠.") 다음 문항으로 간다. 답은 주행 중 첫 줄기에 쓴다.
     */
    val precheck: List<Question> = listOf(
        KSS,
        Question("Q-P1", QuestionType.ARITH, "7 더하기 5는요?", answer = 12),
        Question("Q-P2", QuestionType.CHOICE, "강아지와 고양이 중에 뭐가 좋아요?", memoryKey = KEY_PET, options = listOf("강아지", "고양이")),
        Question("Q-P3", QuestionType.MEMORY_CAPTURE, "점심은 뭐 드셨어요?", memoryKey = KEY_LUNCH),
    )

    /** 재출발 체크: 정답형 3문항. 출발 전 체크와 겹치지 않는다. */
    val recheck: List<Question> = listOf(
        Question("Q-R1", QuestionType.ARITH, "12 빼기 4는요?", answer = 8),
        Question("Q-R2", QuestionType.ARITH, "6 더하기 7은요?", answer = 13),
        Question("Q-R3", QuestionType.ARITH, "15 빼기 9는요?", answer = 6),
    )

    val all: List<Question> get() = precheck + recheck

    /** 기억 질문의 답에서 뗄 끝말. */
    private val endings = listOf("먹었어요", "먹었어", "먹었습니다", "이에요", "예요", "이요", "요", "을", "를")

    /** 기억할 핵심 말. "김치찌개요" → "김치찌개", "네 시간 반이요" → "네시간반". */
    fun memoryCore(answer: String): String {
        var core = KoreanText.compact(answer)
        var changed = true
        while (changed) {
            changed = false
            for (e in endings) {
                if (core.length > e.length && core.endsWith(e)) {
                    core = core.removeSuffix(e)
                    changed = true
                }
            }
        }
        return core
    }

    /**
     * 정답 판정. 자가 보고는 1~9 숫자를 알아들으면 정답으로 본다.
     * [memory]는 세션 동안만 메모리에 두는 기억 질문의 답이다.
     */
    fun judge(q: Question, answer: String, memory: Map<String, String>): Boolean = when (q.type) {
        QuestionType.SELF_REPORT -> KoreanText.firstNumber(answer)?.let { it in 1..9 } ?: false
        QuestionType.ARITH -> KoreanText.firstNumber(answer) == q.answer
        QuestionType.MEMORY_CAPTURE -> memoryCore(answer).isNotEmpty()
        QuestionType.MEMORY_RECALL -> {
            val stored = q.memoryKey?.let { memory[it] }
            val said = memoryCore(answer)
            stored != null && said.isNotEmpty() && (said.contains(stored) || stored.contains(said))
        }
        QuestionType.WORD_CHAIN -> {
            val word = KoreanText.hangul(KoreanText.tokens(answer).firstOrNull().orEmpty())
            val seed = q.seed.orEmpty()
            word.length >= 2 && seed.isNotEmpty() && word.first() == seed.last()
        }
        QuestionType.CHOICE -> {
            val said = KoreanText.compact(answer)
            q.options.any { said.contains(it) }
        }
        QuestionType.FOLLOW_UP -> KoreanText.hangul(answer).isNotEmpty() || KoreanText.firstNumber(answer) != null
    }
}
