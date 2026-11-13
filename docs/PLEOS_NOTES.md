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

### 빌드 설정 (단계 1에서 확정)

| 항목 | 값 | 이유 |
|---|---|---|
| JDK | Android Studio 내장 JBR 25 | 이현준 결정: brew로 따로 설치하지 않는다 |
| Gradle | 9.9.1 (wrapper) | JDK 25에서 돌리려면 Gradle 9.1 이상 필요 |
| AGP | 9.5.1 | Android Studio 2026.2에 들어 있는 버전(`libagp-version.jar`) |
| Kotlin | 2.3.21 | AGP 9 내장 Kotlin(2.2.10)보다 올려 JDK 25에서 안정적으로 컴파일 |
| 소스/바이트코드 | Java 17 (`compileOptions`, `jvmTarget`) | CLAUDE.md 기준 |
| AndroidX | core 1.13.1, activity-compose 1.9.3, lifecycle 2.8.7, Compose BOM 2024.12.01 | compileSdk 34에서 쓸 수 있는 마지막 버전(더 새 버전은 compileSdk 35 이상 요구) |
| `android-34` 플랫폼 | 첫 빌드 때 AGP가 자동으로 받음 | |

명령줄 빌드:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
```

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

- 왼쪽 853×1300 칸은 시스템 주행 화면 전용이다. 서드파티 앱은 오른쪽 영역에서 2:0(가로) 또는 1:1(세로)로 뜬다.

### 앱이 실제로 받은 창 크기 (단계 1, Wakeon 앱 화면·로그로 실측)

| 배치 | 앱 창 크기 | 화면 위치(px) | 비고 |
|---|---|---|---|
| 2:0 가로 (처음 실행) | **1682×1210 dp** | `[853,90]-[2535,1300]` | 앱 하나만 오른쪽 전체 |
| 1:1 세로 | **841×1210 dp** | `[853,90]-[1694,1300]` (내비는 `[1718,90]-[2559,1300]`) | 창 손잡이(오른쪽 끝 ⋮)를 왼쪽으로 끌면 나뉜다 |

- 로그 예: `window content=841x1210dp config=841x1210dp bounds=841x1210px density=1.0`
- 창 크기가 바뀔 때 앱이 다시 시작되지 않고 바로 다시 그려진다(`configChanges`로 처리).
- 공식 규격(840×1184 / 1700×1184)과 조금 다르므로, 화면은 고정 크기가 아니라 받은 창 크기에 맞춰 늘고 줄게 만든다.
- adb로 1:1 전환: `adb shell input swipe 2547 716 1700 716 600` (2:0 상태에서 오른쪽 끝 손잡이를 왼쪽으로 끌기)
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

## 7. 설치 직후 권한 상태 (단계 1 확인)

`adb shell dumpsys package com.wakeon.app | grep granted=`

| 권한 | 상태 |
|---|---|
| `pleos.car.permission.CAR_DRIVE`, `CAR_LIGHTS`, `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH` | 자동 허용 |
| `android.car.permission.CAR_POWERTRAIN` | 자동 허용 |
| `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` (SDK가 추가) | 자동 허용 |
| `android.car.permission.CAR_SPEED` | **거부 상태** → 앱이 실행 중 허락을 요청해야 함(단계 3) |
| `android.permission.RECORD_AUDIO` | **거부 상태** → 앱이 실행 중 허락을 요청해야 함(단계 4) |

시연 신호 수신기는 `android.permission.DUMP`를 가진 보낸 쪽만 받는다. adb 셸은 이 권한이 있어 `am broadcast`가 그대로 통한다.

**주의: 에뮬레이터의 앱 사용자는 10이다**(`adb shell am get-current-user` → 10). adb로 권한을 줄 때는 `--user current`를 붙인다.
붙이지 않으면 사용자 0에만 허락돼 앱에서는 여전히 거부 상태다(속성 설정 조회도 null이 나온다).

```bash
adb shell pm grant --user current com.wakeon.app android.car.permission.CAR_SPEED
```

---

## 8. 단계 3 확인 결과 (2026-11-13)

### 실제 차량 신호

앱 로그(`adb logcat -s Wakeon`)의 `vehicle … real_gear= real_speed= real_brake= real_turn=`로 확인했다.

| 신호 | 읽는 곳 | 에뮬레이터 결과 |
|---|---|---|
| 기어 | `GEAR_SELECTION` + Pleos SDK 숨은 기어 API | P 읽힘. Virtual Console에서 D로 바꾸면 D |
| 속도 | `PERF_VEHICLE_SPEED` | 0.0 읽힘. Virtual Console에서 60으로 바꾸면 60.000004 |
| 브레이크 페달 | Pleos `Brake` | false. Virtual Console 브레이크 50%면 true |
| 방향지시등 | Pleos `TurnSignal` | OFF. Virtual Console "Blink left"면 LEFT |

시연 값이 한 번이라도 들어온 항목은 실제 값보다 시연 값이 우선한다(`MergedVehicleSource`). 앱을 다시 시작하면 시연 값은 지워진다.

### Pleos Connect Virtual Console

- 여는 법: `adb forward tcp:8088 tcp:8088` → 브라우저에서 `http://127.0.0.1:8088/`
- 문서의 카드 목록에는 속도가 없지만, 실제로는 HTTP로 속도도 바꿀 수 있다. 화면 뒤에서 쓰는 주소:
  - `PUT /vehicle/gear?gear=Park|Reverse|Neutral|Drive` (Pleos 전용 속성 `VENDOR_GEAR_SELECTION` 0x21400400)
  - `PUT /vehicle/controls?speed=60&accelerator=0&brake=0` (속도 km/h, 페달 %)
  - `PUT /vehicle/turn-signal?mode=Off|BlinkLeft|BlinkRight|FlashLeft|FlashRight`
  - `GET /vehicle/state` (현재 값, `bridge_ready`)
- **브라우저 창(또는 `POST /browser/session?client=아무이름`을 1초마다 보내는 것)이 열려 있어야** 차량 쪽 연결(DDS)이 살아난다. 없으면 위 주소가 503을 돌려준다.

### 내비 경유지 추가

- `NaviHelper` 연결은 된다(`navi connected`).
- 목적지(경로)가 없는 상태에서 `addWaypoint()`를 부르면 성공·실패 콜백이 오지 않고 5초 시간 초과로 끝난다. 에뮬레이터 지도는 타일이 비어 있고 현재 위치는 성남시 수정구 시흥동(37.4127, 127.0947)이다.
- 그래서 시연에서는 경유지 추가가 실패하면 앱 안 표시(W-10)로 이어 가고 로그에 `navi_result result=demo_fallback`을 남긴다. README에 "에뮬레이터 내비에 경로가 없어 경유지 추가는 앱 안 표시로 대체했다"고 밝힌다.
- `assets/demo_route.json`의 쉼터에는 아직 좌표가 없다(`lat`, `lng`를 넣으면 경유지 추가를 시도한다).
- 진단용 시연 신호: `--ez naviProbe true` (Pleos 문서 예시 좌표로 경유지를 넣어 보고 결과만 로그에 남김)

### 앱이 가려지면 멈춘다

내비 앱이 앞으로 나와 Wakeon 창이 가려지자, 안드로이드가 뒤로 간 앱을 얼려 타이머와 시연 대답 처리가 다음 시연 신호가 올 때까지 멈췄다.
시연 녹화 중에는 Wakeon 창이 항상 보여야 한다. 창이 없을 때도 동작하게 하려면 포그라운드 서비스가 필요하다(SPEC T6, 1차 범위 밖).

---

## 9. 음성 (단계 4, 2026-11-13)

출처: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro , `.../SpeechToText/intro` 와 하위 페이지.
Maven `ai.pleos.playground:TextToSpeech:3.3.1`, `SpeechToText:3.3.1`(소스 jar 없음 → AAR 클래스를 javap로 확인). 의존성 `ai.pleos.playground.security:universal-access`가 따라오므로 Pleos 저장소는 `ai.pleos.*` 그룹 전체를 허용한다.

| 항목 | 결과 |
|---|---|
| 권한 | `pleos.car.permission.TTS_SERVICE`, `STT_SERVICE` (normal, 선언만), 마이크는 `RECORD_AUDIO` (실행 중 허락) |
| 모드 | `Mode.ON_DEVICE` — 네트워크·앱 등록 불필요. 콘솔 표시는 "준비 중"이지만 에뮬레이터 음성 서비스(`ai.pleos.playground.caas` 4.2.10, SDK 요구 4.1.29 이상)에서 동작 |
| Pleos TTS | 한국어(`Locale.KOREA`) 재생 확인. 오디오 속성 `USAGE_ASSISTANT`로 재생됨. **첫 문장은 엔진이 깨어나느라 약 6초 더 걸린다**(이후 1~4초) |
| 멈춤 | 브레이크 신호 → 앱 멈춤 명령 1ms → 서비스 onStop 7ms → 재생 정지 0.21초 (기준 0.25초) |
| Pleos STT | 마이크를 열고(16kHz) 말소리가 없으면 5초 뒤 무응답으로 닫힘. 첫 듣기는 녹음 시작까지 약 2초, 이후 0.35초 |
| 안드로이드 TTS | 이 에뮬레이터에서는 초기화 실패(status -1). Pleos TTS가 되므로 대체 경로로만 둔다 |
| 경고음·알람 | `assets/sounds/warn_1~3.wav`, `alarm_1~3.wav` (`tools/make_sounds.py`로 생성, 단계마다 소리 자체가 커짐). `USAGE_ALARM`으로 재생 |
| 내비 안내 감지 | 다른 앱의 `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` 재생 또는 오디오 포커스 상실 → 고부하(내비 안내). 에뮬레이터에서 내비 안내 음성을 낼 방법이 없어 실제 감지는 아직 확인 못 함 |

```kotlin
val tts = ai.pleos.playground.tts.TextToSpeech(context, Mode.ON_DEVICE)
tts.initialize(object : OnInitializeListener { override fun onSuccess() { tts.addEventListener(listener); tts.setLocale(Locale.KOREA) } ... })
tts.speak("문장", true)        // 이벤트: onStart → onDone / onStop / onError(ErrorType)
tts.stop()

val stt = ai.pleos.playground.stt.SpeechToText(context, Mode.ON_DEVICE)
stt.initialize(...); stt.addListener(resultListener); stt.setLocale(Locale.KOREA)
stt.request()                 // onStartedRecognition, onUpdatedEpdData(on, off: 바이트 위치), onUpdated(text, completed), onEndedRecognition, onError
stt.stop()
```

응답 시간 = 첫 발화 시작 − 질문 음성 끝(`SystemClock.elapsedRealtime`). Pleos STT는 EPD 시작 위치(16kHz·16bit·모노 → 1ms = 32바이트)로 첫 발화 시각을 계산하고, 없으면 첫 인식 글자가 온 시각을 쓴다.
마이크 권한이 없거나 인식 엔진이 없으면 "말소리 없음"과 같게 처리한다(듣기 창만큼 기다린 뒤 무응답). 말소리는 있었는데 글자가 안 나온 경우만 인식 실패다(SPEC T10).

에뮬레이터에서 마이크로 직접 말해 보려면: 에뮬레이터 옆 도구 막대 ⋯ → Microphone → "Virtual microphone uses host audio input" 켜기, 그리고
`adb shell pm grant --user current com.wakeon.app android.permission.RECORD_AUDIO` (단계 5의 동의 화면이 생기면 앱이 직접 허락을 요청한다).

---

## 10. 소리 깨짐 진단과 녹화 (단계 4 보완, 2026-11-13)

| 확인 | 결과 |
|---|---|
| scrcpy 5.0.1 녹음 (`--audio-source=output`, `playback`) | **무음.** 이 에뮬레이터의 소리는 차량용 출력 버스(`AUDIO_DEVICE_OUT_BUS`: bus0_media, bus2_voice_command, bus5_alarm …)로만 나가고, scrcpy가 쓰는 내부 녹음 경로가 없다. `playback` 방식은 미디어·게임 용도만 잡는데 우리 TTS는 ASSISTANT, 경고음은 ALARM 용도 |
| 에뮬레이터 출력을 WAV로 받기 (`QEMU_AUDIO_DRV=wav QEMU_WAV_PATH=…`) | 경고음 3개가 원본과 모양 일치(0.998~1.000), 10ms 단위 끊김 0 → **에뮬레이터 안에서 만든 소리는 멀쩡하다** |
| 결론 | 깨짐은 에뮬레이터 → 맥 CoreAudio 재생 단계에서 생긴다(QEMU CoreAudio 기본 버퍼 512프레임 × 4개) |
| 조치 | AVD 4코어·4GB → 6코어·6GB(원래 설정 백업 `recordings/avd_config_backup.ini`), `QEMU_COREAUDIO_BUFFER_SIZE=2048`, `QEMU_COREAUDIO_BUFFER_COUNT=8`로 켜기(`demo/start_emulator.sh`) |
| 조치 뒤 기기 안 지표 | 오디오 장치 데이터 부족 경고(`Not supplying enough data to HAL`) 52회 → 13회(voice_check 한 번, TTS 약 30초 + 경고음 3개). TTS 재생 14건 중 13건이 데이터 부족 0회 |
| 맥 스피커로 실제로 들리는 소리 | 사람이 들어서 확인해야 한다(맥 출력은 녹음할 방법이 없음) |
| 에뮬레이터 자체 녹화 (`adb emu screenrecord start/stop`) | webm(VP9 + Vorbis 44.1kHz 스테레오). **맥 재생 전 소리를 담아 깨짐 없음**(튐 0, 10ms 넘는 끊김 0). (→ 12장에서 고침: 맥 스피커로 재생하는 모드에서는 녹화본 목소리에도 갈라짐이 남는다. 녹화는 `--record` 모드로.) 소리 패킷은 무음을 건너뛰고 시각만 기록하므로 변환할 때 `aresample=async=1`로 빈 곳을 채우면 영상과 맞는다(앱 로그 시각과 일치 확인). 녹화 음량이 작아(최고 약 −28dBFS) `loudnorm`으로 −16 LUFS에 맞춘다 → `demo/record.sh` |
| 음량 칸 | 알람 스트림은 1~7 중 이미 7이고 `setStreamVolume`이 무시된다. 실제 음량은 차량 음량 그룹(0~45, 현재 20)이 정하며 앱은 바꿀 수 없다(시스템 권한 필요) |

시연 대답을 adb로 넣을 때 띄어쓰기가 있으면 작은따옴표로 한 번 더 감싼다: `--es answer "'네, 한 마리요.'"` (안 감싸면 "네,"까지만 들어감).

---

## 11. 녹화본 잡음 (2026-11-13, 이현준이 귀로 찾은 구간 0~6초, 48초~1분8초, 1분21초~1분45초)

| 확인 | 결과 |
|---|---|
| 정규화 전 원본(에뮬레이터 자체 녹화 webm) | 최고 약 −26dBFS. 말 사이에 −68dBFS 안팎의 아주 작은 잡음(무음 프레임 평균 RMS 0.17~0.19, 약 1/3 프레임이 0이 아님) |
| 마이크를 끈 뒤(시연 모드 STT 안 엶 + `avd hostmicoff`) | 원본의 말 사이 잡음은 비슷했다(0.174 → 0.188). "마이크가 열리면 입출력 동시 모드로 출력이 갈라진다"는 가설은 숫자로는 확인되지 않음. 마이크를 안 여는 것은 그대로 둔다(시연에 필요 없음, STT 시작음도 없어짐) |
| 원인 | 녹화 변환의 `loudnorm`(동적 정규화)이 조용한 곳을 약 25dB 이상 키워 이 미세 잡음을 "치지직"(무음 RMS 3~17)으로 만들었다 |
| 조치 | `demo/record.sh`: 노이즈 게이트(기준 0.0008, 열림 2ms, 닫힘 60ms) → +20dB 일정 증폭 → 리미터 0.9. 게이트 닫힘을 50ms 이하로 줄이면 말끝에서 딸깍 소리 |
| 검사 (`tools/click_check.py`) | 이전 녹음 `voice_check_20261113_080926.wav` 불합격(39초 넘음, 세 구간 무음 RMS 평균 4.3~5.5). 새 녹음 `voice_check_20261113_100828.wav` 합격(108초 전부, 순간 잡음 최대 0.208%, 무음 RMS 최대 0.0), mp4 안의 소리도 합격(최대 0.208%, 0.1) |

검사기 정의와 보정 과정은 `tools/click_check.py` 머리말. 순간 잡음은 말소리 자체와 섞여 구분이 어렵다. 이전 녹음에서도 기준 0.5%를 넘은 초는 없었고, 차이는 주로 무음 구간 배경 잡음에서 났다.


---

## 12. 녹화본 목소리 갈라짐 (2026-11-13, 이현준: "말 사이 잡음은 해결, 목소리 안 갈라짐은 남음")

같은 흐름(`demo/voice_check.sh`)을 조건만 바꿔 녹화하고, 같은 검사기(`tools/click_check.py`, 기준 그대로)로 쟀다. 게이트 없는 원본끼리 비교할 때는 음량만 맞췄다(곱하기만, 다른 처리 없음).

| 확인 | 결과 |
|---|---|
| 변환 과정 (100828 녹음) | 리미터는 한 번도 작동하지 않았다(지연 5ms만 생김, 지연을 맞추면 곱하기만 한 원본과 차이 −72.5dB). +20dB는 곱하기라 최고 −6.6dBFS로 잘림 없음. 게이트는 말소리 프레임의 3.7%(말 시작·끝)만 바꿈 → **갈라짐은 원본(녹화 webm)에 이미 있었다** |
| Pleos TTS 출력 | 22,050Hz · 모노 · 16bit (`dumpsys media.audio_flinger`의 트랙 정보, 서비스 `ai.pleos.playground.caas`). 말소리가 10.3kHz 위로 비어 있는 것과 맞음. SDK에는 샘플레이트·음량 API가 없다(`setAudioAttributes`·`setLocale`·`setSpeakerType`·`setAudioZoneId`뿐) |
| 기기 안 출력 | TTS → 음성 버스 `bus2_voice_command_out`(48kHz · 16bit · 15ms 주기), 경고음 → `bus5_alarm_out`. 차량 오디오 HAL이 음량 그룹 값으로 디지털 감쇠(20/45 = amplitude ratio 0.056, −25dB) → 녹화 원본이 −26dBFS로 작았던 이유. QEMU 출력과 녹화는 44.1kHz 스테레오 |
| 기기 출력 PCM vs 녹화본 (같은 실행, `start_emulator.sh --wav`) | 순간 잡음 평균 0.008% 대 0.006%(음량 20), 0.009% 대 0.009%(음량 40). 스펙트럼도 같은 자리에 같은 모양 → **녹화기(Vorbis 압축)는 흠집을 더하지 않는다**. 말 사이 미세 잡음은 PCM에도 있음(게이트 필요) |
| 맥 스피커 재생 중 녹화 vs 파일 출력 모드 녹화 (음량 40으로 같게) | 순간 잡음 평균 **0.040% 대 0.009%**, 지적 구간 0.065/0.073/0.031% 대 0.007/0.002/0.016%. 음량 20에서도 0.029%(100828) 대 0.006% → **맥 스피커로 내보내는 모드에서 목소리 안 순간 잡음이 4~5배** |
| 원인 지점 | 확인 못 함. 기기 안 음성 버스 데이터 부족 카운터는 두 모드가 비슷(FastMixer underrun 3,071 대 3,028, 일반 믹서 partial/empty 9/5 대 5/2). 맥 오디오로 내보내는 QEMU 단계로 추정 |
| 음량 조절 | `adb shell cmd car_service set-group-volume 0 <그룹> <0~100%>`로 바꿀 수 있다(앱 권한 아님, 개발 환경). 그룹 2 = 음성, 3 = 알람, 89% = 40/45. 재부팅 뒤에도 유지됨 |
| 조치 | 녹화는 `start_emulator.sh --record`(QEMU 소리를 파일로, 맥 스피커 없음) + 차량 음량 40. `record.sh`는 게이트만(기준 0.008 = 이전 0.0008의 +20dB), 게인·리미터 뺌. mp4 소리 AAC 192k → 320k(192k·256k는 게이트가 열리는 말 시작 프레임 앞 압축 번짐으로 한 초가 기준을 넘음) |
| 결과 | 이전 `voice_check_20261113_100828.wav`: 합격이지만 순간 잡음 최대 0.208% · 평균 0.034%. 새 `voice_check_20261113_105411.wav`: 합격, 최대 0.046% · 평균 0.005%, 무음 RMS 0.0. mp4 소리도 합격(0.046% · 0.006%, 무음 0.2) |

기준 0.5%는 그대로 두었다. 100828 녹음은 이 기준을 통과했지만 귀로는 갈라짐이 들렸다. 1초 최대값 기준은 이번 갈라짐을 잡지 못하고, 평균의 차이(4~5배)에서 드러난다.

---

## 13. 시스템 차량 값·내비 지도·벨트 경고 (2026-11-13, 녹화 화면 왼쪽·오른쪽)

| 확인 | 결과 |
|---|---|
| `adb shell cmd car_service inject-vhal-event` | **막힘**: "requires non-user build"(이 에뮬레이터는 user 빌드). `get-property-value`·`get-carpropertyconfig`도 같음 |
| 에뮬레이터 콘솔·gRPC | 차량 값(Car data) 기능 없음 |
| **Pleos 가상 콘솔** | 기기 안 `http://localhost:8088`("Pleos Connect Virtual Console", 앱 `ai.pleos.hardware.vehicle.emulator`가 웹뷰로 띄움). `adb forward tcp:18088 tcp:8088` 뒤 맥에서 REST로 쓸 수 있다. 값을 받으려면 `POST /browser/session?client=…` 접속 신호를 3초 안에 계속 보내야 한다(아니면 503 `emulator_inactive`) |
| 가상 콘솔 API | `PUT /vehicle/gear?gear=Park\|Reverse\|Neutral\|Drive`, `PUT /vehicle/controls?speed=km/h&accelerator=%&brake=%&steering=도`, `PUT /vehicle/turn-signal?mode=Off\|BlinkLeft\|BlinkRight`, `GET /vehicle/state`, `GET /vehicle/catalog`(값 63종) |
| 결과 | 시스템 Driving view가 앱과 같은 기어·속도를 보인다(D · 98·101·99·97·96·72·92km/h, 정차 P · 0). 앱의 실제 값(`real_gear`·`real_speed`)도 같이 바뀐다. `demo/run_scenario.sh`의 `car` |
| 안전벨트 경고 | **끄지 못함.** Driving view는 Pleos 서비스의 벨트 미체결 이벤트(`onSeatBeltWarningEvent`)로 "안전 벨트를 매세요."를 띄운다. 가상 콘솔에 벨트 체결 값이 없고, 경고등(`/vehicle/telltales/simulator` SeatBelt Off)·착석(`/vehicle/seat` occupied)·전원(`/vehicle/power-system/simulator` Ready/On)을 바꿔도 그대로. **주행 장면(D, 속도 > 0)에서는 도로 화면으로 바뀌어 보이지 않고, 정차 장면(P)에서만 보인다** |
| 내비 "서비스 연결 중입니다" | 내비(`ai.umos.maps.android.navigation.app`)가 지도 서버 `connect-gw-coral.umos.ai`의 주소를 찾지 못한다(`UnknownHostException`, 1초마다 재시도). 기기에서 `ping 8.8.8.8`은 되지만 `ping google.com`은 "unknown host"(DNS 실패). 기기 DNS = 10.0.2.3(에뮬레이터 대리). 맥에서는 같은 서버에 접속된다(HTTP 200). 맥 방화벽 꺼짐, 프록시 없음. 에뮬레이터 재시작·`-dns-server`(맥 DNS 202.30.38.101·164.124.101.2와 8.8.8.8) 지정 모두 효과 없음 → **원인 미해결** |
| 내비 목적지 | 앱에 시연 신호 `navDest`("이름\|위도\|경도")를 더했다 → NaviHelper `requestRoute()`. 내비가 지도 서버에 붙어야 경로가 뜬다(지금은 확인 못 함) |
