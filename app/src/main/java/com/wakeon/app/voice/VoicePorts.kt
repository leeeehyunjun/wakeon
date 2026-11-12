package com.wakeon.app.voice

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.wakeon.app.TAG
import com.wakeon.app.core.Sound

/** 음성 출력. 순서: Pleos TTS → 안드로이드 TTS (단계 4). */
interface Speaker {
    /** [lines]를 차례로 말한다(사이 0.5초). 끝나면 [onDone]. */
    fun speak(lines: List<Line>, louder: Boolean, onDone: () -> Unit)
    /** 0.25초 안에 멈춘다. 멈춘 발화의 onDone은 부르지 않는다. */
    fun stop()
    fun release() {}
}

/** 듣기 결과. 인식한 문장은 판정에만 쓰고 저장하지 않는다. */
sealed interface ListenOutcome {
    data class Heard(val text: String, val latencyMs: Long) : ListenOutcome
    data object NoSpeech : ListenOutcome
    data object Failed : ListenOutcome
}

/** 음성 인식. 순서: 시연 주입 큐 → Pleos STT → 안드로이드 SpeechRecognizer (단계 4). */
interface Listener {
    /** [speechEndedAtMs]: 질문 음성이 끝난 단조 시각. 응답 시간의 기준점이다. */
    fun listen(windowMs: Long, speechEndedAtMs: Long, onResult: (ListenOutcome) -> Unit)
    fun stop()
    fun release() {}
}

/** 알림음 (단계 4에서 소리 파일로). */
interface SoundPlayer {
    fun play(sound: Sound, onDone: () -> Unit)
    fun stop()
    fun release() {}
}

/**
 * 임시 음성 출력: 소리 없이 로그만 남기고 글자 수로 길이를 흉내 낸다. 단계 4에서 진짜 TTS로 바꾼다.
 */
class LogSpeaker : Speaker {
    private val main = Handler(Looper.getMainLooper())
    private var done: Runnable? = null

    override fun speak(lines: List<Line>, louder: Boolean, onDone: () -> Unit) {
        stop()
        lines.forEach { Log.i(TAG, "tts vid=${it.id} louder=$louder text=${it.text}") }
        val ms = lines.sumOf { CHAR_MS * it.text.length + BASE_MS } + GAP_MS * (lines.size - 1)
        val r = Runnable {
            done = null
            onDone()
        }
        done = r
        main.postDelayed(r, ms)
    }

    override fun stop() {
        done?.let { main.removeCallbacks(it) }
        done = null
    }

    private companion object {
        const val CHAR_MS = 90L
        const val BASE_MS = 300L
        const val GAP_MS = 500L
    }
}

/** 임시 알림음: 로그만 남긴다. */
class LogSoundPlayer : SoundPlayer {
    private val main = Handler(Looper.getMainLooper())
    private var done: Runnable? = null

    override fun play(sound: Sound, onDone: () -> Unit) {
        stop()
        Log.i(TAG, "sound ${sound.javaClass.simpleName.lowercase()} level=${sound.level}")
        val r = Runnable {
            done = null
            onDone()
        }
        done = r
        main.postDelayed(r, 1_200)
    }

    override fun stop() {
        done?.let { main.removeCallbacks(it) }
        done = null
    }
}
