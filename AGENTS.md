# Wakeon · Codex 작업 규칙

이 저장소는 현대차·기아 차량용 앱 개발 공모전(Pleos Connect) 출품작 **Wakeon**의 안드로이드 앱이다.
작업 순서는 `BUILD.md`, 동작 기준은 `docs/SPEC.md`, 시연 흐름은 `docs/DEMO_SCENARIO.md`를 따른다. **화면 모양은 피그마 Design 페이지(node 47:3447)가 기준**이고, 찾는 법과 문구 규칙은 `docs/DESIGN.md`에 있다.

## 1. 함께 일하는 사람

- 이현준(PM, 비개발자). Mac(Apple Silicon), Android Studio, GitHub Desktop 사용.
- **모든 보고와 질문은 한국어로 한다.** 영어로 답하지 않는다.
- 명령은 그대로 복사해 칠 수 있게 코드 블록 하나에 하나씩 준다. Android Studio 버튼은 화면 위치("오른쪽 위 ▶ 버튼")로 설명한다.
- 비밀값(키스토어 비밀번호 등)은 채팅에 붙여 달라고 하지 않는다. `keystore.properties`에 직접 쓰게 안내하고, 이 파일은 `.gitignore`에 넣는다.
- 확인할 수 있는 건 직접 확인한다(빌드, `adb`, 로그). 사람에게 묻는 건 사람만 알 수 있는 것뿐이다.

## 2. 진행 방식

- `BUILD.md`의 단계를 **순서대로 하나씩** 한다. 각 단계 끝에서 멈추고 아래 형식으로 보고한 뒤 "다음 단계로 갈까요?"라고 묻는다.
  - 한 일 (3줄 이내)
  - 확인 방법과 결과 (빌드 성공 여부, 에뮬레이터 화면에서 볼 것)
  - 막힌 것 / 사람이 할 일
- 단계가 끝날 때마다 git 커밋한다. 커밋 메시지는 한국어로 `[단계 N] 무엇을 했는지`.
- **기록 규칙** (2026-11-13 추가)
  - `docs/DECISIONS.md`: 이현준이 정해 준 것, Codex가 정하고 이현준이 승인한 것, 문서와 다르게 한 것을 날짜·단계·결정·이유·관련 문서(SPEC/DESIGN 등) 표로 한 줄씩 더한다.
  - `docs/WORKLOG.md`: 단계를 끝낼 때마다 보고 내용(한 일, 확인 결과, 막힌 것)을 날짜·커밋 번호와 함께 그대로 붙인다.
  - 두 파일은 그 단계 커밋에 같이 넣는다.
- 마감이 **2026-11-15(일) 23:59**다. 완벽함보다 시연 흐름이 끝까지 도는 것이 먼저다. 막히면 30분 넘게 붙잡지 말고 대체안(BUILD.md 각 단계의 "막히면")으로 넘어가고 보고한다.

## 3. 범위 (11/15 1차 제출용)

- 만드는 것: `docs/DEMO_SCENARIO.md`의 23장면이 에뮬레이터에서 처음부터 끝까지 돌아가는 앱. 상태 머신·대사·타이머는 SPEC과 같은 이름(S-xx, V-xx, W-xx)을 쓴다.
- 만들지 않는 것(2차 이후): 정차 화면의 가로/세로 전환 완성도, 축소 모드·정차 전용 모드, 공공데이터 실시간 연동, F13(시트·조명·공조 제어), F14·F17·F19, 로그 서버.
- **차량 제어 코드는 쓰지 않는다.** Pleos 콘솔에서 Vehicle Control·FusedLocation·TextToSpeech·SpeechToText는 '준비 중'이다(2026-11-12 확인). 시트·조명은 음성 안내로만 한다("등받이를 세워 주세요").

## 4. 지켜야 할 규칙 (어기면 안 됨)

- 서비스 이름: 화면 글자는 `Wakeon`, 음성(TTS)·호출어는 `웨이크온`.
- 화면 문구는 TTS 문장과 같아야 한다. 대사는 `docs/SPEC.md` 7-2 대사 카탈로그의 ID로 관리하고(`VoiceLines.kt`), 코드에 문장을 흩뿌리지 않는다.
- **금지 표현**(SPEC 7-5): "졸음을 감지합니다", "안전해요", "괜찮아요"(앱이 말하는 경우), "졸리지 않네요", "틀렸어요", "위험합니다", "수면장애", "사고를 예방". 모든 대사·화면 문자열에 대해 단위 테스트로 검사하고, 걸리면 테스트가 실패해야 한다. 사용자 대답 예시("아니, 괜찮아")는 검사 대상이 아니다.
- 주행 중 화면: 한 덩어리 3줄·120자 이하, 핵심 글자 61dp 이상, 일반 33dp 이상, 터치 영역 139dp 이상. 키보드·영상 없음. 버튼이 있으면 같은 일을 음성으로도 할 수 있어야 한다.
- 화면은 피그마 Design 페이지 시안과 같아야 한다(색·글자 스타일·컴포넌트·간격). Figma MCP(`.mcp.json`)로 화면마다 시안을 확인하고, 만든 뒤 에뮬레이터 캡처와 비교한다. 시안 문구를 고친 곳은 `docs/DESIGN_DIFF.md`에 남긴다.
- 고정 px로 배치하지 않는다. 앱 창 크기는 기기가 정한다(공식 규격 840×1184 / 1700×1184 dp, 에뮬레이터 실측 841×1210 / 1682×1210). 받은 창 크기에 맞춰 늘고 줄게 만든다.
- 사용자를 끄는 경로("그만 쓰기")는 만들지 않는다. "그만"은 이번 라운드만 멈춘다.
- 음성 원본을 저장하지 않는다. 로그에는 응답 시간(ms)과 의도만 남긴다.

## 5. 기술 기준

- Kotlin, Jetpack Compose, minSdk 28, compileSdk/targetSdk 34, Java 17, Gradle 8 이상.
- Pleos Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`
  - `ai.pleos.playground:Vehicle:2.3.2` (사용 가능)
  - `ai.pleos.playground:NaviHelper:3.0.5` (사용 가능)
  - `ai.pleos.playground:TextToSpeech:3.3.1`, `ai.pleos.playground:SpeechToText:3.3.1` (콘솔 '준비 중' → 안 되면 안드로이드 기본으로 대체)
- 공식 문서: https://document.pleos.ai (API 참조: `/en/api-reference/connect-sdk-pleos/Vehicle/intro`, `.../NaviHelper/intro`, `.../SpeechToText/intro`). 페이지가 안 열리면 이현준에게 Pleos 콘솔의 샘플 앱(zip)을 받아 달라고 요청한다.
- 모든 외부 신호는 인터페이스 뒤에 둔다. `VehicleSource`(Pleos 구현 + 시연 구현), `Speaker`(Pleos TTS → Android TTS), `Listener`(Pleos STT → Android SpeechRecognizer → 시연 주입), `NaviGateway`(NaviHelper → 앱 안 표시만). 판단과 상태 전이는 `WakeonStateMachine` 한 곳에서만 한다.
- 시연 모드: `adb shell am broadcast`로 기어·속도·브레이크·연속 주행 시간·사용자 대답(응답 지연 포함)·시간 배속을 넣는다. 화면에 시연용 버튼을 띄우지 않는다(영상에 찍히면 안 됨). 형식은 `docs/DEMO_SCENARIO.md` 3장.

## 6. 제출물 (공모전 공고 기준)

- `apk/` 폴더에 release APK. 파일명 `wakeoneteam_Wakeon_release.apk` (팀명 wakeoneteam).
- README.md 필수 5항목: 앱 이름과 한 줄 소개 / 실행 방법 / 테스트 환경과 필요한 설정 / 사용한 Pleos Connect 에뮬레이터 버전과 화면 규격 / APK 위치.
- 저장소는 11/15까지 비공개, **11/16(월)까지 공개 전환**, 11/23까지 공개 유지. 접수 메일에 저장소 주소와 최종 커밋 해시를 적는다.
- 소스 코드는 시연 영상과 같은 동작이어야 한다. 영상에 없는 기능을 README에 쓰지 않는다.
