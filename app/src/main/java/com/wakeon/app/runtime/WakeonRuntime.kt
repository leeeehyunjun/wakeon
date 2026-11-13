package com.wakeon.app.runtime

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.wakeon.app.TAG
import com.wakeon.app.config.WakeonConfig
import com.wakeon.app.config.WakeonConfig.Companion.MINUTE_MS
import com.wakeon.app.config.WakeonConfig.Companion.SECOND_MS
import com.wakeon.app.core.Effects
import com.wakeon.app.core.MonoClock
import com.wakeon.app.core.Sound
import com.wakeon.app.core.TriggerId
import com.wakeon.app.core.WakeonSnapshot
import com.wakeon.app.core.WakeonStateMachine
import com.wakeon.app.voice.UserIntent
import com.wakeon.app.core.WallClock
import com.wakeon.app.demo.DemoAnswers
import com.wakeon.app.demo.DemoFirstListener
import com.wakeon.app.nav.DemoRouteLoader
import com.wakeon.app.nav.NaviGateway
import com.wakeon.app.nav.PleosNaviGateway
import com.wakeon.app.nav.RestArea
import com.wakeon.app.nav.RestAreaType
import com.wakeon.app.vehicle.DemoVehicleSource
import com.wakeon.app.vehicle.Gear
import com.wakeon.app.vehicle.MergedVehicleSource
import com.wakeon.app.vehicle.PleosVehicleSource
import com.wakeon.app.vehicle.TurnSignal
import com.wakeon.app.voice.AndroidRecognitionEngine
import com.wakeon.app.voice.AndroidSpeechEngine
import com.wakeon.app.voice.AssetSoundPlayer
import com.wakeon.app.voice.AudioCoordinator
import com.wakeon.app.voice.FallbackListener
import com.wakeon.app.voice.FallbackSpeaker
import com.wakeon.app.voice.Line
import com.wakeon.app.voice.ListenOutcome
import com.wakeon.app.voice.Listener
import com.wakeon.app.voice.PleosRecognitionEngine
import com.wakeon.app.voice.PleosSpeechEngine
import com.wakeon.app.voice.SoundPlayer
import com.wakeon.app.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * 앱 하나에 하나. 차량 신호·시연 신호·음성·내비를 상태 머신에 잇는다.
 * 상태 머신은 메인 스레드에서만 부른다.
 */
class WakeonRuntime(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val config = WakeonConfig()
    private val clock = MonoClock { SystemClock.elapsedRealtime() }
    private val prefs = app.getSharedPreferences("wakeon", Context.MODE_PRIVATE)

    /** 시연 신호가 한 번이라도 들어오면 그 세션은 시연 모드(로그에 demo=true, 화면 표시 없음). */
    var demoMode = false
        private set

    private val audio = AudioCoordinator(app)
    private val demoVehicle = DemoVehicleSource()
    val realVehicle = PleosVehicleSource(app)
    private val vehicle = MergedVehicleSource(demoVehicle, realVehicle, audio.navVoice)
    private val answers = DemoAnswers()
    private val wallClock = DemoWallClock(clock)
    private val eventLog = LogcatEventLog { demoMode }
    private val route = runCatching { DemoRouteLoader.load(app) }
        .onFailure { Log.w(TAG, "route load_failed ${it.message}") }
        .getOrNull()

    private lateinit var machine: WakeonStateMachine
    private val navi: NaviGateway = PleosNaviGateway(app, config.waypointTimeoutS * SECOND_MS) {
        machine.onArriveSignal()
    }

    // 음성: Pleos(기기 안 엔진) → 안드로이드 기본. 듣기는 시연 대답 큐가 먼저(BUILD 단계 4).
    private val speaker: Speaker = FallbackSpeaker(
        listOf(PleosSpeechEngine(app, audio), AndroidSpeechEngine(app, audio)),
        audio,
    )
    private val listener: Listener = DemoFirstListener(
        answers,
        fallback = FallbackListener(
            app,
            listOf(
                PleosRecognitionEngine(app, config.talkListenMaxS * SECOND_MS),
                AndroidRecognitionEngine(app, config.talkListenMaxS * SECOND_MS),
            ),
        ),
        demoMode = { demoMode },
    )
    private val sounds: SoundPlayer = AssetSoundPlayer(app, audio, config.warnLevel3RaiseSteps, config.volumeStepDb)
    private var lastSpeechEndMs = 0L

    private val effects = object : Effects {
        override fun speak(token: Long, lines: List<Line>, louder: Boolean) =
            speaker.speak(lines, louder) { endedAt ->
                main.post {
                    lastSpeechEndMs = endedAt
                    machine.onSpeechDone(token)
                }
            }

        override fun stopSpeaking() = speaker.stop()

        override fun listen(token: Long, windowMs: Long) =
            listener.listen(windowMs, lastSpeechEndMs) { outcome ->
                main.post {
                    when (outcome) {
                        is ListenOutcome.Heard -> machine.onHeard(token, outcome.text, outcome.latencyMs)
                        ListenOutcome.NoSpeech -> machine.onNoAnswer(token)
                        ListenOutcome.Failed -> machine.onRecognitionFailed(token)
                    }
                }
            }

        override fun stopListening() = listener.stop()

        override fun playSound(token: Long, sound: Sound) =
            sounds.play(sound) { main.post { machine.onSoundDone(token) } }

        override fun stopSound() = sounds.stop()

        override fun addWaypoint(token: Long, rest: RestArea) =
            navi.addWaypoint(rest) { result ->
                main.post {
                    // 시연 모드에서 내비 추가가 안 되면 앱 안 표시(W-10)로 이어 간다(BUILD 단계 3-3, README에 밝힘).
                    val ok = result.ok || demoMode
                    val navResult = when {
                        result.ok -> "ok"
                        demoMode -> "demo_fallback"
                        else -> "failed"
                    }
                    Log.i(TAG, "navi_result result=$navResult reason=${result.reason}${if (demoMode) " demo=true" else ""}")
                    machine.onWaypointResult(token, ok)
                }
            }
    }

    val snapshot: StateFlow<WakeonSnapshot> get() = machine.snapshot

    fun start() {
        machine = WakeonStateMachine(
            config = config,
            clock = clock,
            wallClock = wallClock,
            effects = effects,
            eventLog = eventLog,
            route = route,
            consented = prefs.getBoolean(KEY_CONSENT, false),
        )
        realVehicle.start()
        vehicle.sources.forEach { flow -> scope.launch { flow.collect { pushVehicle() } } }
        scope.launch {
            while (isActive) {
                machine.tick()
                delay(TICK_MS)
            }
        }
    }

    /** 동의(W-01 "동의하고 시작"). */
    fun consent() {
        prefs.edit().putBoolean(KEY_CONSENT, true).apply()
        machine.onConsent()
    }

    // ── 화면 버튼 (같은 일을 음성으로도 할 수 있다)

    fun startPrecheck() = ui("start_precheck") { machine.onStartPrecheck() }

    fun skipPrecheck() = ui("skip_precheck") { machine.onSkipPrecheck() }

    fun tap(intent: UserIntent) = ui("tap intent=$intent") { machine.onTap(intent) }

    fun setTrigger(id: TriggerId, on: Boolean) = ui("trigger $id=$on") { machine.setTriggerEnabled(id, on) }

    private inline fun ui(what: String, block: () -> Unit) {
        Log.i(TAG, "ui $what")
        block()
    }

    private fun pushVehicle() {
        if (!vehicle.refresh()) return
        val s = vehicle.signals.value
        val r = realVehicle.current()
        Log.i(
            TAG,
            "vehicle gear=${s.gear} speed=${s.speedKmh} brake=${s.brake} turn=${s.turn} navVoice=${s.navVoice} " +
                "real_gear=${r.gear} real_speed=${r.speedKmh} real_brake=${r.brake} real_turn=${r.turn}",
        )
        machine.onVehicle(s)
    }

    // ════════════════════════════════════════ 시연 신호

    /**
     * `adb shell am broadcast -a com.wakeon.app.DEMO -p com.wakeon.app …` (DEMO_SCENARIO 3장).
     * 한 번에 여러 값을 보내면 초기화 → 설정 → 차량 값 → 주행 시간·위치·도착 → 대답 순으로 처리한다.
     */
    fun handleDemo(extras: Bundle) {
        demoMode = true
        val e = Extras(extras)
        Log.i(TAG, "demo ${e.describe()}")

        if (e.bool("reset") == true) {
            answers.clear()
            wallClock.clear()
            machine.demoReset()
        }
        e.float("timeScale")?.let { machine.demoTimeScale(it) }
        e.int("restSec")?.let { machine.demoRestSeconds(it) }
        e.string("clock")?.let { wallClock.set(it) }
        // 트리거 토글: 시안 W-02처럼 C3(70km/h 10분)를 끈 상태로 시연할 때 --ez c3 false
        e.bool("c2")?.let { machine.setTriggerEnabled(TriggerId.C2, it) }
        e.bool("c3")?.let { machine.setTriggerEnabled(TriggerId.C3, it) }

        e.string("gear")?.let { g -> parseGear(g)?.let { demoVehicle.gear.value = it } }
        e.float("speed")?.let { demoVehicle.speedKmh.value = it }
        e.bool("brake")?.let { demoVehicle.brake.value = it }
        e.string("turn")?.let { t -> parseTurn(t)?.let { demoVehicle.turnSignal.value = it } }
        e.bool("navVoice")?.let { demoVehicle.navVoice.value = it }
        pushVehicle()

        e.int("driveMin")?.let { machine.demoDriveMinutes(it) }
        e.float("routeKm")?.let { machine.onRoutePosition(it.toDouble()) }
        if (e.bool("arrive") == true) machine.onArriveSignal()

        e.string("answer")?.let { text ->
            answers.add(text, e.int("latencyMs")?.toLong() ?: DemoAnswers.DEFAULT_LATENCY_MS)
        }

        // 진단용: 내비에 경유지를 넣어 보고 결과만 로그로 남긴다(Pleos 문서 예시 좌표).
        if (e.bool("naviProbe") == true) {
            val probe = RestArea("소프트웨어드림센터", RestAreaType.DROWSY_SHELTER, 0.0, "오른쪽", 37.41275833, 127.09481944)
            navi.addWaypoint(probe) { Log.i(TAG, "navi_probe ok=${it.ok} reason=${it.reason}") }
        }
    }

    private fun parseGear(s: String) = when (s.trim().uppercase()) {
        "P" -> Gear.P
        "R" -> Gear.R
        "N" -> Gear.N
        "D" -> Gear.D
        else -> null
    }

    private fun parseTurn(s: String) = when (s.trim().uppercase()) {
        "L", "LEFT" -> TurnSignal.LEFT
        "R", "RIGHT" -> TurnSignal.RIGHT
        "OFF" -> TurnSignal.OFF
        else -> null
    }

    /** am broadcast의 --es/--ei/--ef/--ez 어느 것으로 보내도 읽는다. */
    private class Extras(private val b: Bundle) {
        @Suppress("DEPRECATION")
        private fun raw(key: String): Any? = b.get(key)

        fun string(key: String) = raw(key)?.toString()
        fun int(key: String): Int? = when (val v = raw(key)) {
            is Number -> v.toInt()
            is String -> v.trim().toDoubleOrNull()?.toInt()
            else -> null
        }
        fun float(key: String): Float? = when (val v = raw(key)) {
            is Number -> v.toFloat()
            is String -> v.trim().toFloatOrNull()
            else -> null
        }
        fun bool(key: String): Boolean? = when (val v = raw(key)) {
            is Boolean -> v
            is String -> v.trim().lowercase().let { if (it == "true") true else if (it == "false") false else null }
            is Number -> v.toInt() != 0
            else -> null
        }

        /** 로그용. 대답 문장은 시연 대본이라 남겨도 된다(실제 음성 인식 결과는 남기지 않는다). */
        fun describe() = b.keySet().sorted().joinToString(" ") { "$it=${raw(it)}" }
    }

    companion object {
        private const val TICK_MS = 200L
        private const val KEY_CONSENT = "consent"
    }
}

/** 하루 중 분. 시연 `clock`("HH:mm")이 들어오면 그 시각부터 흘러간다. */
private class DemoWallClock(private val clock: MonoClock) : WallClock {
    private var baseMinute: Int? = null
    private var setAtMs = 0L

    fun set(hhmm: String) {
        val parts = hhmm.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        baseMinute = (h * 60 + m) % MINUTES_PER_DAY
        setAtMs = clock.nowMs()
    }

    fun clear() {
        baseMinute = null
    }

    override fun minuteOfDay(): Int {
        val base = baseMinute ?: return LocalTime.now().let { it.hour * 60 + it.minute }
        return ((base + (clock.nowMs() - setAtMs) / MINUTE_MS) % MINUTES_PER_DAY).toInt()
    }

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
