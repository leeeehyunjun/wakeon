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
 * 질문 은행. 시연(DEMO_SCENARIO 장면 2·3·6·7·9·11·20)의 문장을 포함한다.
 * 1차 제출은 정해진 순서로 낸다(질문 다양화 F17은 P1).
 */
object QuestionBank {
    const val KEY_LUNCH = "lunch"
    const val KEY_HOURS = "hours"

    // 대사 카탈로그에 있는 문장은 대사 ID를 그대로 쓴다(V-03, V-10, V-12).
    val KSS = Question(VoiceLines.V03.id, QuestionType.SELF_REPORT, VoiceLines.V03.text)

    /** 출발 전 체크: 자가 보고 1 + 정답형 3 (진행 표시 "1 / 4"). */
    val precheck: List<Question> = listOf(
        KSS,
        Question("Q-P1", QuestionType.ARITH, "7 더하기 5는요?", answer = 12),
        Question("Q-P2", QuestionType.MEMORY_CAPTURE, "점심은 뭐 드셨어요?", memoryKey = KEY_LUNCH),
        Question("Q-P3", QuestionType.MEMORY_CAPTURE, "오늘 몇 시간 가세요?", memoryKey = KEY_HOURS),
    )

    /** 주행 중 대화. 같은 유형이 연달아 나오지 않게 배열했다. 라운드마다 이어서 낸다. */
    val drive: List<Question> = listOf(
        Question("Q-D1", QuestionType.ARITH, "3 더하기 6은요?", answer = 9),
        Question("Q-D2", QuestionType.CHOICE, "강아지와 고양이 중에 뭐가 좋아요?", options = listOf("강아지", "고양이")),
        Question(VoiceLines.V10.id, QuestionType.MEMORY_RECALL, VoiceLines.V10.text, memoryKey = KEY_LUNCH),
        Question(VoiceLines.V12.id, QuestionType.WORD_CHAIN, VoiceLines.V12.text, seed = "기차"),
        Question("Q-D5", QuestionType.MEMORY_RECALL, "아까 몇 시간 간다고 하셨죠?", memoryKey = KEY_HOURS),
        Question("Q-D6", QuestionType.WORD_CHAIN, "끝말잇기 해요. 사과!", seed = "사과"),
        Question("Q-D7", QuestionType.ARITH, "9 더하기 7은요?", answer = 16),
        Question("Q-D8", QuestionType.CHOICE, "바다와 산 중에 어디가 좋아요?", options = listOf("바다", "산")),
        Question("Q-D9", QuestionType.WORD_CHAIN, "끝말잇기 해요. 나무!", seed = "나무"),
        Question("Q-D10", QuestionType.ARITH, "20 빼기 6은요?", answer = 14),
        Question("Q-D11", QuestionType.CHOICE, "봄과 가을 중에 언제가 좋아요?", options = listOf("봄", "가을")),
        Question("Q-D12", QuestionType.WORD_CHAIN, "끝말잇기 해요. 우산!", seed = "우산"),
        Question("Q-D13", QuestionType.ARITH, "8 더하기 6은요?", answer = 14),
        Question("Q-D14", QuestionType.CHOICE, "커피와 녹차 중에 뭐가 좋아요?", options = listOf("커피", "녹차")),
    )

    /** 재출발 체크: 정답형 3문항. 출발 전 체크와 겹치지 않는다. */
    val recheck: List<Question> = listOf(
        Question("Q-R1", QuestionType.ARITH, "12 빼기 4는요?", answer = 8),
        Question("Q-R2", QuestionType.ARITH, "6 더하기 7은요?", answer = 13),
        Question("Q-R3", QuestionType.ARITH, "15 빼기 9는요?", answer = 6),
    )

    val all: List<Question> get() = precheck + drive + recheck

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
    }
}
