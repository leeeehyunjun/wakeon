package com.wakeon.app.voice

/** 대사 한 줄. 화면 문구와 TTS 문장은 같은 [text]를 쓴다. */
data class Line(val id: String, val text: String)

/**
 * 대사 카탈로그 (SPEC 7-2). 문장을 바꾸려면 SPEC 표를 먼저 바꾸고 여기를 맞춘다.
 * `{ }` 자리는 [fill]로 채운다. 이름 뒤의 조사(를/을, 예요/이에요, 으로/로 …)는 받침에 맞춰 바뀐다.
 * 서비스 이름은 음성에서 '웨이크온'으로 쓴다(화면 표기는 Wakeon).
 */
object VoiceLines {
    val V01 = Line("V-01", "시작하려면 아래 내용을 확인해 주세요.")
    val V02 = Line("V-02", "오늘 장거리 가세요? 출발 전에 30초만 대답해 주세요.")
    val V03 = Line("V-03", "지금 졸린 정도를 1에서 9로 말해 주세요.")
    val V05 = Line("V-05", "출발 전에 15분 쉬고 가시는 걸 권해요.")
    val V06 = Line("V-06", "준비됐어요. 졸릴 만한 때 제가 먼저 말 걸게요.")
    val V07 = Line("V-07", "주행 초반에 조금 더 여쭤볼게요.")
    /** 줄기 첫 질문 예시(체크에서 들은 답으로 시작). 실제 문장은 [ConversationPlanner] 템플릿. */
    val V10 = Line("V-10", "아까 강아지가 더 좋다고 하셨죠. 키우세요?")
    /** 호응 예시: 핵심어를 되받는 짧은 말. 판정하지 않는다("틀렸어요" 금지). */
    val V11_EXAMPLES = listOf("강아지 좋죠.", "한 마리 키우시는구나.", "보리, 이름 예쁘네요.", "그렇군요.").map { Line("V-11", it) }
    /** 호응 + 이어지는 질문 예시 */
    val V12 = Line("V-12", "한 마리 키우시는구나. 이름이 뭐예요?")
    val V13 = Line("V-13", "잘 못 들었어요. 한 번만 다시 말해 주세요.")
    val V14 = Line("V-14", "제 말 들리세요?")
    val V15 = Line("V-15", "혹시 졸리세요? '응' 또는 '아니'로 말해 주세요.")
    val V16 = Line("V-16", "'응'이나 '아니'로만 대답해 주세요.")
    /** 의심 확인에 '아니' → 받고 바로 다음 질문 하나(5-5). 줄기를 이어 갈 때는 [V17_MORE]를 붙인다. */
    val V17 = Line("V-17", "알겠어요.")
    const val V17_MORE = "그럼 {이름} 얘기 하나만 더요."
    const val V17_MORE_GENERIC = "그럼 하나만 더 여쭤볼게요."
    val V20 = Line("V-20", "졸음 신호가 보여요. 가까운 쉼터로 안내할게요.")
    const val V21 = "{거리}km 앞 {쉼터}를 경유지로 넣을까요?"
    const val V22 = "30km 안에 졸음쉼터가 없어요. {거리}km 앞 {휴게소}를 넣을까요?"
    val V22B = Line("V-22b", "이 도로에는 쉼터 정보가 없어요. 가까운 안전한 곳에서 쉬어 가세요.")
    const val V23 = "{거리}km 앞 {쉼터}가 있어요. 기존 경유지 대신 넣을까요?"
    const val V24 = "{쉼터}를 경유지로 넣었어요. {분}분 뒤 도착해요."
    val V24B = Line("V-24b", "대답이 없어서 가까운 쉼터를 넣었어요. 원치 않으면 '취소'라고 하세요.")
    const val V25 = "{n}km 앞 {쉼터}예요. {방향}으로 빠지세요."
    const val V26 = "다음 쉼터는 {거리}km 앞이에요."
    val V27 = Line("V-27", "알겠어요. 15분 뒤에 다시 여쭤볼게요.")
    val V28 = Line("V-28", "오늘은 쉼터 안내를 많이 거절하셨어요. 도착 후 평가에 남겨 주세요.")
    const val V29 = "경유지를 넣지 못했어요. {거리}km 앞에서 {방향}으로 빠지세요."
    /** V-30은 두 문장씩 나눠 말한다(사이 0.5초). */
    val V30_A = Line("V-30", "도착했어요. 15분 쉬어 갈까요?")
    val V30_B = Line("V-30", "커피가 있으면 지금 드세요.")
    val V31 = Line("V-31", "주차하셨어요?")
    val V32 = Line("V-32", "잠깐 눈 붙인 뒤에 하면 더 좋아요. 그래도 지금 할까요?")
    const val V33 = "{분}분 뒤에 깨워 드릴게요. 편히 쉬세요."
    val V34_RECLINE = Line("V-34", "등받이를 뒤로 젖히고 쉬세요.")
    val V34_UPRIGHT = Line("V-34", "등받이를 세워 주세요.")
    val V35 = Line("V-35", "일어나실 시간이에요. 등받이를 세워 주세요.")
    val V36 = Line("V-36", "충분히 주무시고 가세요.")
    val V37 = Line("V-37", "쉬는 중에 출발하셨어요. 20분 뒤에 다시 말을 걸게요.")
    val V40 = Line("V-40", "깨어난 직후엔 몸이 덜 깼어요. 스트레칭, 따라 말하기, 걷기 중에 뭐 할까요?")
    val V41 = Line("V-41", "차 문을 잠그고, 차로 쪽이 아닌 쪽으로 걸으세요.")
    val V50 = Line("V-50", "출발 전에 세 가지만 물어볼게요.")
    const val V51 = "반응이 아직 평소보다 느려요. {분}분만 더 쉬어 가세요."
    const val V52 = "다음 쉼터는 {거리}km 앞이에요. 졸리면 바로 쉬세요."
    val V60 = Line("V-60", "오늘 웨이크온 어땠어요? 도움 됐어요, 거슬렸어요로 말해 주세요.")

    fun v21(km: String, rest: String) = Line("V-21", fill(V21, "거리" to km, "쉼터" to rest))
    fun v22(km: String, serviceArea: String) = Line("V-22", fill(V22, "거리" to km, "휴게소" to serviceArea))
    fun v23(km: String, rest: String) = Line("V-23", fill(V23, "거리" to km, "쉼터" to rest))
    fun v24(rest: String, minutes: Int) = Line("V-24", fill(V24, "쉼터" to rest, "분" to minutes.toString()))
    fun v25(km: String, rest: String, exit: String) =
        Line("V-25", fill(V25, "n" to km, "쉼터" to rest, "방향" to exit))
    fun v26(km: String) = Line("V-26", fill(V26, "거리" to km))
    fun v29(km: String, exit: String) = Line("V-29", fill(V29, "거리" to km, "방향" to exit))
    fun v33(minutes: Int) = Line("V-33", fill(V33, "분" to minutes.toString()))
    fun v51(minutes: Int) = Line("V-51", fill(V51, "분" to minutes.toString()))
    fun v52(km: String) = Line("V-52", fill(V52, "거리" to km))

    /** 금지 표현·길이 검사용: 모든 대사를 예시 값으로 채운 목록. */
    fun allForCheck(): List<Line> = listOf(
        V01, V02, V03, V05, V06, V07, V10, *V11_EXAMPLES.toTypedArray(), V12, V13, V14, V15, V16,
        V17, Line("V-17", fill(V17_MORE, "이름" to "보리")), Line("V-17", V17_MORE_GENERIC), V20,
        v21("12", "가나졸음쉼터"), v22("27", "다라휴게소"), V22B, v23("12", "가나졸음쉼터"),
        v24("가나졸음쉼터", 8), V24B, v25("1", "가나졸음쉼터", "오른쪽"), v26("38"), V27, V28,
        v29("12", "오른쪽"), V30_A, V30_B, V31, V32, v33(15), V34_RECLINE, V34_UPRIGHT, V35, V36,
        V37, V40, V41, V50, v51(5), v52("38"), V60,
    )

    /**
     * `{key}` 자리를 값으로 바꾼다. 자리 바로 뒤에 조사가 오면 값의 마지막 글자 받침에 맞춘다.
     */
    fun fill(template: String, vararg values: Pair<String, String>): String {
        var out = template
        for ((key, value) in values) {
            val token = "{$key}"
            while (true) {
                val at = out.indexOf(token)
                if (at < 0) break
                val rest = out.substring(at + token.length)
                val (josaLen, josa) = Josa.matchAfter(value, rest)
                out = out.substring(0, at) + value + josa + rest.substring(josaLen)
            }
        }
        return out
    }
}

/** 받침에 따라 바뀌는 조사. */
internal object Josa {
    /** (받침 없음, 받침 있음) 짝. 긴 것부터 검사한다. */
    private val pairs = listOf(
        "예요" to "이에요",
        "로" to "으로",
        "를" to "을",
        "가" to "이",
        "는" to "은",
        "와" to "과",
        "랑" to "이랑",
        "면" to "이면",
    )

    fun matchAfter(word: String, following: String): Pair<Int, String> {
        val batchim = lastBatchim(word)
        for ((without, with) in pairs) {
            // 받침 있는 형태를 먼저 본다("으로"가 "로"로 잘못 잡히지 않게).
            for (form in listOf(with, without)) {
                if (following.startsWith(form)) {
                    val chosen = when {
                        batchim == null -> form
                        without == "로" && batchim == RIEUL -> without
                        batchim > 0 -> with
                        else -> without
                    }
                    return form.length to chosen
                }
            }
        }
        return 0 to ""
    }

    private const val RIEUL = 8

    /** 마지막 한글 글자의 받침 번호(0 = 없음). 한글이 아니면 null. 숫자는 읽는 소리로 본다. */
    fun lastBatchim(word: String): Int? {
        val c = word.trim().lastOrNull() ?: return null
        if (c in '0'..'9') return digitBatchim[c - '0']
        if (c !in '가'..'힣') return null
        return (c - '가') % 28
    }

    // 영(ㅇ) 일(ㄹ) 이 삼(ㅁ) 사 오 육(ㄱ) 칠(ㄹ) 팔(ㄹ) 구
    private val digitBatchim = intArrayOf(21, 8, 0, 16, 0, 0, 1, 8, 8, 0)
}
