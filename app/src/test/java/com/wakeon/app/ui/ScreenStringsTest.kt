package com.wakeon.app.ui

import com.wakeon.app.ui.screens.ScreenStrings
import com.wakeon.app.ui.screens.rawSentences
import com.wakeon.app.ui.screens.sentences
import com.wakeon.app.voice.VoiceLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScreenStringsTest {

    private fun plain(s: String) = s.replace("**", "")

    @Test
    fun `주행 화면 고정 문구는 한 덩어리 120자 3줄 이하다`() {
        val over = ScreenStrings.driving.filter { plain(it).length > 120 || plain(it).lines().size > 3 }
        assertEquals("주행 화면 규칙(F1 AC-1.3)을 넘는 문구", emptyList<String>(), over)
    }

    @Test
    fun `주행 화면에 보이는 대사도 120자 이하다`() {
        val over = VoiceLines.allForCheck().filter { it.text.length > 120 }
        assertEquals(emptyList<Any>(), over)
    }

    @Test
    fun `필수 고지 문구는 SPEC F12와 같다`() {
        val spec = File("../docs/SPEC.md").readText()
        assertTrue("SPEC F12의 필수 고지 문구와 화면 문구가 다르다", spec.contains("\"${ScreenStrings.CONSENT_NOTICE}\""))
    }

    @Test
    fun `문장 나누기는 마침표만 떼고 물음표는 둔다`() {
        assertEquals(listOf("졸음 신호가 보여요", "가까운 쉼터로 안내할게요"), sentences(VoiceLines.V20.text))
        assertEquals(listOf("혹시 졸리세요?", "'응' 또는 '아니'로 말해 주세요"), sentences(VoiceLines.V15.text))
        assertEquals(listOf("알겠어요.", "그럼 보리 얘기 하나만 더요.", "보리는 산책 좋아해요?"), rawSentences("알겠어요. 그럼 보리 얘기 하나만 더요. 보리는 산책 좋아해요?"))
    }
}
