#!/bin/bash
# 시연 시나리오(docs/DEMO_SCENARIO.md 2장, 장면 1~22)를 시연 신호로 처음부터 끝까지 돌린다.
#   bash demo/run_scenario.sh              # 장면 1~22
#   bash demo/run_scenario.sh --from 14    # 1~13은 쉬지 않고 지나가고 14부터 장면마다 보여 준다(녹화도 14부터)
#   bash demo/run_scenario.sh --record     # 에뮬레이터 자체 녹화로 recordings/demo_<시각>.mp4·.wav도 남긴다
#   bash demo/run_scenario.sh --skip-nav-check   # 내비가 지도 서버에 연결되지 않아도 그대로 진행
# 녹화할 때는 에뮬레이터를 녹화 모드로 켠다: bash demo/start_emulator.sh --record (맥 스피커로는 소리가 안 나옴)
#
# 장면 0(타이틀)과 23(엔드 카드)은 편집에서 넣는다. 장면 사이는 정해진 시간이 아니라 앱 로그(말 시작·화면 바뀜)를 보고 넘어간다.
# 정차 화면은 2:0(가로) 창, 주행 화면은 1:1(세로) 창으로 바꿔 가며 진행한다(창 손잡이를 adb로 끈다, PLEOS_NOTES 2장).
# 대답은 시연 신호로 묻기 전에 미리 넣는다(시연 모드는 듣기 창이 열릴 때 대답이 없으면 5초 무응답). 화면에는 시연 표시가 없다.
# 시스템 화면(왼쪽 Driving view)도 같은 기어·속도를 보이도록, 시연 신호와 함께 에뮬레이터의 Pleos 가상 콘솔
# (기기 안 http://localhost:8088, "Pleos Connect Virtual Console")에 차량 값을 넣는다. 셸의 `cmd car_service
# inject-vhal-event`는 이 에뮬레이터(user 빌드)에서 막혀 있다(PLEOS_NOTES 12장).

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/.." && pwd)"
FROM=1
RECORD=0
NAV_CHECK=1
while [ $# -gt 0 ]; do
  case "$1" in
    --from) FROM="$2"; shift 2 ;;
    --record) RECORD=1; shift ;;
    --skip-nav-check) NAV_CHECK=0; shift ;;
    *) echo "사용법: bash demo/run_scenario.sh [--from 장면] [--record] [--skip-nav-check]"; exit 1 ;;
  esac
done

b() { "$ADB" shell am broadcast -a com.wakeon.app.DEMO -p com.wakeon.app "$@" >/dev/null; }
# 대답 넣기. 띄어쓰기가 있으면 작은따옴표로 한 번 더 감싼다(DEMO_SCENARIO 3장).
a() { b --es answer "'$1'" --ei latencyMs "$2"; }
clear_log() { "$ADB" logcat -c; }

# ── 시스템 차량 값 (Pleos 가상 콘솔 → VHAL)
CONSOLE_PORT=18088
CONSOLE="http://127.0.0.1:$CONSOLE_PORT"
# 콘솔은 접속 신호가 3초 넘게 끊기면 값을 받지 않는다(emulator_inactive). 1초마다 보낸다.
heartbeat() { while true; do curl -s -m 2 -X POST "$CONSOLE/browser/session?client=wakeon-demo" >/dev/null 2>&1; sleep 1; done; }
put() { curl -s -m 3 -o /dev/null -w "%{http_code}" -X PUT "$CONSOLE$1"; }
# car 기어(Park|Drive) 속도 [브레이크%] [방향지시등 Off|BlinkLeft|BlinkRight]
car() {
  local gear=$1 speed=$2 brake=${3:-0} turn=${4:-Off} acc=0
  [ "$speed" -gt 0 ] && [ "$brake" -eq 0 ] && acc=20
  if [ "$gear" = Park ]; then
    put "/vehicle/controls?speed=$speed&accelerator=$acc&brake=$brake&steering=0" >/dev/null
    put "/vehicle/gear?gear=$gear" >/dev/null
  else
    put "/vehicle/gear?gear=$gear" >/dev/null
    put "/vehicle/controls?speed=$speed&accelerator=$acc&brake=$brake&steering=0" >/dev/null
  fi
  put "/vehicle/turn-signal?mode=$turn" >/dev/null
}

SCENE=0
FAILS=()
# 앱 로그에 [패턴]이 나올 때까지 기다린다(최대 [초]). 못 보면 실패로 적고 계속한다.
wait_log() {
  for _ in $(seq 1 $(( $2 * 5 ))); do
    "$ADB" logcat -d -s 'Wakeon:*' | grep -q -E "$1" && return 0
    sleep 0.2
  done
  echo "  ✗ 장면 $SCENE: 기다리던 로그가 없음 ($1)"
  FAILS+=("장면 $SCENE: $1")
  return 1
}

# 장면 [번호]부터 화면을 보여 줄 때만 쉰다.
hold() { [ "$SCENE" -ge "$FROM" ] && sleep "$1"; return 0; }

WINDOW=landscape
window() {
  [ "$WINDOW" = "$1" ] && return
  if [ "$1" = portrait ]; then
    "$ADB" shell input swipe 2547 716 1700 716 600   # 2:0 → 1:1 (오른쪽 끝 손잡이를 왼쪽으로)
  else
    "$ADB" shell input swipe 1706 716 2550 716 600   # 1:1 → 2:0
  fi
  WINDOW=$1
  sleep 1.5
}

REC=""
REC_AT=0
START_AT=$SECONDS
scene() {
  SCENE=$1
  if [ "$RECORD" = 1 ] && [ -z "$REC" ] && [ "$SCENE" -ge "$FROM" ]; then
    REC="$(bash "$DIR/record.sh" start demo)"
    REC_AT=$SECONDS
    echo "● 녹화 시작: $REC"
  fi
  printf "# 장면 %s · 화면 %s · %s  (%ds)\n" "$1" "$2" "$3" $(( SECONDS - START_AT ))
}

"$ADB" get-state >/dev/null 2>&1 || { echo "에뮬레이터가 연결되지 않았습니다. bash demo/start_emulator.sh 로 먼저 켜 주세요."; exit 1; }
mkdir -p "$ROOT/recordings"
LOG="$ROOT/recordings/scenario_$(date +%Y%m%d_%H%M%S).log"
"$ADB" logcat -s 'Wakeon:*' > "$LOG" &
LOGCAT_PID=$!
"$ADB" forward tcp:$CONSOLE_PORT tcp:8088 >/dev/null
heartbeat &
HB_PID=$!
trap 'kill $LOGCAT_PID $HB_PID 2>/dev/null' EXIT
sleep 1
car Park 0
if curl -s -m 3 "$CONSOLE/vehicle/state" | grep -q '"value":"Park"'; then
  echo "시스템 차량 값: 가상 콘솔 연결됨 (P · 0km/h)"
else
  echo "  ✗ 시스템 차량 값을 바꾸지 못함(가상 콘솔 응답 없음). 왼쪽 Driving view는 P · 0km/h로 남는다"
  FAILS+=("준비: 가상 콘솔")
fi

"$ADB" shell am start -n com.wakeon.app/.MainActivity >/dev/null
sleep 2
# 창을 2:0으로 맞춘다(이전 실행이 1:1로 끝났을 수 있음).
"$ADB" logcat -d -s 'Wakeon:*' | grep "window content" | tail -1 | grep -q "841x" && WINDOW=portrait
window landscape

# 엔진 깨우기(첫 문장만 늦게 나오는 것을 녹화 전에 치움)
clear_log
b --ez reset true --es gear P --ei speed 0 --ez c3 false
wait_log "tts_spoken vid=V-02" 30

# 내비 점검(녹화 전): 지도 서버에 붙었는지 → 목적지 부산역 → 경로 안내 시작
NAV_PKG=ai.umos.maps.android.navigation.app
NAV_PID="$("$ADB" shell pidof $NAV_PKG | tr -d '\r')"
nav_ready=0
if [ -n "$NAV_PID" ]; then
  # 연결되지 않은 내비는 1초마다 서버 연결을 다시 시도하며 "gateway probe failed"를 남긴다. 6초 동안 없으면 연결된 것으로 본다.
  "$ADB" logcat -c
  sleep 6
  if ! "$ADB" logcat -d --pid="$NAV_PID" | grep -q "gateway probe failed"; then nav_ready=1; fi
fi
if [ "$nav_ready" = 1 ]; then
  # 새로 켠 에뮬레이터는 경로 계산에 25~35초 걸린다(2026-11-13 실측).
  # 첫 요청이 "경로 탐색 오류(일시적으로 서비스를 이용할 수 없습니다)"로 끝나기도 한다(2026-11-14, 바로 다시 요청하면 10초 만에 됨).
  # 그 오류 창은 경로가 잡힌 뒤에도 남으므로 닫고 다시 요청한다(최대 3번). 오류 없이 시간만 넘으면(이미 경로가 있을 때 등) 그대로 진행.
  route=""
  for try in 1 2 3; do
    clear_log
    b --es navDest "'부산역|35.1151|129.0415'"
    for _ in $(seq 1 225); do
      nl="$("$ADB" logcat -d -s 'Wakeon:*')"
      echo "$nl" | grep -q "navi route_started" && { route=ok; break; }
      echo "$nl" | grep -q "navi error" && { route=error; break; }
      sleep 0.2
    done
    [ "$route" = error ] || break
    echo "  내비 경로 탐색 오류(${try}번째) → 오류 창을 닫고 다시 요청"
    sleep 2
    "$ADB" shell input tap 1693 821   # 내비 오류 창의 "닫기"(경로를 넣으면 내비가 2:0 창으로 앞에 나온다)
    sleep 3
    route=""
  done
  if [ "$route" = ok ]; then
    echo "내비: 지도 연결됨 · 목적지 부산역 경로 안내 시작"
  elif [ "$route" = error ]; then
    echo "  ✗ 내비 경로 탐색이 3번 모두 오류. 녹화하지 않고 멈춘다(그래도 진행하려면 --skip-nav-check)"
    FAILS+=("준비: 내비 경로 탐색 오류")
    [ "$NAV_CHECK" = 1 ] && exit 2
  else
    echo "  ✗ 장면 0: 기다리던 로그가 없음 (navi route_started) — 오류는 없어 그대로 진행"
    FAILS+=("장면 0: navi route_started")
  fi
  # 경로를 넣으면 내비가 앞으로 나온다. Wakeon을 다시 앞으로 불러 창 순서(Wakeon 왼쪽 · 내비 오른쪽)를 지킨다.
  sleep 2
  "$ADB" shell am start -n com.wakeon.app/.MainActivity >/dev/null 2>&1
  sleep 2
else
  reason="$("$ADB" logcat -d --pid="$NAV_PID" 2>/dev/null | grep "gateway probe failed" | tail -1 | sed 's/.*gateway probe failed //')"
  echo "  ✗ 내비가 지도 서버에 연결되지 않음(화면: \"서비스 연결 중입니다\"). 원인: ${reason:-내비 앱 없음}"
  echo "    에뮬레이터 안에서 서버 이름을 찾지 못하면(UnknownHostException) 맥 네트워크를 바꾸거나 에뮬레이터를 다시 켠 뒤 시도한다."
  if [ "$NAV_CHECK" = 1 ]; then
    echo "    녹화하지 않고 멈춘다. 그래도 진행하려면 --skip-nav-check"
    exit 2
  fi
  FAILS+=("준비: 내비 지도 연결")
fi

# ════════════════════════════════════════ 장면 1~4 · 출발 전 (가로 창)
scene 1 W-02 "홈 · 출발 전"
clear_log
# talkFrom PET: 주행 중 첫 질문이 바로 강아지 줄기(점심 줄기 없음). 대답은 숫자로 넣는다(화면·자막에 "3." 그대로)
b --ez reset true --es clock 21:04 --es gear P --ei speed 0 --ef timeScale 60 --ei restSec 30 --ez c3 false --es talkFrom PET
car Park 0
a "응, 부산까지." 1000; a "3." 1000; a "12." 1000; a "강아지." 1000; a "김치찌개요" 1000
wait_log "tts_start vid=V-03" 30
scene 2 W-03 "출발 전 체크 1/4 · 졸린 정도"
wait_log "tts_start vid=Q-P1" 30
scene 3 W-03 "출발 전 체크 2/4 · 정답형 (3/4·4/4는 편집에서 줄임)"
wait_log "tts_start vid=V-06" 60
scene 4 W-03b "준비 완료 · 기준선 1.0초"
wait_log "tts_spoken vid=V-06" 20
hold 3

# ════════════════════════════════════════ 장면 5~16 · 주행 (세로 창)
scene 5 W-04 "주행 대기 · 연속 1시간 42분"
window portrait
clear_log
b --es gear D --ei speed 98 --ef routeKm 226 --ei driveMin 102 --es clock 22:46
car Drive 98
wait_log "screen W-04" 20
hold 5
clear_log
a "네, 한 마리요." 1200; a "보리요." 1700
b --ei driveMin 124 --ei speed 101 --es clock 23:08
car Drive 101
wait_log "tts_start vid=T-PET-1" 30
scene 6 W-05 "대화 1/6 · 강아지 줄기"
wait_log "tts_start vid=T-PET-2" 30
scene 7 W-05b "대답 인식 → 이름"
wait_log "talk_q id=T-PET-2" 30
scene 8 W-06 "대화 멈춤 · 브레이크 + 방향지시등"
b --ez brake true --es turn R --ei speed 84 --es clock 23:09
# 시스템 방향지시등은 켜지 않는다: 켜면 에뮬레이터가 측면 카메라 창(시스템 화면)을 앱 위에 띄운다(2026-11-13 영상 38초).
# 앱의 "방향지시등" 칩은 시연 신호(--es turn R)로 켠다(시연 값이 시스템 값보다 앞선다).
car Drive 84 40
wait_log "screen W-06" 10
hold 4
clear_log
a "…3살… 인가?" 2800
b --ez brake false --es turn OFF --ei speed 99 --es clock 23:10
car Drive 99
wait_log "tts_start vid=T-PET-3" 30
scene 9 W-05 "대답이 늦어짐 (2.8초)"
a "아니, 괜찮아." 1500
wait_log "tts_start vid=V-15" 30
scene 10 W-07 "의심 확인"
a "__none__" 0
wait_log "tts_start vid=T-PET-4" 30
scene 11 W-05 "'아니' → 하나만 더 (무응답)"
a "__none__" 0
wait_log "tts_start vid=V-14" 30
scene 12 W-07b "무응답 · 한 번 더"
wait_log "warn_level level=1" 30
scene 13 W-08 "단계적 경고 1→3"
clear_log
a "어… 응." 1300
b --ei speed 97 --es clock 23:14
car Drive 97
wait_log "tts_start vid=V-21" 60
scene 14 W-09 "쉼터 제안 · 선기졸음쉼터 12km"
b --ei speed 96 --es clock 23:15
car Drive 96
wait_log "tts_start vid=V-24" 30
scene 15 W-10 "경유지 추가"
b --ei speed 95
car Drive 95
wait_log "tts_spoken vid=V-24" 20
hold 2
clear_log
b --ef routeKm 237 --ei speed 72 --es clock 23:22
car Drive 72
wait_log "tts_start vid=V-25" 30
scene 16 W-10b "1km 전 안내"
wait_log "tts_spoken vid=V-25" 20
hold 2

# ════════════════════════════════════════ 장면 17~21 · 쉼터 (가로 창)
scene 17 W-12 "도착 · 휴식 제안"
window landscape
clear_log
a "응, 잘게." 1200
car Park 0
b --es gear P --ei speed 0 --es clock 23:24 --ez arrive true
wait_log "screen W-13" 40
scene 18 W-13 "휴식 (시연 30초 = 화면 15분)"
clear_log
a "으음… 일어났어." 2000
b --es clock 23:25
wait_log "wake_alarm level=1" 60
scene 19 W-13b "기상 알람 1→2단계"
b --es clock 23:40
wait_log "screen W-15" 40
scene 20 W-15 "재출발 체크 (2/3·3/3은 편집에서 줄임)"
b --es clock 23:41
a "8." 1100; a "13." 1000; a "6." 1000
wait_log "tts_start vid=V-52" 90
scene 21 W-16 "출발 · 다음 쉼터 32km"
b --es clock 23:42
wait_log "tts_spoken vid=V-52" 20
hold 3

# ════════════════════════════════════════ 장면 22 · 다시 주행 (세로 창)
scene 22 W-04 "다시 대기 · 연속 0분"
window portrait
clear_log
b --es gear D --ei speed 92 --es clock 23:43
car Drive 92
wait_log "state from=S-16 to=S-03" 20
wait_log "screen W-04" 20
hold 4

if [ -n "$REC" ]; then
  REC_SEC=$(( SECONDS - REC_AT ))
  bash "$DIR/record.sh" stop "$REC"
  echo "● 녹화 길이(편집 전): ${REC_SEC}초"
fi

echo "정리 — 가로 창, 홈으로"
window landscape
b --ez reset true --es gear P --ei speed 0
car Park 0
sleep 1
b --ez reset true

echo "전체 $(( SECONDS - START_AT ))초 · 앱 로그 $LOG"
if [ ${#FAILS[@]} -gt 0 ]; then
  echo "실패 ${#FAILS[@]}곳:"
  printf '  - %s\n' "${FAILS[@]}"
  exit 1
fi
echo "결과: 장면 1~22 끊김 없이 완료 (마지막 상태 S-03)"
