package com.wakeon.app.demo

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.wakeon.app.TAG
import com.wakeon.app.voice.ListenOutcome
import com.wakeon.app.voice.Listener

/** 시연 대답 큐. 질문 하나에 하나씩, 넣은 순서대로 쓴다(DEMO_SCENARIO 3장). */
class DemoAnswers {
    data class Answer(val text: String, val latencyMs: Long)

    private val queue = ArrayDeque<Answer>()

    @Synchronized
    fun add(text: String, latencyMs: Long) {
        queue.addLast(Answer(text, latencyMs))
    }

    @Synchronized
    fun poll(): Answer? = queue.removeFirstOrNull()

    @Synchronized
    fun clear() = queue.clear()

    @Synchronized
    fun size() = queue.size

    companion object {
        const val NONE = "__none__"
        const val DEFAULT_LATENCY_MS = 1_000L
    }
}

/**
 * 듣기: 시연 대답 큐가 있으면 그것을 쓰고, 없으면 [fallback](실제 음성 인식)에 넘긴다.
 * 시연 대답은 질문 음성이 끝나고 `latencyMs` 뒤에 들은 것으로 처리한다.
 *
 * 시연 모드([demoMode])에서는 마이크와 STT를 절대 열지 않는다(2026-11-13 결정). 에뮬레이터에서 마이크가 열리면
 * 오디오가 입출력 동시 모드가 되어 출력 소리에 잡음이 낀다. 큐가 비었거나 `__none__`이면 마이크 없이 듣기 창만큼
 * 기다린 뒤 무응답으로 처리한다(STT 시작음도 나지 않는다). 실제 모드는 그대로 실제 음성 인식을 쓴다.
 */
class DemoFirstListener(
    private val answers: DemoAnswers,
    private val fallback: Listener?,
    private val demoMode: () -> Boolean = { false },
) : Listener {
    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    override fun listen(windowMs: Long, speechEndedAtMs: Long, onResult: (ListenOutcome) -> Unit) {
        stop()
        val answer = answers.poll()
        if (answer == null) {
            if (demoMode()) {
                Log.i(TAG, "listen demo_no_answer mic=off window_ms=$windowMs")
                return schedule(windowMs) { onResult(ListenOutcome.NoSpeech) }
            }
            if (fallback != null) return fallback.listen(windowMs, speechEndedAtMs, onResult)
            return schedule(windowMs) { onResult(ListenOutcome.NoSpeech) }
        }
        Log.i(TAG, "listen demo_answer latency_ms=${answer.latencyMs} left=${answers.size()} mic=off")
        if (answer.text == DemoAnswers.NONE || answer.latencyMs >= windowMs) {
            schedule(windowMs) { onResult(ListenOutcome.NoSpeech) }
        } else {
            schedule(answer.latencyMs) { onResult(ListenOutcome.Heard(answer.text, answer.latencyMs)) }
        }
    }

    private fun schedule(delayMs: Long, action: () -> Unit) {
        val r = Runnable {
            pending = null
            action()
        }
        pending = r
        main.postDelayed(r, delayMs)
    }

    override fun stop() {
        pending?.let { main.removeCallbacks(it) }
        pending = null
        fallback?.stop()
    }
}
