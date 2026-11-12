package com.wakeon.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear as PleosGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import com.wakeon.app.TAG
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 실제 차량 신호 (docs/PLEOS_NOTES.md 3~5장).
 * - 기어: 안드로이드 `GEAR_SELECTION` + Pleos Vehicle SDK 기어(Virtual Console이 쓰는 VENDOR_GEAR_SELECTION). 나중에 바뀐 값을 쓴다.
 * - 속도: 안드로이드 `PERF_VEHICLE_SPEED` (m/s → km/h). `CAR_SPEED` 권한이 허락돼야 읽힌다.
 * - 브레이크 페달·방향지시등: Pleos Vehicle SDK.
 * 실패하면 그 항목은 null로 남고 시연 값이나 안전 쪽 기본값(F1)을 쓴다.
 */
class PleosVehicleSource(private val context: Context) : VehicleSource {
    override val gear = MutableStateFlow<Gear?>(null)
    override val speedKmh = MutableStateFlow<Float?>(null)
    override val brake = MutableStateFlow<Boolean?>(null)
    override val turnSignal = MutableStateFlow<TurnSignal?>(null)
    override val navVoice = MutableStateFlow<Boolean?>(null)

    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var vehicle: Vehicle? = null
    private var speedSubscribed = false

    private val carCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> (value.value as? Int)?.let { gear.value = fromAndroidGear(it) }
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> (value.value as? Float)?.let { speedKmh.value = it * 3.6f }
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "vehicle car_property_error id=$propertyId")
        }
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            brake.value = status
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "vehicle pleos_brake_failed ${e.message}")
        }
    }

    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            turnSignal.value = when (state) {
                TurnSignalValue.LEFT -> TurnSignal.LEFT
                TurnSignalValue.RIGHT -> TurnSignal.RIGHT
                TurnSignalValue.OFF -> TurnSignal.OFF
                null -> null
            }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "vehicle pleos_turn_failed ${e.message}")
        }
    }

    private val pleosGearListener = object : GearSelectionListener {
        override fun onSelected(gear: PleosGear?) {
            fromPleosGear(gear)?.let { this@PleosVehicleSource.gear.value = it }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "vehicle pleos_gear_failed ${e.message}")
        }
    }

    fun start() {
        startCarProperties()
        startPleos()
    }

    /** `CAR_SPEED` 권한을 막 허락받았을 때 다시 부른다. */
    fun subscribeSpeed() {
        val cpm = properties ?: return
        if (speedSubscribed) return
        val config = runCatching { cpm.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED) }.getOrNull()
        Log.i(TAG, "vehicle speed_supported=${config != null}")
        speedSubscribed = try {
            cpm.registerCallback(carCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        } catch (e: SecurityException) {
            Log.w(TAG, "vehicle speed_permission_missing")
            false
        } catch (e: Exception) {
            Log.w(TAG, "vehicle speed_failed ${e.message}")
            false
        }
        Log.i(TAG, "vehicle speed_subscribed=$speedSubscribed")
    }

    private fun startCarProperties() {
        try {
            val c = Car.createCar(context)
            if (c == null) {
                Log.w(TAG, "vehicle car_unavailable")
                return
            }
            car = c
            val cpm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            properties = cpm
            val gearOk = cpm.registerCallback(carCallback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            Log.i(TAG, "vehicle car_gear_subscribed=$gearOk")
            subscribeSpeed()
        } catch (e: Exception) {
            Log.w(TAG, "vehicle car_failed ${e.javaClass.simpleName} ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun startPleos() {
        try {
            val v = Vehicle(context)
            v.initialize()
            vehicle = v
            v.getBrake().registerBrakePedalStatus(brakeListener)
            v.getTurnSignal().registerTurnSignalState(turnListener)
            // 문서에 숨겨진 기어 API. Virtual Console의 기어 카드가 이 값(VENDOR_GEAR_SELECTION)을 바꾼다.
            v.getGear().registerGearSelection(pleosGearListener)
            Log.i(TAG, "vehicle pleos_started")
        } catch (e: Exception) {
            Log.w(TAG, "vehicle pleos_failed ${e.javaClass.simpleName} ${e.message}")
        }
    }

    fun release() {
        try {
            properties?.unregisterCallback(carCallback)
            car?.disconnect()
            vehicle?.release()
        } catch (_: Exception) {
        }
    }

    private fun fromAndroidGear(v: Int): Gear? = when (v) {
        VehicleGear.GEAR_PARK -> Gear.P
        VehicleGear.GEAR_REVERSE -> Gear.R
        VehicleGear.GEAR_NEUTRAL -> Gear.N
        VehicleGear.GEAR_UNKNOWN -> null
        else -> Gear.D // DRIVE, 1~9단
    }

    private fun fromPleosGear(g: PleosGear?): Gear? = when (g) {
        null, PleosGear.GEAR_UNKNOWN -> null
        PleosGear.GEAR_PARK -> Gear.P
        PleosGear.GEAR_REVERSE -> Gear.R
        PleosGear.GEAR_NEUTRAL -> Gear.N
        else -> Gear.D
    }
}
