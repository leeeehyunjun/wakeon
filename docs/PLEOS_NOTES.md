# Pleos 개발 메모

단계 0(2026-11-13)에 확인한 개발 환경, 에뮬레이터 실측값, Pleos SDK 사용법을 적는다.
코드 조각은 공식 문서와 Maven의 `-sources.jar`(SDK 원본 소스)에서 옮겼고, 줄바꿈만 다시 맞췄다.

---

## 1. 개발 환경 (Mac)

| 항목 | 값 | 비고 |
|---|---|---|
| 시스템 `java -version` | 1.8.0_461 | 너무 낮음. Gradle용으로 쓰지 않는다 |
| Android Studio 내장 JDK | OpenJDK 25.0.4 (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`) | Android Studio 안에서 빌드할 때 쓰임 |
| Android SDK | `~/Library/Android/sdk` | platforms: `android-37.0`만 있음 → compileSdk 34용 `android-34`는 단계 1에서 받는다 |
| build-tools | 36.0.0 | |
| adb | 1.0.41 (platform-tools 37.0.1) | **PATH에 없음** → `~/.zshrc`에 추가 필요 |
| AVD | `Pleos_Connect_v2.1.0`, `Medium_Phone_API_37.0` | |

명령줄 빌드(`./gradlew`)를 하려면 `JAVA_HOME`이 JDK 17 이상을 가리켜야 한다. 단계 1에서 정한다.

---

## 2. 에뮬레이터 실측

| 항목 | 값 |
|---|---|
| AVD 이름 | `Pleos_Connect_v2.1.0` (`adb devices` → `emulator-5554`) |
| 빌드 | `RELEASE.release_connect_v2.1.0` (user 빌드, test-keys) |
| Android | 14 (API 34), `ro.hardware.type=automotive` |
| 화면 (`wm size`) | 2560×1440 px |
| 밀도 (`wm density`) | 160 dpi → **1dp = 1px** |
| 전체 설정 | `w2560dp h1210dp`, 앱 영역(mAppBounds) 2560×1300 |
| 왼쪽 칸 (주행 화면 `ai.umos.drivingview`) | Task bounds `[0,0]-[853,1300]` → 853×1300 |
| 오른쪽 칸 (내비 `ai.umos.maps.android.navigation.app`) | Task bounds `[853,90]-[2535,1300]` → **1682×1210** |

- CLAUDE.md의 실측값(세로 841×1210 / 가로 1682×1210)과 가로 창은 같다. 세로 창은 Task bounds가 853×1300으로 나와서, 앱이 실제로 받는 창 크기를 단계 1에서 앱 화면에 띄워 다시 잰다.
- 측정 명령: `adb shell wm size`, `adb shell wm density`, `adb shell dumpsys window | grep -E "mBounds|mAppBounds"`, `adb shell dumpsys activity containers`

### 에뮬레이터에 깔린 Pleos 서비스 (SDK 요구 버전과 비교)

| 패키지 | 설치 버전 | SDK가 요구하는 최소 버전 |
|---|---|---|
| `ai.pleos.playground.service.vehicle` | 2.3.22 (code 74) | Vehicle 2.3.2 → 서비스 2.3.13 (code 65) 이상 ✅ |
| `ai.pleos.playground.service.navi` | 3.0.4 (code 55) | NaviHelper 3.0.5 → 서비스 2.0.1 (code 11) 이상 ✅ |
| `ai.umos.maps.android.navigation.app` | 3.3.2-rc5-hotfix.3 | **내비 앱이 실제로 있음.** 경유지 추가를 진짜로 시도해 볼 수 있다(단계 3) |

### 음성 엔진 (단계 4 참고)

- 안드로이드 TTS 엔진: `ai.pleos.playground.caas` (`SpeechService`)가 `TTS_SERVICE`로 등록돼 있다. 안드로이드 기본 `TextToSpeech`를 쓰면 이 엔진으로 간다.
- 음성 인식기: `ai.umos.ambientai/.service.AmbRecognitionService` (기본 `voice_recognition_service`)
- 셸에서 `cmd car_service get-property-value`는 "requires non-user build"로 막혀 있다. 차량 값은 앱 안에서 읽어 로그로 확인해야 한다.

---

## 3. 차량 신호를 어디서 읽나

| 신호 | 읽는 곳 | 권한 | 권한 등급 |
|---|---|---|---|
| 브레이크 페달 | Pleos Vehicle SDK `Brake` | `pleos.car.permission.CAR_DRIVE` | normal (선언만) |
| 방향지시등 | Pleos Vehicle SDK `TurnSignal` | `pleos.car.permission.CAR_LIGHTS` | normal |
| **속도** | 안드로이드 `CarPropertyManager` `PERF_VEHICLE_SPEED` | `android.car.permission.CAR_SPEED` | **dangerous (실행 중 허락 필요)** |
| **기어** | 안드로이드 `CarPropertyManager` `GEAR_SELECTION` | `android.car.permission.CAR_POWERTRAIN` | normal |

**중요:** Pleos Vehicle SDK 2.3.2에는 공개된 속도 API가 없다. 기어 API(`vehicle.getGear()`)는 소스에 있지만 `@hide`이고
`@Deprecated("This API is deprecated, use the Google GEAR_SELECTION property.")`라고 적혀 있다. 그래서 속도·기어는 안드로이드 기본 차량 API로 읽는다.
권한 등급은 에뮬레이터에서 `adb shell dumpsys package permission <이름>`으로 확인했다.

---

## 4. Vehicle SDK (`ai.pleos.playground:Vehicle:2.3.2`)

출처:
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/get-property
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/callback
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code
- SDK 소스: `https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2-sources.jar`

> CLAUDE.md의 `/en/api-reference/connect-sdk-pleos/...` 주소는 이제 `/en/docs/pleos-only/connect-sdk-pleos/...`로 넘겨진다(redirect).
> API Reference 하위 페이지는 브라우저에서 그려지는 방식이라 받아지지 않는다. 함수 이름은 SDK 소스에서 확인했다.

### 설정

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}
// app/build.gradle.kts
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
}
```

```xml
<!-- AndroidManifest.xml: 쓰는 도메인 권한만 선언 -->
<uses-permission android:name="pleos.car.permission.CAR_DRIVE" />   <!-- Brake -->
<uses-permission android:name="pleos.car.permission.CAR_LIGHTS" />  <!-- TurnSignal -->
```

SDK가 자체 매니페스트로 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 권한과 `<queries>`(`ai.pleos.playground.vehicle.VEHICLE_START`)를 합쳐 넣는다. 따로 쓸 필요는 없다.
SDK 의존성: kotlin-stdlib 1.9.25, `vehicle-aidl` 1.1.1, `vehicle-data` 1.1.11, core-ktx 1.8.0, coroutines-android 1.6.3.

### 초기화와 해제

```kotlin
val vehicle = Vehicle(context)   // ai.pleos.playground.vehicle.Vehicle
vehicle.initialize()             // Vehicle Service에 연결 (IPC)
val brake = vehicle.getBrake()
val turnSignal = vehicle.getTurnSignal()
// ...
vehicle.release()                // 비동기 콜백이 끝나기 전에 부르지 않는다
```

### 브레이크 페달 (읽기 + 구독)

```kotlin
// ai.pleos.playground.vehicle.api.Brake  @RequiresPermission(CAR_DRIVE)
brake.getCurrentBrakePedalStatus(
    onComplete = { pressed: Boolean? -> /* true = 밟음 */ },
    onFailed = { e: Exception -> }
)

val brakeListener = object : BrakePedalStatusListener {   // ai.pleos.playground.vehicle.listener
    override fun onBrakePedalStatusUpdated(status: Boolean?) { /* ... */ }
    override fun onFailed(e: Exception) { /* ... */ }
}
brake.registerBrakePedalStatus(brakeListener)
brake.unregisterBrakePedalStatus(brakeListener)
```

### 방향지시등

```kotlin
// ai.pleos.playground.vehicle.api.TurnSignal  @RequiresPermission(CAR_LIGHTS)
turnSignal.getCurrentTurnSignalState(
    onComplete = { v: TurnSignalValue? -> /* OFF / LEFT / RIGHT */ },
    onFailed = { e -> }
)
val turnListener = object : TurnSignalStateListener {
    override fun onTurnSignalStateUpdated(state: TurnSignalValue?) { }
    override fun onFailed(e: Exception) { }
}
turnSignal.registerTurnSignalState(turnListener)
```

### 지원 여부 미리 확인

```kotlin
brake.checkBrakePedalStatusCapabilities(
    onComplete = { config -> /* config.accessType: NONE / READ / WRITE / READ_WRITE */ },
    onFailed = { e -> }
)
```

### 오류 처리

`onFailed`로 오는 `Exception`이 `VehicleException`이면 `e.errorCode`(`ErrorCode`)로 나눈다.
`ERROR_NOT_SUPPORTED_PROPERTY`, `ERROR_NOT_SUPPORTED_AREA`, `ERROR_NOT_SUPPORTED_VALUE_TYPE`, `ERROR_SECURITY_PERMISSION`,
`ERROR_SERVICE_CONNECTION_FAILURE`, `ERROR_INTERNAL_SERVICE`, `ERROR_DRIVING_RESTRICTED`, `ERROR_EMPTY_RESULT`, `ERROR_UNDEFINED`.
→ Wakeon은 실패하면 해당 신호를 "모름"으로 두고 시연 값(`DemoVehicleSource`)에 맡긴다.

---

## 5. 속도·기어 (안드로이드 `CarPropertyManager`)

출처: https://developer.android.com/reference/android/car/VehiclePropertyIds ,
https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager
(단계 3에서 에뮬레이터로 실제 값이 들어오는지 확인한다.)

```kotlin
// app/build.gradle.kts
android { useLibrary("android.car") }
```

```xml
<uses-permission android:name="android.car.permission.CAR_SPEED" />       <!-- dangerous: requestPermissions 필요 -->
<uses-permission android:name="android.car.permission.CAR_POWERTRAIN" />  <!-- normal -->
```

```kotlin
val car = Car.createCar(context)
val cpm = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager

val callback = object : CarPropertyManager.CarPropertyEventCallback {
    override fun onChangeEvent(v: CarPropertyValue<*>) {
        when (v.propertyId) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> speedKmh = (v.value as Float) * 3.6f   // 단위 m/s
            VehiclePropertyIds.GEAR_SELECTION -> gear = v.value as Int                       // VehicleGear.GEAR_PARK/REVERSE/NEUTRAL/DRIVE
        }
    }
    override fun onErrorEvent(propertyId: Int, areaId: Int) { }
}
cpm.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
cpm.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
// 끝낼 때: cpm.unregisterCallback(callback); car.disconnect()
```

기어 값: `VehicleGear.GEAR_NEUTRAL=0x1`, `GEAR_REVERSE=0x2`, `GEAR_PARK=0x4`, `GEAR_DRIVE=0x8`.

---

## 6. NaviHelper SDK (`ai.pleos.playground:NaviHelper:3.0.5`)

출처:
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/error-handling
- SDK 소스: `.../ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5-sources.jar`, `.../navi-data/3.0.4/navi-data-3.0.4-sources.jar`

### 설정

```kotlin
implementation("ai.pleos.playground:NaviHelper:3.0.5")
```

```xml
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE" />         <!-- get*Info(), requestTrip() -->
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE_SEARCH" />  <!-- requestRoute, addWaypoint, removeWaypoint ... -->
```

SDK가 `<queries>`(`ai.pleos.playground.intent.action.NAVI_START`)를 합쳐 넣는다.

### 초기화와 해제

```kotlin
import ai.pleos.playground.navi.helper.NaviHelper

val naviHelper = NaviHelper(applicationContext)
naviHelper.initialize(
    onConnected = { naviHelper.addListener(naviEventListener) /* 이제 API 호출 가능 */ },
    onFailed = { throwable -> /* 연결 재시도 모두 실패 → 앱 안 표시로 대체 */ }
)

// 끝낼 때
naviHelper.removeListener(naviEventListener)
naviHelper.release()
```

### 경유지 추가 (쉼터 안내에 사용)

경유지는 최대 2개(`WaypointIndex.FIRST`, `SECOND`, 전체 삭제는 `ALL`).

```kotlin
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo

naviHelper.addWaypoint(
    RequestWaypointInfo(
        latitude = 37.41275833,
        longitude = 127.09481944,
        poiName = "소프트웨어드림센터",
        waypointIndex = WaypointIndex.FIRST,
        poiId = "36khp2hdj2cfcm5nqhe3",
        address = "경기도 성남시 수정구 창업로40번길 6",
        poiSubId = "0"
    )
)
naviHelper.getWaypointInfo()               // 결과는 onWayPointInfo()로
naviHelper.removeWaypoint(WaypointIndex.FIRST)
```

목적지가 없는 상태라면 `requestRoute(RouteInfo(...))`로 먼저 목적지를 정하거나, `requestRouteWithWaypoints(RouteWithWaypointsRequest(destination, waypoints))`로 한 번에 요청한다.

### 결과 받기 (`NaviHelperEventListener`, 필요한 것만 override)

```kotlin
private val naviEventListener = object : NaviHelperEventListener {
    override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) { /* 경유지 추가 성공 → W-10 */ }
    override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) { }
    override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) { /* 쉼터 도착 → S-11 */ }
    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) { }
    override fun onError(errorCode: NaviErrorCode) { /* 실패 → E-5(V-29) 또는 시연 대체 */ }
}
```

`WaypointArrivedInfo` 필드: `duration`, `distance`, `index`, `name`, `address`.
오류 코드(`NaviErrorCode`): `CAUSE_LOW_BATTERY`, `CAUSE_SEARCH_ROUTE_OVER_DISTANCE`, `CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE`, `CAUSE_SEARCH_ROUTE`.
`addWaypoint()`는 반환값이 없다. 성공·실패는 위 콜백으로만 안다 → `NaviGateway`는 콜백을 기다리다 시간 초과면 실패로 본다.

---

## 7. 단계 1에서 이어서 할 것

- `android-34` 플랫폼 설치, 명령줄 빌드용 JDK 17 준비
- 앱 화면에 받은 창 크기(dp)를 띄워 세로·가로 창 실측 → 2장 표 갱신
- Manifest에 위 권한 넣기. `CAR_SPEED`는 실행 중 허락 요청 필요
