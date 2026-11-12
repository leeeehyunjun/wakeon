package com.wakeon.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ForbiddenExpressionsTest {

    private fun appTexts(): List<Pair<String, String>> =
        VoiceLines.allForCheck().map { it.id to it.text } +
            QuestionBank.all.map { it.id to it.text } +
            stringResources()

    /** res 아래 모든 strings.xml의 문자열. */
    private fun stringResources(): List<Pair<String, String>> {
        val res = File("src/main/res")
        assertTrue("res 폴더를 찾지 못함: ${res.absolutePath}", res.isDirectory)
        val pattern = Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        return res.walkTopDown()
            .filter { it.name == "strings.xml" }
            .flatMap { file -> pattern.findAll(file.readText()).map { "${file.parentFile.name}/${it.groupValues[1]}" to it.groupValues[2] } }
            .toList()
    }

    @Test
    fun `모든 대사·질문·화면 문자열에 금지 표현이 없다`() {
        val hits = appTexts().mapNotNull { (id, text) ->
            ForbiddenExpressions.find(text).takeIf { it.isNotEmpty() }?.let { "$id: \"$text\" → $it" }
        }
        assertEquals("금지 표현 발견:\n" + hits.joinToString("\n"), emptyList<String>(), hits)
    }

    @Test
    fun `검사기는 금지 표현을 실제로 잡는다`() {
        assertEquals(listOf("안전해요"), ForbiddenExpressions.find("이제 안전해요."))
        assertEquals(listOf("졸음을 감지합니다"), ForbiddenExpressions.find("졸음을감지합니다"))
        assertEquals(listOf("괜찮아요"), ForbiddenExpressions.find("괜찮아요, 계속 가세요."))
    }

    @Test
    fun `사용자 대답 예시는 검사 대상이 아니다`() {
        // 문법의 NO 낱말 "괜찮아"는 사용자가 하는 말이라 앱 문자열 목록에 넣지 않는다.
        assertTrue(appTexts().none { it.second == "아니, 괜찮아." })
    }

    @Test
    fun `대사는 한 덩어리 120자 이하다`() {
        val long = VoiceLines.allForCheck().filter { it.text.length > 120 }
        assertEquals(emptyList<Line>(), long)
    }

    @Test
    fun `화면 이름은 Wakeon, 음성은 웨이크온으로 쓴다`() {
        val spoken = VoiceLines.allForCheck() + QuestionBank.all.map { Line(it.id, it.text) }
        assertTrue(spoken.none { it.text.contains("Wakeon", ignoreCase = true) })
    }
}
