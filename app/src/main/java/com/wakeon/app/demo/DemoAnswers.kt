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
 */
class DemoFirstListener(
    private val answers: DemoAnswers,
    private val fallback: Listener?,
) : Listener {
    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    override fun listen(windowMs: Long, speechEndedAtMs: Long, onResult: (ListenOutcome) -> Unit) {
        stop()
        val answer = answers.poll()
        if (answer == null) {
            if (fallback != null) return fallback.listen(windowMs, speechEndedAtMs, onResult)
            return schedule(windowMs) { onResult(ListenOutcome.NoSpeech) }
        }
        Log.i(TAG, "listen demo_answer latency_ms=${answer.latencyMs} left=${answers.size()}")
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
