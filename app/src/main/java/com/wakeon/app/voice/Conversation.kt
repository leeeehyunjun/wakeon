package com.wakeon.app.voice

/**
 * 깨우는 스몰토크 (SPEC F4 "깨우는 스몰토크 원칙", F17 '이어가기' 유형).
 * - 출발 전 체크에서 들은 답(점심, 강아지/고양이, 목적지)으로 줄기를 시작한다.
 * - 대답을 받으면 핵심어를 되받는 짧은 호응 + 그 대답에서 이어지는 질문을 함께 말한다. 판정하지 않는다.
 * - 대답에서 들은 이름·나이 같은 슬롯은 세션 메모리([memory])에만 두고 세션이 끝나면 지운다.
 */
object Slot {
    const val DESTINATION = "destination"
    const val LUNCH = "lunch"
    const val PET_PREF = "pet_pref"
    const val PET_COUNT = "pet_count"
    const val PET_NAME = "pet_name"
    const val PET_AGE = "pet_age"
    const val DINNER = "dinner"
    const val PLACE = "place"
    const val WHO = "who"
    const val SONG = "song"
}

enum class Topic(val label: String) {
    FOOD("음식"), PET("반려동물"), DESTINATION("목적지"), FAMILY("가족"), MUSIC("음악"),
}

/** 대화 한 걸음: 호응(없을 수 있음) + 이어지는 질문(없으면 줄기 끝). */
data class TalkStep(val ack: String?, val question: Question?)

/** 주제별 줄기 템플릿과 갈래. 질문 id는 `T-주제-번호`. */
class ConversationPlanner(private val memory: MutableMap<String, String>) {
    private val order = listOf(Topic.FOOD, Topic.PET, Topic.DESTINATION, Topic.FAMILY, Topic.MUSIC)
    /** 첫 주제의 순서 번호. 시연에서는 반려동물부터 시작한다([startFrom], docs/DECISIONS.md 2026-11-13). */
    private var firstIndex = 0
    private var topicCursor = 0

    var topic: Topic? = null
        private set
    private var turn: String? = null

    /** 지금 줄기에서 이야기하는 대상(예: 반려동물 이름). '아니' 뒤 "그럼 보리 얘기 하나만 더요."에 쓴다. */
    val subject: String?
        get() = if (topic == Topic.PET) memory[Slot.PET_NAME] else null

    /** 다음 주제의 첫 질문. 차례대로 돌고, 한 바퀴 돌면 다시 처음부터. */
    fun startNextThread(): Question {
        val t = order[topicCursor % order.size]
        topicCursor++
        val q = first(t)
        topic = t
        turn = q.id
        return q
    }

    /** 무응답·멈춤 등으로 줄기를 접는다. */
    fun dropThread() {
        topic = null
        turn = null
    }

    /** 지금 질문에 대한 대답 → 호응과 다음 질문. 줄기가 끝나면 question = null. */
    fun onAnswer(answer: String): TalkStep {
        if (topic == null) return TalkStep(null, null)
        val step = next(turn.orEmpty(), answer)
        turn = step.question?.id
        if (step.question == null) topic = null
        return step
    }

    fun reset() {
        topicCursor = firstIndex
        dropThread()
    }

    /** 다음 줄기를 [t]부터 차례로 시작한다. [reset] 뒤에도 유지된다. */
    fun startFrom(t: Topic) {
        firstIndex = order.indexOf(t)
        topicCursor = firstIndex
    }

    // ════════════════════════════════════════ 줄기 시작

    private fun first(t: Topic): Question = when (t) {
        Topic.FOOD -> memory[Slot.LUNCH]?.let { q("T-FOOD-1", "점심에 {점심} 드셨다고 하셨죠. 맛있었어요?", "점심" to it) }
            ?: q("T-FOOD-1A", "오늘 점심은 뭐 드셨어요?")
        Topic.PET -> petAnimal()?.let { q("T-PET-1", "아까 {동물}가 더 좋다고 하셨죠. 키우세요?", "동물" to it) }
            ?: q("T-PET-1A", "혹시 키우는 동물 있어요?")
        Topic.DESTINATION -> memory[Slot.DESTINATION]?.let { q("T-DEST-1", "{목적지}는 자주 가세요?", "목적지" to it) }
            ?: q("T-DEST-1A", "오늘은 어디까지 가세요?")
        Topic.FAMILY -> memory[Slot.DESTINATION]?.let { q("T-FAM-1", "{목적지}에 누구 만나러 가세요?", "목적지" to it) }
            ?: q("T-FAM-1A", "오늘 누구 만나러 가세요?")
        Topic.MUSIC -> q("T-MUSIC-1", "요즘 자주 듣는 노래 있어요?")
    }

    // ════════════════════════════════════════ 갈래

    private fun next(turnId: String, a: String): TalkStep = when (turnId) {
        // 음식
        "T-FOOD-1" -> step(byYesNo(a, "다행이에요.", "아쉬웠겠어요."), foodNext())
        "T-FOOD-1A" -> {
            val lunch = keep(Slot.LUNCH, Answer.core(a))
            step(lunch?.let { "$it 좋죠." } ?: GENERIC, foodNext())
        }
        "T-FOOD-2", "T-FOOD-2A" -> {
            val dinner = keep(Slot.DINNER, Answer.core(a))
            end(dinner?.let { "$it 좋죠." } ?: GENERIC)
        }

        // 반려동물
        "T-PET-1", "T-PET-1A" -> when {
            Answer.isNo(a) -> step(null, q("T-PET-N2", "그럼 키운다면 이름을 뭐로 지을래요?"))
            Answer.isYes(a) -> {
                val count = keep(Slot.PET_COUNT, Answer.count(a))
                step(count?.let { "$it 키우시는구나." } ?: "키우시는구나.", q("T-PET-2", "이름이 뭐예요?"))
            }
            else -> step(GENERIC, q("T-PET-U2", "어릴 때 {동물} 키워 본 적 있어요?", "동물" to (petAnimal() ?: "동물")))
        }
        "T-PET-2" -> {
            val name = keep(Slot.PET_NAME, Answer.name(a))
            if (name != null) {
                step(fill("{이름}, 이름 예쁘네요.", "이름" to name), q("T-PET-3", "{이름}는 몇 살이에요?", "이름" to name))
            } else {
                step(GENERIC, q("T-PET-3", "몇 살이에요?"))
            }
        }
        "T-PET-3" -> {
            val age = keep(Slot.PET_AGE, Answer.age(a))
            val cat = memory[Slot.PET_PREF] == CAT
            val ack = age?.let { fill(if (cat) "{나이}면 한창 장난칠 때죠." else "{나이}면 한창 뛰어놀 때죠.", "나이" to it) } ?: GENERIC
            step(ack, petLast(cat))
        }
        "T-PET-4" -> end(byYesNo(a, "좋겠네요.", GENERIC))
        "T-PET-N2" -> {
            val name = keep(Slot.PET_NAME, Answer.name(a))
            if (name != null) {
                step(fill("{이름}, 이름 예쁘네요.", "이름" to name), q("T-PET-N3", "{이름}랑 제일 먼저 뭐 하고 싶어요?", "이름" to name))
            } else {
                end(GENERIC)
            }
        }
        "T-PET-N3" -> end("좋네요.")
        "T-PET-U2" -> end(byYesNo(a, "좋은 추억이겠어요.", GENERIC))

        // 목적지
        "T-DEST-1" -> step(byYesNo(a, "자주 가시는구나.", "오랜만이시겠어요."), destNext())
        "T-DEST-1A" -> {
            val dest = keep(Slot.DESTINATION, Answer.destination(a) ?: Answer.core(a))
            step(dest?.let { fill("{목적지}, 좋죠.", "목적지" to it) } ?: GENERIC, destNext())
        }
        "T-DEST-2" -> {
            val place = if (Answer.isNo(a)) null else keep(Slot.PLACE, Answer.core(a))
            end(place?.let { "$it 좋죠." } ?: GENERIC)
        }

        // 가족
        "T-FAM-1", "T-FAM-1A" -> {
            val who = if (Answer.isNo(a)) null else keep(Slot.WHO, Answer.core(a))
            if (who != null) {
                step(fill("{누구} 만나러 가시는구나.", "누구" to who), q("T-FAM-2", "{누구}는 잘 지내세요?", "누구" to who))
            } else {
                step(GENERIC, q("T-FAM-2A", "그럼 가서 제일 먼저 뭐 하실 거예요?"))
            }
        }
        "T-FAM-2" -> end(byYesNo(a, "다행이에요.", "걱정되시겠어요."))
        "T-FAM-2A" -> end("좋네요.")

        // 음악
        "T-MUSIC-1" -> {
            val song = if (Answer.isNo(a) || Answer.isYes(a) && Answer.core(a).isNullOrEmpty()) null else keep(Slot.SONG, Answer.core(a))
            step(song?.let { "$it 좋죠." } ?: GENERIC, q("T-MUSIC-2", "신나는 노래랑 잔잔한 노래 중에 뭐가 좋아요?", options = listOf("신나는", "잔잔한")))
        }
        "T-MUSIC-2" -> {
            val pick = listOf("신나는", "잔잔한").firstOrNull { KoreanText.compact(a).contains(it) }
            end(pick?.let { "$it 노래 좋죠." } ?: GENERIC)
        }

        else -> end(GENERIC)
    }

    private fun foodNext(): Question = memory[Slot.DESTINATION]
        ?.let { q("T-FOOD-2", "{목적지} 가면 제일 먼저 뭐 드실 거예요?", "목적지" to it) }
        ?: q("T-FOOD-2A", "저녁은 뭐 드실 거예요?")

    private fun destNext(): Question = memory[Slot.DESTINATION]
        ?.let { q("T-DEST-2", "{목적지} 가면 꼭 들르는 곳 있어요?", "목적지" to it) }
        ?: q("T-DEST-2", "가면 꼭 들르는 곳 있어요?")

    private fun petLast(cat: Boolean): Question {
        val name = memory[Slot.PET_NAME]
        val text = when {
            name != null && cat -> fill("{이름}는 장난감 놀이 좋아해요?", "이름" to name)
            name != null -> fill("{이름}는 산책 좋아해요?", "이름" to name)
            cat -> "장난감 놀이 좋아해요?"
            else -> "산책 좋아해요?"
        }
        return Question("T-PET-4", QuestionType.FOLLOW_UP, text)
    }

    private fun petAnimal(): String? = memory[Slot.PET_PREF]?.takeIf { it == DOG || it == CAT }

    private fun byYesNo(a: String, yes: String, no: String) = when {
        Answer.isNo(a) -> no
        Answer.isYes(a) -> yes
        else -> GENERIC
    }

    private fun keep(slot: String, value: String?): String? {
        val v = value?.takeIf { it.isNotBlank() && !Answer.isUnknown(it) } ?: return null
        memory[slot] = v
        return v
    }

    private fun step(ack: String?, q: Question) = TalkStep(ack, q)
    private fun end(ack: String?) = TalkStep(ack, null)

    private fun q(id: String, template: String, vararg values: Pair<String, String>, options: List<String> = emptyList()) =
        Question(id, QuestionType.FOLLOW_UP, fill(template, *values), options = options)

    private fun fill(template: String, vararg values: Pair<String, String>) = VoiceLines.fill(template, *values)

    companion object {
        const val DOG = "강아지"
        const val CAT = "고양이"
        /** 판정하지 않는 기본 호응 */
        const val GENERIC = "그렇군요."
    }
}

/** 대답에서 핵심어·슬롯을 꺼내는 도우미. 인식 문장은 판정에만 쓰고 저장하지 않는다(슬롯 값만 세션 메모리). */
object Answer {
    private val unknownWords = listOf("모르", "몰라", "글쎄", "기억안", "생각안")
    private val leadingFillers = setOf("네", "예", "응", "어", "음", "으음", "아", "그냥", "음…", "그", "저", "제", "저희", "우리", "이름은", "이름이", "걔", "얘", "걔는", "얘는")
    private val trailingVerbs = setOf("해요", "합니다", "해", "했어요", "있어요", "있어", "같아요", "같아", "인가", "인가요", "거든요")
    private val endings = listOf("이라고요", "라고요", "이라고", "라고", "이에요", "예요", "이요", "입니다", "요", "이야", "야", "을", "를", "이랑", "랑", "하고")

    fun isUnknown(text: String) = KoreanText.compact(text).let { c -> unknownWords.any { c.contains(it) } }

    fun isNo(text: String): Boolean {
        val c = KoreanText.compact(text)
        return UserIntent.NO in Grammar.intents(text) || listOf("안키워", "안키우", "없어", "없어요", "없습니다").any { c.contains(it) }
    }

    fun isYes(text: String): Boolean {
        if (isNo(text)) return false
        val c = KoreanText.compact(text)
        return UserIntent.YES in Grammar.intents(text) || listOf("키워", "마리", "있어", "맞아", "좋아").any { c.contains(it) }
    }

    /** "네, 한 마리요." → "한 마리" */
    fun count(text: String): String? =
        Regex("(한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|\\d+)\\s*마리").find(KoreanText.normalize(text))?.let { "${it.groupValues[1]} 마리" }

    /**
     * "…세 살… 인가?" → "세 살", "…3살… 인가?" → "세 살".
     * 앱이 되받아 말하는 값이라 숫자는 읽는 말(고유어 수)로 바꾼다. 화면의 인식 결과는 들은 그대로(숫자) 보여 준다.
     */
    fun age(text: String): String? {
        Regex("(\\S+)\\s*살").find(KoreanText.normalize(text))?.let { m ->
            val n = m.groupValues[1]
            val value = KoreanText.firstNumber(n) ?: return null
            if (!n.any { it.isDigit() }) return "$n 살"
            return KoreanText.nativeCounter(value)?.let { "$it 살" } ?: "${n}살"
        }
        return null
    }

    /** "보리요." / "이름은 보리예요" / "보리라고 해요" → "보리" */
    fun name(text: String): String? {
        if (isUnknown(text) || isNo(text)) return null
        val words = KoreanText.tokens(text).filter { it !in leadingFillers && it !in trailingVerbs }
        val word = words.lastOrNull() ?: return null
        return stripEnding(KoreanText.hangul(word)).takeIf { it.isNotEmpty() }
    }

    /** "응, 부산까지." → "부산" */
    fun destination(text: String): String? {
        for (t in KoreanText.tokens(text)) {
            val h = KoreanText.hangul(t)
            for (suffix in listOf("까지", "으로", "에")) {
                if (h.length > suffix.length && h.endsWith(suffix)) return h.removeSuffix(suffix)
            }
        }
        return null
    }

    /** 대답의 핵심어: "돼지국밥이요." → "돼지국밥", "네, 부모님이요" → "부모님" */
    fun core(text: String): String? {
        if (isUnknown(text)) return null
        val words = KoreanText.tokens(text).filter { it !in leadingFillers && it !in trailingVerbs }
        if (words.isEmpty()) return null
        val joined = words.joinToString(" ")
        val last = stripEnding(KoreanText.hangul(words.last()))
        return (words.dropLast(1) + last).joinToString(" ").takeIf { last.isNotEmpty() } ?: joined
    }

    private fun stripEnding(word: String): String {
        var w = word
        var changed = true
        while (changed) {
            changed = false
            for (e in endings) {
                if (w.length > e.length && w.endsWith(e)) {
                    w = w.removeSuffix(e)
                    changed = true
                    break
                }
            }
        }
        return w
    }
}
