package com.wakeon.app.voice

import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.wakeon.app.TAG
import java.util.Locale
import ai.pleos.playground.stt.SpeechToText as PleosStt

/** 음성 인식 엔진 하나. 엔진을 쓸 수 없게 되면 [onEngineError]로 알리고 [FallbackListener]가 다음 엔진을 쓴다. */
interface RecognitionEngine {
    val name: String
    val ready: Boolean
    fun listen(windowMs: Long, speechEndedAtMs: Long, onResult: (ListenOutcome) -> Unit, onEngineError: (String) -> Unit)
    fun stop()
    fun release() {}
}

/**
 * 듣기 창 규칙(SPEC 7-4): 말소리가 없으면 [windowMs] 뒤 무응답, 말이 시작되면 끝점까지 최대 [maxAfterSpeechMs].
 * 응답 시간 = 첫 발화 시작 − 질문 음성 종료(같은 단조 시계).
 * "소리 없음"은 무응답, "말했는데 못 알아들음"만 인식 실패로 본다(T10). 엔진·권한이 없으면 무응답과 같다.
 * 인식한 문장은 판정에만 쓰고 저장하지 않는다. 음성 원본도 저장하지 않는다.
 */
class FallbackListener(
    private val context: Context,
    private val engines: List<RecognitionEngine>,
) : Listener {
    private val main = Handler(Looper.getMainLooper())
    private var job = 0L
    private var idle: Runnable? = null

    override fun listen(windowMs: Long, speechEndedAtMs: Long, onResult: (ListenOutcome) -> Unit) {
        stop()
        val myJob = ++job
        val deliver: (ListenOutcome) -> Unit = { outcome ->
            main.post {
                if (myJob == job) {
                    job++
                    val kind = when (outcome) {
                        is ListenOutcome.Heard -> "heard latency_ms=${outcome.latencyMs}"
                        ListenOutcome.NoSpeech -> "no_speech"
                        ListenOutcome.Failed -> "failed"
                    }
                    Log.i(TAG, "listen_result $kind")
                    onResult(outcome)
                }
            }
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "listen mic_permission_missing → 듣기 창만큼 기다린 뒤 무응답")
            return waitSilently(windowMs) { deliver(ListenOutcome.NoSpeech) }
        }

        fun attempt(index: Int) {
            val engine = engines.drop(index).firstOrNull { it.ready }
            if (engine == null) {
                Log.w(TAG, "listen no_engine → 무응답")
                return waitSilently(windowMs) { deliver(ListenOutcome.NoSpeech) }
            }
            val nextIndex = engines.indexOf(engine) + 1
            Log.i(TAG, "listen_start engine=${engine.name} window_ms=$windowMs")
            engine.listen(windowMs, speechEndedAtMs, deliver) { reason ->
                main.post {
                    if (myJob != job) return@post
                    Log.w(TAG, "listen engine_error engine=${engine.name} reason=$reason → 다음 엔진")
                    attempt(nextIndex)
                }
            }
        }
        attempt(0)
    }

    private fun waitSilently(ms: Long, then: () -> Unit) {
        val r = Runnable { idle = null; then() }
        idle = r
        main.postDelayed(r, ms)
    }

    override fun stop() {
        job++
        idle?.let { main.removeCallbacks(it) }
        idle = null
        engines.forEach { it.stop() }
    }

    override fun release() = engines.forEach { it.release() }

    companion object {
        /** 16kHz · 16bit · 모노 PCM: 1ms = 32바이트 (Pleos EPD 위치 환산) */
        const val PCM_BYTES_PER_MS = 32L
    }
}

/** 한 번의 듣기에서 시각과 결과를 정리하는 도우미. */
private class ListenSession(
    private val main: Handler,
    private val windowMs: Long,
    private val maxAfterSpeechMs: Long,
    private val speechEndedAtMs: Long,
    private val onResult: (ListenOutcome) -> Unit,
    private val onClose: () -> Unit,
) {
    var speechStartAtMs: Long? = null
        private set
    var lastText: String? = null
    private var done = false

    private val noSpeech = Runnable { if (speechStartAtMs == null) finish(ListenOutcome.NoSpeech) }
    private val tooLong = Runnable { finish(lastText?.let { heard(it) } ?: ListenOutcome.Failed) }

    fun start() = main.postDelayed(noSpeech, windowMs)

    fun speechStarted(atMs: Long) {
        if (speechStartAtMs != null) return
        speechStartAtMs = atMs
        main.removeCallbacks(noSpeech)
        main.postDelayed(tooLong, maxAfterSpeechMs)
    }

    fun heard(text: String) = ListenOutcome.Heard(text, ((speechStartAtMs ?: SystemClock.elapsedRealtime()) - speechEndedAtMs).coerceAtLeast(0))

    /** 엔진이 끝났다고 알릴 때: 들은 말이 있으면 그것, 말소리는 있었는데 글자가 없으면 실패, 아니면 무응답. */
    fun ended() = finish(lastText?.let { heard(it) } ?: if (speechStartAtMs != null) ListenOutcome.Failed else ListenOutcome.NoSpeech)

    fun finish(outcome: ListenOutcome) {
        if (done) return
        done = true
        main.removeCallbacks(noSpeech)
        main.removeCallbacks(tooLong)
        onClose()
        onResult(outcome)
    }

    fun cancel() {
        done = true
        main.removeCallbacks(noSpeech)
        main.removeCallbacks(tooLong)
    }
}

/** Pleos SpeechToText SDK, 기기 안 엔진(ON_DEVICE). */
class PleosRecognitionEngine(context: Context, private val maxAfterSpeechMs: Long) : RecognitionEngine {
    override val name = "pleos"
    private val main = Handler(Looper.getMainLooper())
    private val stt = PleosStt(context.applicationContext, Mode.ON_DEVICE)

    @Volatile
    override var ready = false
        private set

    private var session: ListenSession? = null
    private var engineError: ((String) -> Unit)? = null
    private var recognitionStartAtMs = 0L

    private val results = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() {
            val at = SystemClock.elapsedRealtime()
            main.post { recognitionStartAtMs = at }
        }

        override fun onUpdatedEpdData(on: Long, off: Long) {
            main.post {
                if (on >= 0) session?.speechStarted(recognitionStartAtMs + on / FallbackListener.PCM_BYTES_PER_MS)
            }
        }

        override fun onUpdated(stt: String, completed: Boolean) {
            val at = SystemClock.elapsedRealtime()
            main.post {
                val s = session ?: return@post
                if (stt.isNotBlank()) {
                    s.speechStarted(at)
                    s.lastText = stt
                }
                if (completed) s.finish(if (stt.isNotBlank()) s.heard(stt) else ListenOutcome.Failed)
            }
        }

        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() {
            main.post { session?.ended() }
        }

        override fun onError(errorType: ErrorType) {
            main.post {
                val s = session ?: return@post
                when (errorType) {
                    ErrorType.PERMISSION_DENIED, ErrorType.NOT_SUPPORTED_API, ErrorType.ON_DEVICE_ENGINE_ERROR,
                    ErrorType.UNAVAILABLE_LANGUAGE, ErrorType.MIC_ERROR, ErrorType.INVALID_REQUEST_ERROR,
                    -> {
                        s.cancel()
                        session = null
                        ready = false
                        engineError?.invoke(errorType.name)
                    }
                    else -> s.ended()
                }
            }
        }
    }

    init {
        try {
            stt.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    stt.addListener(results)
                    stt.setLocale(Locale.KOREA)
                    ready = true
                    Log.i(TAG, "stt engine=pleos ready")
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "stt engine=pleos init_failed $errorType")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "stt engine=pleos init_exception ${e.javaClass.simpleName} ${e.message}")
        }
    }

    override fun listen(windowMs: Long, speechEndedAtMs: Long, onResult: (ListenOutcome) -> Unit, onEngineError: (String) -> Unit) {
        engineError = onEngineError
        recognitionStartAtMs = SystemClock.elapsedRealtime()
        val s = ListenSession(main, windowMs, maxAfterSpeechMs, speechEndedAtMs, onResult) { stopEngine() }
        session = s
        s.start()
        try {
            stt.request()
        } catch (e: Exception) {
            s.cancel()
            session = null
            ready = false
            onEngineError("exception_${e.javaClass.simpleName}")
        }
    }

    private fun stopEngine() {
        session = null
        try {
            stt.stop()
        } catch (_: Exception) {
        }
    }

    override fun stop() {
        val s = session ?: return
        s.cancel()
        stopEngine()
    }

    override fun release() {
        try {
            stt.removeListener(results)
            stt.release()
        } catch (_: Exception) {
        }
    }
}

/** 안드로이드 기본 SpeechRecognizer(ko-KR, 오프라인 우선). */
class AndroidRecognitionEngine(private val context: Context, private val maxAfterSpeechMs: Long) : RecognitionEngine {
    override val name = "android"
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var session: ListenSession? = null

    override val ready: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun listen(windowMs: Long, speechEndedAtMs: Long, onResult: (ListenOutcome) -> Unit, onEngineError: (String) -> Unit) {
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context.applicationContext).also { recognizer = it }
        val s = ListenSession(main, windowMs, maxAfterSpeechMs, speechEndedAtMs, onResult) { r.cancel() }
        session = s
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = s.speechStarted(SystemClock.elapsedRealtime())
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                best(partialResults)?.let { s.lastText = it }
            }

            override fun onResults(results: Bundle?) {
                val text = best(results)
                s.finish(text?.let { s.heard(it) } ?: if (s.speechStartAtMs != null) ListenOutcome.Failed else ListenOutcome.NoSpeech)
            }

            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> s.ended()
                    SpeechRecognizer.ERROR_CLIENT -> Unit // 우리가 cancel()한 경우
                    else -> {
                        s.cancel()
                        session = null
                        onEngineError("error_$error")
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        s.start()
        r.startListening(intent)
    }

    private fun best(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    override fun stop() {
        val s = session ?: return
        s.cancel()
        session = null
        recognizer?.cancel()
    }

    override fun release() {
        recognizer?.destroy()
        recognizer = null
    }
}
