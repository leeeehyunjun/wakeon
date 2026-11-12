package com.wakeon.app.voice

/**
 * 앱이 말하거나 화면에 쓰면 안 되는 표현 (SPEC 7-5, AC-6.1, AC-11.3).
 * 모든 대사·질문·화면 문자열을 단위 테스트로 검사한다. 사용자 대답 예시("아니, 괜찮아")는 대상이 아니다.
 */
object ForbiddenExpressions {
    val all: List<String> = listOf(
        "졸음을 감지합니다",
        "안전해요",
        "괜찮아요",
        "졸리지 않네요",
        "틀렸어요",
        "위험합니다",
        "수면장애",
        "사고를 예방",
        "운전하셔도 돼요",
        "괜찮아 보여요",
    )

    /** [text]에 들어 있는 금지 표현. 띄어쓰기가 달라도 잡는다. */
    fun find(text: String): List<String> {
        val compact = text.replace(" ", "")
        return all.filter { compact.contains(it.replace(" ", "")) }
    }
}
