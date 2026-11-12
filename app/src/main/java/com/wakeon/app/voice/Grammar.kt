package com.wakeon.app.voice

import com.wakeon.app.core.WakeonState
import com.wakeon.app.core.WakeonState.S01
import com.wakeon.app.core.WakeonState.S03
import com.wakeon.app.core.WakeonState.S04
import com.wakeon.app.core.WakeonState.S06
import com.wakeon.app.core.WakeonState.S07
import com.wakeon.app.core.WakeonState.S08
import com.wakeon.app.core.WakeonState.S09
import com.wakeon.app.core.WakeonState.S11
import com.wakeon.app.core.WakeonState.S12
import com.wakeon.app.core.WakeonState.S13
import com.wakeon.app.core.WakeonState.S15
import com.wakeon.app.core.WakeonState.S17

/** 사용자 의도 (SPEC 7-3). */
enum class UserIntent {
    YES, YES_AMBIGUOUS, NO, STOP, SLEEPY, START, FIND_REST, AWAKE, SNOOZE_5, ROUTINE, CANCEL,
    FEEDBACK_GOOD, FEEDBACK_BAD,
}

/**
 * 인식 문법 (SPEC 7-3). 의도는 표에 적힌 상태에서만 받는다.
 * 한 문장에 여러 의도가 섞이면 [priority] 순서로 하나를 고른다("아니, 졸려" → SLEEPY).
 */
object Grammar {
    private enum class Mode { EXACT, PREFIX }
    private data class Word(val word: String, val mode: Mode)

    private fun exact(vararg w: String) = w.map { Word(it, Mode.EXACT) }
    private fun prefix(vararg w: String) = w.map { Word(it, Mode.PREFIX) }

    /** 낱말 단위로 보는 말. EXACT는 낱말 그대로 또는 끝에 '요'만 붙은 것("네요"). */
    private val words: Map<UserIntent, List<Word>> = mapOf(
        // '어', '네', '예'는 다른 낱말("어디", "네 시간")과 헷갈리지 않게 낱말 전체로만 받는다.
        UserIntent.YES to prefix("응", "그래", "좋아", "넣어", "쉴래") + exact("어", "네", "예"),
        UserIntent.YES_AMBIGUOUS to prefix("조금", "약간", "모르겠"),
        UserIntent.NO to prefix("아니", "괜찮아", "됐어", "나중에"),
        UserIntent.STOP to prefix("그만"),
        UserIntent.SLEEPY to prefix("졸려", "피곤"),
        UserIntent.AWAKE to prefix("일어났"),
        UserIntent.CANCEL to prefix("취소"),
    )

    /** 공백을 지운 문장 안에서 찾는 구. */
    private val phrases: Map<UserIntent, List<String>> = mapOf(
        UserIntent.STOP to listOf("조용히해"),
        UserIntent.SLEEPY to listOf("졸음와", "졸음이와"),
        UserIntent.START to listOf("웨이크온"),
        UserIntent.FIND_REST to listOf("쉼터안내", "쉼터찾아"),
        UserIntent.SNOOZE_5 to listOf("5분만", "오분만", "조금만더"),
        UserIntent.ROUTINE to listOf("몸깨울래", "스트레칭", "따라말하기", "걷기"),
        UserIntent.FEEDBACK_GOOD to listOf("도움됐", "도움돼"),
        UserIntent.FEEDBACK_BAD to listOf("거슬렸"),
    )

    /** 겹칠 때 앞의 것을 고른다. 졸림 표현은 '아니'보다 앞선다(안전 쪽). */
    private val priority = listOf(
        UserIntent.FEEDBACK_GOOD, UserIntent.FEEDBACK_BAD, UserIntent.CANCEL, UserIntent.START,
        UserIntent.FIND_REST, UserIntent.AWAKE, UserIntent.SNOOZE_5, UserIntent.ROUTINE,
        UserIntent.STOP, UserIntent.SLEEPY, UserIntent.NO, UserIntent.YES_AMBIGUOUS, UserIntent.YES,
    )

    /** 의도를 받는 상태 (7-3 표). SLEEPY는 4-2 전이 표의 S-06 행('졸려' → S-08)도 받는다. */
    private val acceptedIn: Map<UserIntent, Set<WakeonState>> = mapOf(
        UserIntent.YES to setOf(S01, S06, S08, S11, S15),
        UserIntent.YES_AMBIGUOUS to setOf(S06),
        UserIntent.NO to setOf(S01, S06, S08, S11),
        UserIntent.STOP to setOf(S04, S06, S07, S08),
        UserIntent.SLEEPY to setOf(S03, S04, S06),
        UserIntent.START to setOf(S03),
        UserIntent.FIND_REST to setOf(S03, S04),
        UserIntent.AWAKE to setOf(S12),
        UserIntent.SNOOZE_5 to setOf(S12),
        UserIntent.ROUTINE to setOf(S11, S12, S13),
        UserIntent.CANCEL to setOf(S09),
        UserIntent.FEEDBACK_GOOD to setOf(S17),
        UserIntent.FEEDBACK_BAD to setOf(S17),
    )

    /** 문장에 들어 있는 모든 의도. */
    fun intents(text: String): Set<UserIntent> {
        val tokens = KoreanText.tokens(text)
        val compact = KoreanText.compact(text)
        val found = mutableSetOf<UserIntent>()
        for ((intent, list) in words) {
            val hit = list.any { w ->
                tokens.any { t ->
                    when (w.mode) {
                        Mode.EXACT -> t == w.word || t == w.word + "요"
                        Mode.PREFIX -> t.startsWith(w.word)
                    }
                }
            }
            if (hit) found += intent
        }
        for ((intent, list) in phrases) {
            if (list.any { compact.contains(it) }) found += intent
        }
        // "조금만 더"는 미루기 요청이지 애매한 '응'이 아니다.
        if (UserIntent.SNOOZE_5 in found) found -= UserIntent.YES_AMBIGUOUS
        return found
    }

    /** [state]에서 받는 의도 중 우선순위가 가장 높은 것. 없으면 null. */
    fun intentIn(text: String, state: WakeonState): UserIntent? {
        val found = intents(text)
        return priority.firstOrNull { it in found && state in acceptedIn.getValue(it) }
    }
}
