package com.wakeon.app.voice

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.wakeon.app.TAG
import com.wakeon.app.core.Sound

/** 음성 출력. 순서: Pleos TTS → 안드로이드 TTS ([FallbackSpeaker]). */
interface Speaker {
    /** [lines]를 차례로 말한다(사이 0.5초). 끝나면 [onDone](말이 끝난 단조 시각, 응답 시간의 기준점). */
    fun speak(lines: List<Line>, louder: Boolean, onDone: (endedAtMs: Long) -> Unit)
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

/** 음성 인식. 순서: 시연 주입 큐 → Pleos STT → 안드로이드 SpeechRecognizer. */
interface Listener {
    /** [speechEndedAtMs]: 질문 음성이 끝난 단조 시각. 응답 시간의 기준점이다. */
    fun listen(windowMs: Long, speechEndedAtMs: Long, onResult: (ListenOutcome) -> Unit)
    fun stop()
    fun release() {}
}

/** 알림음. */
interface SoundPlayer {
    fun play(sound: Sound, onDone: () -> Unit)
    fun stop()
    fun release() {}
}

/**
 * 경고음·기상 알람: `assets/sounds/warn_1~3.wav`, `alarm_1~3.wav` (tools/make_sounds.py로 만듦, 48kHz·16bit).
 * 앱 자체 알람 용도로 재생하고, 단계마다 소리 파일 자체가 커진다(처음부터 최대 음량 금지).
 * 3단계 경고음 동안만 [warnLevel3RaiseSteps]칸 올린다: 앱이 스트림 음량을 바꿀 수 없어서
 * 1·2단계를 그만큼 낮춘 크기로 틀고 3단계는 원래 크기로 튼다.
 */
class AssetSoundPlayer(
    private val context: Context,
    private val audio: AudioCoordinator,
    warnLevel3RaiseSteps: Int,
    volumeStepDb: Double,
) : SoundPlayer {
    private val lowerWarnGain = Math.pow(10.0, -warnLevel3RaiseSteps * volumeStepDb / 20.0).toFloat()
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var job = 0L

    override fun play(sound: Sound, onDone: () -> Unit) {
        stop()
        val my = ++job
        val name = when (sound) {
            is Sound.Warn -> "warn"
            is Sound.Alarm -> "alarm"
        }
        val file = "sounds/${name}_${sound.level.coerceIn(1, 3)}.wav"
        try {
            audio.acquire()
            val p = MediaPlayer()
            context.assets.openFd(file).use { fd -> p.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            p.setAudioAttributes(audio.alarmAttributes)
            val gain = if (sound is Sound.Warn && sound.level < 3) lowerWarnGain else 1f
            p.setVolume(gain, gain)
            p.setOnCompletionListener {
                main.post {
                    if (my != job) return@post
                    release(p)
                    onDone()
                }
            }
            p.prepare()
            p.start()
            player = p
            Log.i(TAG, "sound_play file=$file gain=${"%.2f".format(gain)}")
        } catch (e: Exception) {
            Log.w(TAG, "sound_failed file=$file ${e.javaClass.simpleName} ${e.message}")
            audio.release()
            main.post { if (my == job) onDone() }
        }
    }

    private fun release(p: MediaPlayer) {
        if (player !== p) return
        player = null
        p.release()
        audio.release()
    }

    override fun stop() {
        job++
        player?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            release(it)
        }
    }
}
