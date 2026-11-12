package com.wakeon.app.voice

import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.wakeon.app.TAG
import java.util.Locale
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import android.speech.tts.TextToSpeech as AndroidTts

/** TTS 엔진 하나. 준비가 안 됐거나 오류가 나면 [FallbackSpeaker]가 다음 엔진으로 넘긴다. */
interface SpeechEngine {
    val name: String
    val ready: Boolean
    /** 문장을 차례로 말한다(사이 0.5초). 끝나면 [onDone](끝난 단조 시각), 엔진 오류면 [onError]. */
    fun speak(lines: List<Line>, louder: Boolean, onDone: (endedAtMs: Long) -> Unit, onError: (String) -> Unit)
    fun stop()
    fun release() {}
}

/**
 * 음성 출력: Pleos TTS(ON_DEVICE) → 안드로이드 TTS (BUILD 단계 4-1).
 * 앞 엔진이 준비되지 않았거나 오류가 나면 그 발화부터 다음 엔진으로 말한다.
 */
class FallbackSpeaker(
    private val engines: List<SpeechEngine>,
    private val audio: AudioCoordinator,
) : Speaker {
    private val main = Handler(Looper.getMainLooper())
    private var job = 0L
    private var holdingFocus = false

    override fun speak(lines: List<Line>, louder: Boolean, onDone: (endedAtMs: Long) -> Unit) {
        stop()
        val myJob = ++job
        val startedAt = SystemClock.elapsedRealtime()
        audio.acquire()
        holdingFocus = true

        fun attempt(index: Int) {
            val engine = engines.drop(index).firstOrNull { it.ready }
            if (engine == null) {
                Log.w(TAG, "tts no_engine vid=${lines.firstOrNull()?.id}")
                // 엔진이 하나도 없으면 말한 것으로 치고 흐름을 이어 간다(E-7은 화면에서 알린다).
                return finish(myJob) { onDone(SystemClock.elapsedRealtime()) }
            }
            val nextIndex = engines.indexOf(engine) + 1
            lines.forEach { Log.i(TAG, "tts_start vid=${it.id} engine=${engine.name} louder=$louder text=${it.text}") }
            engine.speak(
                lines,
                louder,
                onDone = { endedAt ->
                    main.post {
                        if (myJob != job) return@post
                        Log.i(TAG, "tts_spoken vid=${lines.joinToString(",") { it.id }} ms=${endedAt - startedAt} engine=${engine.name}")
                        finish(myJob) { onDone(endedAt) }
                    }
                },
                onError = { reason ->
                    main.post {
                        if (myJob != job) return@post
                        Log.w(TAG, "tts_error engine=${engine.name} reason=$reason → 다음 엔진")
                        attempt(nextIndex)
                    }
                },
            )
        }
        attempt(0)
    }

    private fun finish(myJob: Long, then: () -> Unit) {
        if (myJob != job) return
        releaseFocus()
        then()
    }

    override fun stop() {
        job++
        if (!holdingFocus) return // 말하는 중이 아님
        val at = SystemClock.elapsedRealtime()
        engines.forEach { it.stop() }
        releaseFocus()
        Log.i(TAG, "tts_stop took_ms=${SystemClock.elapsedRealtime() - at}")
    }

    private fun releaseFocus() {
        if (!holdingFocus) return
        holdingFocus = false
        audio.release()
    }

    override fun release() = engines.forEach { it.release() }
}

/** Pleos TextToSpeech SDK, 기기 안 엔진(ON_DEVICE: 네트워크·앱 등록 불필요). */
class PleosSpeechEngine(context: Context, audio: AudioCoordinator) : SpeechEngine {
    override val name = "pleos"
    private val main = Handler(Looper.getMainLooper())
    private val tts = PleosTts(context.applicationContext, Mode.ON_DEVICE)

    @Volatile
    override var ready = false
        private set

    private var queue: List<Line> = emptyList()
    private var index = 0
    private var onDone: ((Long) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private val watchdog = Runnable { fail("timeout") }

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() {
            main.post { main.removeCallbacks(watchdog); main.postDelayed(watchdog, partLimitMs()) }
        }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() {
            val at = SystemClock.elapsedRealtime()
            main.post { next(at) }
        }
        override fun onStop() = Unit
        override fun onError(errorType: ErrorType) {
            main.post { fail(errorType.name) }
        }
    }

    init {
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.addEventListener(events)
                    tts.setLocale(Locale.KOREA)
                    tts.setAudioAttributes(audio.speechAttributes)
                    ready = true
                    Log.i(TAG, "tts engine=pleos ready")
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "tts engine=pleos init_failed $errorType")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "tts engine=pleos init_exception ${e.javaClass.simpleName} ${e.message}")
        }
    }

    override fun speak(lines: List<Line>, louder: Boolean, onDone: (Long) -> Unit, onError: (String) -> Unit) {
        // Pleos TTS에는 음량 지정이 없다. "조금 더 크게"(V-14)는 안드로이드 TTS에서만 반영된다.
        queue = lines
        index = 0
        this.onDone = onDone
        this.onError = onError
        speakCurrent()
    }

    private fun speakCurrent() {
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, partLimitMs() + START_LIMIT_MS)
        try {
            tts.speak(queue[index].text, true)
        } catch (e: Exception) {
            fail("exception_${e.javaClass.simpleName}")
        }
    }

    private fun next(endedAt: Long) {
        if (onDone == null) return
        index++
        if (index < queue.size) {
            main.removeCallbacks(watchdog)
            main.postDelayed({ if (onDone != null) speakCurrent() }, GAP_MS)
            return
        }
        main.removeCallbacks(watchdog)
        val done = onDone
        clear()
        done?.invoke(endedAt)
    }

    private fun fail(reason: String) {
        val err = onError ?: return
        main.removeCallbacks(watchdog)
        clear()
        ready = false
        err(reason)
    }

    private fun clear() {
        onDone = null
        onError = null
    }

    private fun partLimitMs() = (queue.getOrNull(index)?.text?.length ?: 20) * 250L + 3_000L

    override fun stop() {
        if (onDone == null) return
        clear()
        main.removeCallbacks(watchdog)
        try {
            tts.stop()
        } catch (_: Exception) {
        }
    }

    override fun release() {
        try {
            tts.removeEventListener(events)
            tts.release()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val GAP_MS = 500L
        const val START_LIMIT_MS = 4_000L
    }
}

/** 안드로이드 기본 TextToSpeech(한국어). 에뮬레이터에서는 Pleos 음성 서비스가 엔진으로 등록돼 있다. */
class AndroidSpeechEngine(context: Context, audio: AudioCoordinator) : SpeechEngine {
    override val name = "android"
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    override var ready = false
        private set

    private var job = 0L
    private var lastId: String? = null
    private var onDone: ((Long) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    private val tts: AndroidTts = AndroidTts(context.applicationContext) { status ->
        main.post { onInit(status, audio) }
    }

    private fun onInit(status: Int, audio: AudioCoordinator) {
        if (status != AndroidTts.SUCCESS) {
            Log.w(TAG, "tts engine=android init_failed status=$status")
            return
        }
        val lang = tts.setLanguage(Locale.KOREAN)
        tts.setAudioAttributes(audio.speechAttributes)
        tts.setOnUtteranceProgressListener(progress)
        ready = lang != AndroidTts.LANG_MISSING_DATA && lang != AndroidTts.LANG_NOT_SUPPORTED
        Log.i(TAG, "tts engine=android ready=$ready lang=$lang default=${tts.defaultEngine}")
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) {
            val at = SystemClock.elapsedRealtime()
            main.post {
                if (utteranceId != lastId) return@post
                val done = onDone
                clear()
                done?.invoke(at)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = onError(utteranceId, -1)

        override fun onError(utteranceId: String?, errorCode: Int) {
            main.post {
                if (utteranceId?.startsWith("wk-$job-") != true) return@post
                val err = onError
                clear()
                err?.invoke("error_$errorCode")
            }
        }
    }

    override fun speak(lines: List<Line>, louder: Boolean, onDone: (Long) -> Unit, onError: (String) -> Unit) {
        val my = ++job
        this.onDone = onDone
        this.onError = onError
        val params = Bundle().apply { putFloat(AndroidTts.Engine.KEY_PARAM_VOLUME, if (louder) LOUD else NORMAL) }
        lines.forEachIndexed { i, line ->
            val id = "wk-$my-$i"
            if (i > 0) tts.playSilentUtterance(GAP_MS, AndroidTts.QUEUE_ADD, "wk-$my-gap$i")
            val mode = if (i == 0) AndroidTts.QUEUE_FLUSH else AndroidTts.QUEUE_ADD
            if (tts.speak(line.text, mode, params, id) != AndroidTts.SUCCESS) {
                clear()
                return onError("speak_failed")
            }
            lastId = id
        }
    }

    private fun clear() {
        onDone = null
        onError = null
    }

    override fun stop() {
        if (onDone == null) return
        clear()
        job++
        tts.stop()
    }

    override fun release() = tts.shutdown()

    private companion object {
        const val GAP_MS = 500L
        const val NORMAL = 0.8f
        const val LOUD = 1.0f
    }
}
