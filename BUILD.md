# Wakeon 빌드 계획 (1차 제출: 2026-11-15 23:59)

각 단계 끝에서 멈추고 `CLAUDE.md` 2장 형식으로 한국어 보고 → 커밋 → "다음 단계로 갈까요?".
목표 일정: 단계 0~3 금요일 오전, 4~5 금요일 오후~밤, 6~8 토요일 오전, 토요일 오후 녹화, 일요일 제출.

---

## 단계 0. 환경 확인 (15분)

1. `java -version`, Android SDK 경로(`~/Library/Android/sdk`), `adb version`, `emulator -list-avds`를 확인한다. `adb`가 PATH에 없으면 `~/.zshrc`에 추가하는 명령을 안내한다.
2. 에뮬레이터(Pleos Connect v2.1.0, API 34)가 켜져 있는지 `adb devices`로 확인한다.
3. `adb shell wm size`, `adb shell wm density`, `adb shell dumpsys window | grep -E "mBounds|mAppBounds" | head -20`으로 실제 화면·창 크기를 기록한다.
4. Pleos 문서(https://document.pleos.ai/en/api-reference/connect-sdk-pleos/Vehicle/intro, `.../Vehicle/project-setup`, `.../Vehicle/get-property`, `.../NaviHelper/intro`, `.../NaviHelper/waypoint-settings`, `.../NaviHelper/event-listeners`)를 읽고 초기화·속도·기어·브레이크 읽기·경유지 추가 방법을 `docs/PLEOS_NOTES.md`에 요약한다(코드 조각 포함, 출처 URL).

**완료:** 위 값이 `docs/PLEOS_NOTES.md`에 적혀 있다.
**막히면:** 문서가 안 열리면 이현준에게 Pleos 콘솔(developers.pleos.ai → 프로젝트 wakeon)의 샘플 앱 다운로드를 요청하고, 그동안 단계 1을 진행한다.

## 단계 1. 프로젝트 뼈대 (1시간)

1. 저장소 루트에 Android 프로젝트를 만든다. 모듈 `app`, 패키지 `com.wakeon.app`, Kotlin + Compose(Material3), minSdk 28, compile/target 34, Java 17. 버전 카탈로그(`gradle/libs.versions.toml`) 사용.
2. `settings.gradle.kts`의 `dependencyResolutionManagement.repositories`에 Pleos Maven을 추가하고 `Vehicle:2.3.2`, `NaviHelper:3.0.5`를 의존성에 넣는다. TTS·STT SDK는 단계 4에서 시도한다.
3. Manifest: 자동차 앱 요구 사항(`uses-feature android.hardware.type.automotive`, Pleos 문서의 권한·메타데이터), 마이크 권한 `RECORD_AUDIO`, 시연 신호 수신기(`DemoReceiver`, 액션 `com.wakeon.app.DEMO`).
4. `.gitignore`(build, .gradle, local.properties, keystore.properties, *.jks), `LICENSE` 없음(비워 둠).
5. 빈 화면에 "Wakeon"과 현재 창 크기(dp)를 띄워 에뮬레이터에서 실행한다.

**완료:** 에뮬레이터 앱 목록에 Wakeon이 보이고, 실행하면 창 크기가 표시된다. 세로(1:1) 창과 가로(2:0) 창 둘 다에서 값을 기록해 PLEOS_NOTES에 적는다.
**막히면:** Pleos 의존성 해결이 실패하면 의존성을 잠시 빼고 진행, 오류 전문을 보고한다.

## 단계 2. 상태 머신과 설정값 (2시간)

1. `config/WakeonConfig.kt`: SPEC 5-6 기준값을 전부 한곳에(코드에 숫자 직접 금지).
2. `voice/VoiceLines.kt`: SPEC 7-2 대사를 ID별 상수로. 시연에 쓰는 질문 은행(DEMO_SCENARIO 장면 6·9·11·20의 문장 포함, 정답 판정 규칙 포함).
3. `core/WakeonStateMachine.kt`: SPEC 4-1 상태 18개 enum, 4-2 전이표 중 **시연 경로에 필요한 행**을 구현한다: 전역 고부하, S-01→S-02→S-01, S-03→S-04(C1), S-04⇄S-05, S-04→S-06(S1·무응답), S-06→S-04('아니'), S-06→S-07(두 번째 무응답), S-07→S-08, S-08→S-09('응'), S-09→S-11(도착), S-11→S-12('응' 또는 10초), S-12→S-14("일어났어"), S-14→S-16/S-15, S-16→S-03(D). 나머지 행은 TODO 주석으로 남긴다.
4. 판단 로직: 연속 주행 시간(5-2), 트리거 C1~C3(5-3), 응답 시간·기준선(5-4), S1~S4(5-5). **기준선 규칙: 출발 전 체크의 정답형 유효 응답이 3개 이상이면 그 중앙값으로 확정(V-06), 3개 미만이면 V-07과 임시 기준 2,000ms를 쓰고 주행 초반 대화로 채운다. 확정 뒤에도 5개가 될 때까지 유효 응답을 더해 중앙값을 갱신한다.**
5. 단위 테스트: 전이표 경로, 기준선 계산(제외 대상 포함), S1 판정(DEMO_SCENARIO 장면 6·7·9 값 1.2/1.7/2.8초, B=1.0 → S1 참), 금지 표현 검사(VoiceLines 전부 + strings.xml).

**완료:** `./gradlew test` 통과. 테스트 이름은 한국어 설명 가능.

## 단계 3. 차량 신호와 시연 모드 (1.5시간)

1. `vehicle/VehicleSource` 인터페이스: `gear`, `speedKmh`, `brake`, `turnSignal`, `navVoice` 를 `StateFlow`로.
   - `PleosVehicleSource`: Pleos Vehicle SDK로 기어·속도·브레이크를 구독(단계 0 요약대로). 방향지시등은 가능하면 AAOS `TURN_SIGNAL_STATE`, 안 되면 항상 OFF.
   - `DemoVehicleSource`: 시연 신호 값.
   - `MergedVehicleSource`: 시연 값이 한 번이라도 들어온 항목은 시연 값을, 아니면 Pleos 값을 쓴다.
2. `demo/DemoReceiver`: DEMO_SCENARIO 3장의 extra 전부. `answer` 큐, `latencyMs`, `timeScale`, `restSec`, `clock`, `routeKm`, `arrive`, `reset`.
3. `nav/NaviGateway`: NaviHelper로 경유지 추가를 시도하고 결과(성공/실패)를 돌려준다. 실제 모드에서 실패하면 SPEC대로 E-5(V-29 음성 길안내)로 간다. 시연 모드에서 에뮬레이터에 내비가 없어 실패하면 W-10으로 계속 가되 로그에 `navi_result=demo_fallback`을 남기고, README에 "에뮬레이터에 내비가 없어 경유지 추가는 앱 안 표시로 대체했다"고 밝힌다(PRD 12장 원칙). 쉼터 목록은 `assets/demo_route.json`.
4. 에뮬레이터 Extended Controls(⋯) → Car data/Virtual Console에서 기어·속도를 바꿨을 때 앱이 받는지 확인한다.

**완료:** `adb shell am broadcast -a com.wakeon.app.DEMO -p com.wakeon.app --es gear D --ei speed 98` 후 로그(`adb logcat -s Wakeon`)에 상태 S-03이 찍힌다. Virtual Console 결과도 보고.

## 단계 4. 음성 (2시간)

1. `voice/Speaker`: Pleos TextToSpeech SDK를 먼저 시도 → 초기화 실패나 권한 거절이면 Android `TextToSpeech`(한국어 `Locale.KOREAN`)로. 재생 시작·종료 콜백 시각을 `SystemClock.elapsedRealtime()`으로 기록. 0.25초 안에 멈추는 `stopNow()`.
   - 에뮬레이터에 한국어 TTS 음성이 없으면 이현준에게 설치 방법을 안내한다(설정 → 시스템 → 언어 → 텍스트 음성 변환). 그래도 없으면 보고.
2. `voice/Listener`: 순서대로 ① 시연 주입 큐 ② Pleos SpeechToText ③ Android `SpeechRecognizer`. 듣기 창 5초(말 시작 시 최대 8초). 결과를 SPEC 7-3 인식 문법으로 의도(YES/NO/STOP/SLEEPY/AWAKE …)와 원문으로 바꾼다. 숫자는 한글 수사("삼", "십이", "팔")와 아라비아 숫자 모두.
3. 경고음: `assets/sounds/warn_1.ogg`~`warn_3.ogg`(점점 커지는 부드러운 차임, 직접 생성하거나 `ToneGenerator`로 대체), 알람 `alarm_1~3`. 단계마다 5초(알람은 30초, 시연 배속 적용).
4. 오디오 포커스: TTS 재생 때 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, 포커스 잃으면 고부하(내비 안내)로 처리.

**완료:** 시연 신호로 W-03 체크를 돌렸을 때 TTS가 들리고, 주입한 대답이 처리되며 응답 시간 로그 `precheck_q{idx,latency_ms,correct}`가 남는다.
**막히면:** 실제 음성 인식은 시연에 필수가 아니다(대답은 주입). 30분 안에 안 되면 ③까지 빼고 보고.

## 단계 5. 화면 (4~5시간)

`docs/DEMO_SCENARIO.md` 2장의 화면을 **이 순서로** 만든다. 각 화면은 상태 하나에 대응하는 Composable(`ui/screens/W05Talk.kt` 식). 문구는 표의 "화면에 꼭 있어야 할 것"과 VoiceLines 그대로.

0. 먼저 Figma MCP 연결을 확인한다(`DESIGN.md` 1장). 안 되면 멈추고 보고.
1. 공통: Pretendard 글꼴, 디자인 시스템(색 16개·글자 스타일 20개, `DESIGN.md` 2장)을 `ui/theme`에 정의. 시안에서 반복되는 요소(헤더, 상태 칩, 음성 상태 표시, 하단 안내 한 줄, 응/아니 버튼 — 누르면 음성 대답과 같은 의도)를 `get_design_context`로 확인해 공통 Composable로 만든다.
2. W-01 모드 선택·동의(첫 실행 1회) → W-02 홈 → W-03 출발 전 체크 → W-03b 준비 완료
3. W-04 주행 대기 → W-05 가벼운 대화 → W-05b 대답 인식 → W-06 대화 멈춤
4. W-07 의심 확인 → W-07b 무응답 → W-08 단계적 경고
5. W-09 쉼터 제안 → W-10 경유지 추가 완료 → W-10b 1km 전 안내
6. W-12 도착 → W-13 휴식 모드 → W-13b 기상 알람
7. W-15 재출발 체크 → W-16 출발 안내

- 화면마다 `DESIGN.md` 3장의 앱 창 node를 `get_design_context` + `get_screenshot`으로 보고 만든다. 정차 화면은 시안대로 가로 창(1700×1184), 주행 화면은 세로 창(840×1184).
- 화면마다 Compose `@Preview`(시안 크기)를 둔다.
- 한 화면을 끝낼 때마다 에뮬레이터 캡처와 시안 스크린샷을 비교해 다른 점을 고친다. 문구를 고친 곳은 `docs/DESIGN_DIFF.md`에 적는다.

**완료:** 에뮬레이터에서 상태를 시연 신호로 하나씩 넘기며 시연에 쓰는 화면이 모두 뜨고, 시안과 나란히 놓았을 때 같아 보인다. 화면마다 `adb exec-out screencap -p > shots/W-xx.png`로 캡처해 저장소 `docs/shots/`에 넣고 보고한다.

## 단계 6. 시나리오 스크립트와 통합 시험 (2시간)

1. `demo/run_scenario.sh`: DEMO_SCENARIO 2장을 순서대로 실행(장면 번호 출력, 장면별 대기). `--from N` 옵션으로 중간 장면부터 시작.
2. 처음부터 끝까지 3번 돌려 끊김 없이 S-03(장면 22)까지 가는지 확인한다. 실패한 장면과 로그를 보고한다.
3. 금지 표현 테스트, 전이 테스트 다시 통과 확인.

**완료:** 스크립트 한 번으로 23장면이 끝난다. 전체 녹화 시간(편집 전)을 보고한다.

## 단계 7. release APK (1시간)

1. 서명 키 생성 안내: 이현준이 터미널에 직접 입력하도록 `keytool -genkeypair -v -keystore ~/wakeon-release.jks -alias wakeon -keyalg RSA -keysize 2048 -validity 10000` 명령을 준다(비밀번호는 Claude에게 말하지 않음). `keystore.properties`(gitignore)에 경로·비밀번호를 쓰게 한다.
2. `signingConfigs.release`, `minifyEnabled false`(1차는 안정성 우선).
3. `./gradlew assembleRelease` → `apk/wakeoneteam_Wakeon_release.apk`로 복사.
4. 에뮬레이터에서 기존 앱을 지우고 release APK를 `adb install`해 시나리오를 한 번 더 돌린다.

**완료:** release APK로 시나리오 완주.

## 단계 8. README와 제출 준비 (1시간)

1. `README.md`를 공고의 필수 5항목으로 쓴다(초안: 저장소의 `README.md`). 실제 측정한 창 크기·에뮬레이터 버전으로 채운다.
2. 시연 영상에 쓴 기능만 적는다. 1차에 없는 기능은 "2차 계획"으로 분리.
3. 최종 커밋 → 해시를 이현준에게 알려 준다(접수 메일에 적어야 함).
4. 공개 전환은 11/16(월)에 이현준이 GitHub에서 한다(Settings → General → Danger Zone → Change visibility). 그 전에 저장소에 비밀값(keystore, 키)이 없는지 `git log -p | grep -i -E "password|storePassword|keyPassword"`로 확인한다.

**완료:** README 5항목, apk 폴더, 최종 해시.

## 단계 9. 시연 영상 편집 (2시간, 2026-11-13 추가)

기준: `docs/DEMO_SCENARIO.md` 2장(장면표)과 피그마 스토리보드(섹션 F, node 13:4541).

1. 녹화본: `bash demo/run_scenario.sh --record`로 만든 `recordings/demo_<시각>.mp4`·`.wav`와 같은 실행의 앱 로그(`recordings/scenario_<시각>.log`). 내비가 지도 서버에 붙으면 목적지 부산역을 넣은 녹화를 쓴다.
2. 결과: `recordings/final/wakeoneteam_Wakeon_demo.mp4`, 1920×1080, 30fps, H.264+AAC, 2분 20초 안팎(최대 2분 30초), 500MB 이하.
3. 구성: 0:00 타이틀 4초("Wakeon · 시연 영상" / "금요일 밤 9시, 서울에서 부산 본가까지") → 장면 1~22 → 엔드 카드 5초("졸음을 진단하지 말고, 쉼터까지 데려가라" / "Wakeon · 경고 → 쉼터 → 휴식 → 재출발을 하나의 대화로"). 타이틀·엔드 카드는 검은 배경에 Pretendard.
4. 자르기: 기다리는 시간은 잘라 내되, 앱이 말하는 문장은 중간에 자르지 않는다. 출발 전 체크 3/4·4/4, 재출발 2/3·3/3은 뺀다. 휴식 타이머는 15:00 → 줄어드는 모습 2~3초만. 점심 대화는 편집으로 자르지 않고 시연에서 묻지 않는다(`talkFrom PET`, 11/13 보완). 장면 22는 영상에서 뺀다(앱 동작은 그대로, 11/14 PM 결정). 장면 21에서 바로 엔드 카드로 간다.
5. 자막: 화면 아래에 앱 대사("차")와 운전자 대답("나", 강조색 #4FD0EE). 숫자는 숫자로("3.", "12."). 앱 대사 자막은 TTS 재생 시작(앱 로그 `tts_play` + 녹화 시작 오프셋)에 뜨고 소리가 끝나면 내려간다. 운전자 자막은 대답 시각에 뜬다. 녹화본의 화면·소리 차이(에뮬레이터 소리 지연 약 0.27초)는 편집에서 소리를 당겨 맞춘다(11/13 PM 결정). 자막은 컷을 넘지 않고, 운전자 자막은 앱의 다음 말 소리가 시작되기 전이나 컷이 바뀔 때 반드시 내려가고, 뜬 뒤 최소 0.8초는 같은 컷에 머문다(11/14 PM 규칙). 이를 위해 시연 대답에는 말하는 시간(기본 0.9초)이 붙는다. 스토리보드 편집 자막 6개: "응답 1.0초 — 이 속도가 오늘의 ‘평소’", "1시간 42분 뒤, 경부고속도로", "응답 2.8초 · 평소 1.0초", "… 5초 동안 대답이 없음", "— 15분 뒤 —", "연속 주행 시간이 0부터".
6. 표기: 화면 구석에 작게 "Pleos Connect 에뮬레이터 v2.1.0 실제 동작 화면 · 운전자 대답은 시연 신호로 입력"(영상 내내).
7. 내비 창이 로딩 상태면 그 부분을 어둡게 덮고 "내비(시스템 앱)"라고만 표시한다. 지도를 지어내 넣지 않는다.
8. 소리: 앱 소리 그대로, 배경 음악 없음.
9. 확인: 완성본을 `tools/click_check.py`로 검사, 5초 간격 프레임 모음(`docs/shots/final_sheet.png`), 장면별 시작 시각 표, 자막 싱크 표(`tools/video/sync_check.py`: 문장마다 자막 시작 − 소리 시작, 0.1초 넘는 곳 없음).

도구: `tools/video/render_overlays.swift`(타이틀·자막 그림, ffmpeg에 글자 그리기 기능이 없어 맥 AppKit으로 그림), `tools/video/edit_demo.py`(앱 로그로 자를 곳·자막 시각을 정하고 ffmpeg로 이어 붙임), `tools/video/sync_check.py`(완성본 화면의 자막 띠 변화와 소리 시작을 재서 자막 싱크 표를 만듦).

**완료:** 완성본 경로, 소리 검사 결과, 프레임 모음, 장면별 시작 시각 표, 자막 싱크 표를 보고하고 멈춘다.
