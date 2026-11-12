# Wakeon

> 졸음이 오기 쉬운 순간 차가 먼저 말을 걸고, 가장 가까운 졸음쉼터까지 데려가 15분 동안 제대로 쉬게 하는 Pleos Connect 음성 휴식 동반자.

<!-- 단계 8에서 Claude Code가 실제 값으로 채운다. {중괄호}는 채울 자리. -->

## 1. 앱 이름과 한 줄 소개

- 앱 이름: **Wakeon** (음성 호출어 ‘웨이크온’)
- 한 줄 소개: 졸음 감지기가 아니라 휴식 동반자. 대화 반응이 평소보다 느려지면 직접 묻고, "응" 한 마디로 졸음쉼터를 경유지에 넣고, 15분 휴식과 재출발 확인까지 이어 줍니다.

## 2. 실행 방법

1. Android Studio에서 Pleos Connect 에뮬레이터 v2.1.0(API 34)을 실행합니다.
2. APK 설치: `adb install apk/wakeoneteam_Wakeon_release.apk`
3. 앱 목록에서 Wakeon을 실행하고 동의 화면에서 마이크 권한을 허용합니다.
4. 차량 상태 바꾸기 — 둘 중 하나:
   - 에뮬레이터 Extended Controls의 차량 데이터(Virtual Console)에서 기어·속도를 바꿉니다.
   - 시연 스크립트: `bash demo/run_scenario.sh` (시연 영상과 같은 23장면을 순서대로 재생)
5. 시연 신호를 직접 보내려면: `adb shell am broadcast -a com.wakeon.app.DEMO -p com.wakeon.app --es gear D --ei speed 98` (전체 목록은 `docs/DEMO_SCENARIO.md` 3장)

## 3. 테스트 환경과 필요한 설정

- macOS {버전} (Apple Silicon), Android Studio {버전}
- Pleos Connect 에뮬레이터 v2.1.0, Android 14 (API 34), RAM 4GB, Cold boot
- 한국어 TTS 음성: {설치 여부와 방법}
- 사용 SDK: Pleos Vehicle {2.3.2}, NaviHelper {3.0.5}. 음성은 {Pleos TextToSpeech/SpeechToText 또는 Android 기본 TextToSpeech·SpeechRecognizer}
- 네트워크 없이 동작합니다.

## 4. 에뮬레이터 버전과 화면 규격

- 에뮬레이터: Pleos Connect v2.1.0
- 기준 디스플레이: Connect-S 17″ (2560×1440, 160dpi)
- 설계 기준(공식 규격표): 세로 창 840×1184dp, 가로 창 1700×1184dp
- 에뮬레이터 실측 창: 세로 {841×1210}dp, 가로 {1682×1210}dp (`adb shell dumpsys window`)
- 화면은 받은 창 크기에 맞춰 늘고 줄도록 만들었습니다.

## 5. APK 위치

- `apk/wakeoneteam_Wakeon_release.apk`

## 1차 제출 범위와 한계

- 구현: 출발 전 체크와 기준선, 위험 맥락 트리거(연속 2시간 등), 음성 대화와 응답 시간 측정, 고부하 시 대화 멈춤, 의심 확인, 단계적 경고, 쉼터 제안·경유지 추가, 도착·15분 휴식·기상 알람, 재출발 체크.
- 시연 모드: 영상 촬영을 위해 운전자 대답과 차량 신호를 adb로 넣을 수 있습니다. 판단과 상태 전이는 실제 앱 로직이 수행합니다.
- {에뮬레이터에 내비가 없으면: 경유지 추가는 NaviHelper 호출을 시도하고, 실패하면 앱 안 표시로 대체했습니다.}
- 시트·조명·공조 제어는 하지 않습니다(Pleos Vehicle Control '준비 중'). 대신 "등받이를 세워 주세요"처럼 음성으로 안내합니다.
- 졸음쉼터 데이터는 1차에서 시연 경로용 예시 데이터(`app/src/main/assets/demo_route.json`)를 씁니다. 공공데이터 연동은 2차 계획입니다.
- Wakeon은 졸음을 진단하지 않습니다.
