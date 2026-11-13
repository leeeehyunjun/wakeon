#!/bin/bash
# 화면 확인용(단계 5): 시연 흐름(docs/DEMO_SCENARIO.md 2장)을 시연 신호로 따라가며 화면마다 앱 창을 캡처한다.
#   bash demo/capture_screens.sh          # docs/shots/W-xx.png 로 저장
# 에뮬레이터를 켜고(bash demo/start_emulator.sh) Wakeon 앱이 오른쪽 창에 떠 있는 상태에서 실행한다.
# 정차 화면은 2:0(가로) 창, 주행 화면은 1:1(세로) 창에서 찍는다(창 손잡이를 adb로 끌어 바꾼다).
# 대답은 시연 신호로 넣는다. 화면에는 시연 표시가 나오지 않는다.

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/shots"
mkdir -p "$OUT"

b() { "$ADB" shell am broadcast -a com.wakeon.app.DEMO -p com.wakeon.app "$@" >/dev/null; }
# 대답 넣기. 띄어쓰기가 있으면 작은따옴표로 한 번 더 감싼다(DEMO_SCENARIO 3장).
a() { b --es answer "'$1'" --ei latencyMs "$2"; }

clear_log() { "$ADB" logcat -c; }

# 앱 로그에 [패턴]이 나올 때까지 기다린다(최대 [초]).
wait_log() {
  for _ in $(seq 1 $(( $2 * 5 ))); do
    "$ADB" logcat -d -s 'Wakeon:*' | grep -q -E "$1" && return 0
    sleep 0.2
  done
  echo "  (기다리던 로그가 없음: $1)"
  return 1
}

WINDOW=landscape
# 창 배치 바꾸기: portrait = 1:1(세로 841), landscape = 2:0(가로 1682). PLEOS_NOTES 2장.
window() {
  [ "$WINDOW" = "$1" ] && return
  if [ "$1" = portrait ]; then
    "$ADB" shell input swipe 2547 716 1700 716 600
  else
    "$ADB" shell input swipe 1706 716 2550 716 600
  fi
  WINDOW=$1
  sleep 1.5
}

# 앱 창만 잘라 저장한다. 화면 2560×1440, 앱 창은 [853,90]부터.
shot() {
  local tmp="$OUT/.cap.png"
  "$ADB" exec-out screencap -p > "$tmp"
  local w=1682
  [ "$WINDOW" = portrait ] && w=841
  if command -v ffmpeg >/dev/null; then
    ffmpeg -v error -y -i "$tmp" -vf "crop=$w:1210:853:90" "$OUT/$1.png"
    rm -f "$tmp"
  else
    mv "$tmp" "$OUT/$1.png"
  fi
  echo "  📷 $1"
}

"$ADB" get-state >/dev/null 2>&1 || { echo "에뮬레이터가 연결되지 않았습니다."; exit 1; }
# 앱 로그 전체를 따로 남긴다(문제 찾기용, recordings/는 저장소에 올리지 않음)
mkdir -p "$ROOT/recordings"
LOG="$ROOT/recordings/capture_$(date +%Y%m%d_%H%M%S).log"
"$ADB" logcat -s 'Wakeon:*' > "$LOG" &
LOGCAT_PID=$!
trap 'kill $LOGCAT_PID 2>/dev/null' EXIT
# 앱을 새로 띄워 지금 화면 로그를 받는다.
"$ADB" shell am force-stop com.wakeon.app
clear_log
"$ADB" shell am start -n com.wakeon.app/.MainActivity >/dev/null
wait_log "screen W-" 20
window landscape

# 첫 실행 동의(W-01): 동의 전이면 찍고 "동의하고 시작"을 누른다.
if "$ADB" logcat -d -s 'Wakeon:*' | grep -q "screen W-01"; then
  shot W-01
  "$ADB" shell input tap 2104 1112
  sleep 2
fi

echo "장면 1~4 · 출발 전 (가로 창)"
clear_log
b --ez reset true --es clock 21:04 --es gear P --ei speed 0 --ef timeScale 60 --ei restSec 30 --ez c3 false --es talkFrom FOOD
wait_log "tts_start vid=V-02" 30 && sleep 1.5 && shot W-02
# 대답은 묻기 전에 미리 넣는다(시연 모드는 듣기 창이 열릴 때 대답이 없으면 5초 무응답으로 처리).
clear_log; a "응, 부산까지." 1000; a "3." 1000; a "12." 1000; a "강아지." 1000; a "김치찌개요" 1000
wait_log "tts_start vid=V-03" 30 && sleep 1.5 && shot W-03
wait_log "precheck_q idx=1" 30 && sleep 0.2 && shot W-03_heard
wait_log "tts_start vid=Q-P1" 30 && sleep 1 && shot W-03_quiz
wait_log "tts_start vid=V-06" 60 && sleep 1.5 && shot W-03b

echo "장면 5~16 · 주행 (세로 창)"
window portrait
clear_log
b --es gear D --ei speed 98 --ef routeKm 226 --ei driveMin 102 --es clock 22:46
wait_log "screen W-04" 20 && sleep 1.5 && shot W-04
clear_log
b --ei driveMin 124 --es clock 23:08
# 점심 줄기 첫 질문: 말하는 중 → 듣는 중(대답 지연 2.5초 동안) → 인식 결과
wait_log "screen W-05" 30 && wait_log "tts_start" 30 && sleep 1.2 && shot W-05
clear_log; a "네, 맛있었어요." 2500
wait_log "listen demo_answer" 30 && sleep 0.8 && shot W-05_listen
wait_log "talk_q" 10 && sleep 0.3 && shot W-05b
clear_log; a "돼지국밥이요." 1000; a "네, 한 마리요." 1200; a "보리요." 1700
# 장면 8: "보리요." 인식 결과 2초 안에 브레이크
wait_log "talk_q.*latency_ms=1700" 90 && b --ez brake true --es turn R --ei speed 84 --es clock 23:09
wait_log "screen W-06" 10 && sleep 0.8 && shot W-06
clear_log; b --ez brake false --es turn OFF --ei speed 99 --es clock 23:10
a "…3살… 인가?" 2800
wait_log "tts_start vid=V-15" 60 && sleep 1.5 && shot W-07
clear_log; a "아니, 괜찮아." 1500; a "__none__" 0; a "__none__" 0
wait_log "tts_start vid=T-PET-4" 30 && sleep 1.5 && shot W-05_long
wait_log "tts_start vid=V-14" 30 && sleep 1 && shot W-07b
clear_log
wait_log "warn_level level=2" 60 && sleep 0.5 && shot W-08
wait_log "tts_start vid=V-21" 60 && sleep 1.5 && shot W-09
clear_log; a "어… 응." 1300
wait_log "tts_start vid=V-24" 30 && sleep 1.5 && shot W-10
clear_log; b --ef routeKm 237 --ei speed 72 --es clock 23:22
wait_log "tts_start vid=V-25" 30 && sleep 1.5 && shot W-10b

echo "장면 17~21 · 쉼터 (가로 창)"
window landscape
clear_log; b --es gear P --ei speed 0 --es clock 23:24 --ez arrive true
wait_log "tts_start vid=V-30" 30 && sleep 2 && shot W-12
clear_log; a "응, 잘게." 1200
wait_log "screen W-13" 30 && sleep 3 && shot W-13
wait_log "screen W-13b" 60 && sleep 1 && shot W-13b
clear_log; a "으음… 일어났어." 2000
wait_log "tts_start vid=Q-R1" 60 && sleep 1.5 && shot W-15
clear_log; a "8." 1100; a "13." 1000; a "6." 1000
wait_log "tts_start vid=V-52" 60 && sleep 1.5 && shot W-16

echo "장면 22 · 다시 주행 (세로 창)"
window portrait
clear_log; b --es gear D --ei speed 92 --es clock 23:43
wait_log "screen W-04" 20 && sleep 1.5 && shot W-04_restart

echo "정리 — 가로 창, 홈으로"
window landscape
b --ez reset true --es gear P --ei speed 0
sleep 1
b --ez reset true
echo "완료: $OUT (앱 로그 $LOG)"
