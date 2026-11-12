package com.wakeon.app.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 차량 신호 출처. null은 "아직 모름(또는 읽을 수 없음)". */
interface VehicleSource {
    val gear: StateFlow<Gear?>
    val speedKmh: StateFlow<Float?>
    val brake: StateFlow<Boolean?>
    val turnSignal: StateFlow<TurnSignal?>
    val navVoice: StateFlow<Boolean?>

    fun current() = VehicleSignals(
        gear = gear.value,
        speedKmh = speedKmh.value,
        brake = brake.value,
        turn = turnSignal.value,
        navVoice = navVoice.value,
    )
}

/** 시연 신호 값(DEMO_SCENARIO 3장). 한 번이라도 들어온 항목만 값이 있다. */
class DemoVehicleSource : VehicleSource {
    override val gear = MutableStateFlow<Gear?>(null)
    override val speedKmh = MutableStateFlow<Float?>(null)
    override val brake = MutableStateFlow<Boolean?>(null)
    override val turnSignal = MutableStateFlow<TurnSignal?>(null)
    override val navVoice = MutableStateFlow<Boolean?>(null)

    fun clear() {
        gear.value = null
        speedKmh.value = null
        brake.value = null
        turnSignal.value = null
        navVoice.value = null
    }
}

/**
 * 시연 값이 한 번이라도 들어온 항목은 시연 값을, 아니면 실제(Pleos) 값을 쓴다.
 * 내비 안내 음성은 오디오 포커스로 따로 알아낸 값([audioNavVoice])과 시연 값 중 하나라도 켜지면 켜짐.
 */
class MergedVehicleSource(
    private val demo: VehicleSource,
    private val real: VehicleSource,
    private val audioNavVoice: StateFlow<Boolean?> = MutableStateFlow(null),
) {
    private val _signals = MutableStateFlow(VehicleSignals())
    val signals: StateFlow<VehicleSignals> = _signals.asStateFlow()

    /** 지금 값으로 다시 계산한다. 바뀌었으면 true. */
    fun refresh(): Boolean {
        val d = demo.current()
        val r = real.current()
        val next = VehicleSignals(
            gear = d.gear ?: r.gear,
            speedKmh = d.speedKmh ?: r.speedKmh,
            brake = d.brake ?: r.brake,
            turn = d.turn ?: r.turn,
            navVoice = d.navVoice ?: audioNavVoice.value ?: r.navVoice,
        )
        if (next == _signals.value) return false
        _signals.value = next
        return true
    }

    val sources: List<StateFlow<*>>
        get() = listOf(
            demo.gear, demo.speedKmh, demo.brake, demo.turnSignal, demo.navVoice,
            real.gear, real.speedKmh, real.brake, real.turnSignal, real.navVoice, audioNavVoice,
        )
}
