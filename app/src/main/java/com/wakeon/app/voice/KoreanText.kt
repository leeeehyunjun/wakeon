package com.wakeon.app.voice

/** 인식 결과 문장을 판정하기 쉽게 다듬는 도구. 내용은 메모리에서만 쓰고 저장하지 않는다. */
object KoreanText {
    private val punctuation = Regex("[\\p{Punct}…·“”‘’「」『』~]")

    /** 문장부호를 지우고 공백을 하나로 줄인다. */
    fun normalize(text: String): String =
        text.replace(punctuation, " ").replace(Regex("\\s+"), " ").trim().lowercase()

    fun tokens(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }

    /** 공백까지 지운 형태(구 일치 검사용). */
    fun compact(text: String): String = normalize(text).replace(" ", "")

    /** 한글 음절만 남긴다. */
    fun hangul(text: String): String = text.filter { it in '가'..'힣' }

    // ── 숫자 읽기: 아라비아 숫자, 한자어 수사(삼, 십이), 고유어 수사(여덟, 열둘)

    private val sinoDigits = mapOf(
        '영' to 0, '공' to 0, '일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5,
        '육' to 6, '륙' to 6, '칠' to 7, '팔' to 8, '구' to 9,
    )
    private val nativeOnes = mapOf(
        "하나" to 1, "한" to 1, "둘" to 2, "두" to 2, "셋" to 3, "세" to 3, "넷" to 4, "네" to 4,
        "다섯" to 5, "여섯" to 6, "일곱" to 7, "여덟" to 8, "아홉" to 9,
    )
    private val nativeTens = mapOf(
        "열" to 10, "스물" to 20, "스무" to 20, "서른" to 30, "마흔" to 40, "쉰" to 50,
    )
    // '이'는 숫자 2이기도 해서 "요"를 먼저 떼어 본다("십이요" → "십이"). 안 되면 "이요"("열둘이요" → "열둘").
    private val suffixes = listOf("입니다", "에요", "예요", "요", "이요", "이에요", "야", "이야", "개", "점")

    /** 문장에서 처음 나오는 숫자를 읽는다. 없으면 null. */
    fun firstNumber(text: String): Int? {
        Regex("\\d+").find(text)?.let { return it.value.toIntOrNull() }
        for (raw in tokens(text)) {
            val token = hangul(raw)
            if (token.isEmpty()) continue
            parseWord(token)?.let { return it }
            for (s in suffixes) {
                if (token.length > s.length && token.endsWith(s)) {
                    parseWord(token.removeSuffix(s))?.let { return it }
                }
            }
        }
        return null
    }

    private fun parseWord(word: String): Int? = parseSino(word) ?: parseNative(word)

    /** 영~구, 십, 백 조합: 삼, 십이, 이십삼, 백. */
    private fun parseSino(word: String): Int? {
        if (word.isEmpty() || word.any { it !in sinoDigits && it != '십' && it != '백' }) return null
        var total = 0
        var current = 0
        for (c in word) {
            when (c) {
                '백' -> { total += (if (current == 0) 1 else current) * 100; current = 0 }
                '십' -> { total += (if (current == 0) 1 else current) * 10; current = 0 }
                else -> {
                    if (current != 0) return null // "삼삼" 같은 나열은 숫자로 보지 않는다
                    current = sinoDigits.getValue(c)
                }
            }
        }
        return total + current
    }

    /** 하나~아홉, 열~쉰 조합: 여덟, 열둘, 스물셋. */
    private fun parseNative(word: String): Int? {
        nativeOnes[word]?.let { return it }
        for ((tensWord, tens) in nativeTens) {
            if (word == tensWord) return tens
            if (word.startsWith(tensWord)) {
                nativeOnes[word.removePrefix(tensWord)]?.let { return tens + it }
            }
        }
        return null
    }
}
