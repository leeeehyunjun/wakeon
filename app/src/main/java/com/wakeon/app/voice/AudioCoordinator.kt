package com.wakeon.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.wakeon.app.TAG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 오디오 포커스와 내비 안내 음성 감지 (BUILD 단계 4-4, SPEC 7-4).
 * - Wakeon이 말하거나 알림음을 낼 때 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`을 잡는다(음악은 음량만 낮아짐).
 * - 다른 소리(내비 안내 등)에 포커스를 뺏기거나, 내비 안내 용도(`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`)의
 *   소리가 재생 중이면 [navVoice]를 켠다. 상태 머신은 이것을 고부하(내비 안내)로 본다.
 */
class AudioCoordinator(context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _navVoice = MutableStateFlow<Boolean?>(null)
    val navVoice: StateFlow<Boolean?> = _navVoice.asStateFlow()

    val speechAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** 경고음·기상 알람: 앱 자체 알람 용도. 내비 안내 채널은 쓰지 않는다(AC-7.2). */
    val alarmAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var request: AudioFocusRequest? = null
    private var users = 0
    private var focusLost = false
    private var navPlaying = false

    private val clearLost = Runnable {
        focusLost = false
        update("focus_timeout")
        abandonIfIdle()
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                focusLost = true
                main.removeCallbacks(clearLost)
                main.postDelayed(clearLost, LOST_TIMEOUT_MS)
                update("focus_loss_$change")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusLost = false
                main.removeCallbacks(clearLost)
                update("focus_gain")
                abandonIfIdle()
            }
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            val nav = configs.any {
                it.audioAttributes.usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            }
            if (nav != navPlaying) {
                navPlaying = nav
                update("nav_playback")
            }
        }
    }

    init {
        try {
            am.registerAudioPlaybackCallback(playbackCallback, main)
        } catch (e: Exception) {
            Log.w(TAG, "audio playback_callback_failed ${e.message}")
        }
        _navVoice.value = false
    }

    /** 말하기·알림음을 시작할 때. 포커스를 못 받아도 소리는 낸다(로그만). */
    fun acquire() {
        users++
        if (request != null) return
        val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAttributes)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(focusListener, main)
            .build()
        val result = am.requestAudioFocus(r)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            request = r
        } else {
            Log.w(TAG, "audio focus_denied result=$result")
        }
    }

    /** 말하기·알림음이 끝났을 때. 내비에 포커스를 뺏긴 상태면 돌려받을 때까지 요청을 유지한다. */
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        abandonIfIdle()
    }

    private fun abandonIfIdle() {
        if (users > 0 || focusLost) return
        request?.let { am.abandonAudioFocusRequest(it) }
        request = null
    }

    private fun update(reason: String) {
        val on = navPlaying || focusLost
        if (_navVoice.value != on) {
            Log.i(TAG, "audio nav_voice=$on reason=$reason")
            _navVoice.value = on
        }
    }

    private companion object {
        /** 포커스를 돌려받는 알림이 오지 않을 때 내비 안내가 끝난 것으로 보는 시간 */
        const val LOST_TIMEOUT_MS = 20_000L
    }
}
