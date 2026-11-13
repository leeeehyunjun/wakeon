#!/bin/bash
# 소리 확인용. 에뮬레이터를 켜고(bash demo/start_emulator.sh) Wakeon 창이 보이는 상태에서 실행한다.
#   bash demo/voice_check.sh            # 맥 스피커로 듣기
#   bash demo/voice_check.sh --record   # 에뮬레이터 자체 녹화로 recordings/voice_check_<시각>.webm·.wav도 남김
# 1) 출발 전 체크: 질문 4개와 "강아지 좋죠."(짧은 호응), "준비됐어요…"
# 2) 대화: 점심 줄기 → 강아지 줄기("키우세요?" → "이름이 뭐예요?" → "보리, 이름 예쁘네요…")
#    → 대답이 늦어져 "혹시 졸리세요?" → "아니, 괜찮아." → "알겠어요. 그럼 보리 얘기 하나만 더요…"
# 3) 경고: 대답 없음 → "제 말 들리세요?" → 대답 없음 → 경고음 1·2·3단계 → 쉼터 제안
# 대답은 시연 신호로 넣으므로 말하지 않아도 된다. 화면에는 시연 표시가 나오지 않는다.

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
DIR="$(cd "$(dirname "$0")" && pwd)"

b() { "$ADB" shell am broadcast -a com.wakeon.app.DEMO -p com.wakeon.app "$@" >/dev/null; }
# 대답 넣기. adb는 기기 셸에서 띄어쓰기로 다시 쪼개므로 문장을 작은따옴표로 한 번 더 감싼다.
a() { b --es answer "'$1'" --ei latencyMs "$2"; }

# 앱 로그에 [패턴]이 나올 때까지 기다린다(최대 [초]).
wait_log() {
  for _ in $(seq 1 $(( $2 * 5 ))); do
    "$ADB" logcat -d -s 'Wakeon:*' | grep -q -E "$1" && return 0
    sleep 0.2
  done
  echo "  (기다리던 로그가 없음: $1)"
  return 1
}

"$ADB" get-state >/dev/null 2>&1 || { echo "에뮬레이터가 연결되지 않았습니다. bash demo/start_emulator.sh 로 먼저 켜 주세요."; exit 1; }
"$ADB" shell am start -n com.wakeon.app/.MainActivity >/dev/null
sleep 2

# 엔진 깨우기(첫 문장만 늦게 나오는 것을 녹음 전에 치움)
"$ADB" logcat -c
b --ez reset true --es gear P --ei speed 0 --ez c3 false
wait_log "tts_spoken vid=V-02" 30
"$ADB" logcat -c

# 녹화는 엔진 깨우기가 끝난 뒤부터(시연 녹화와 같은 순서)
if [ "$1" = "--record" ]; then
  REC="$(bash "$DIR/record.sh" start voice_check)"
  echo "녹화 시작: $REC"
fi

echo "1) 출발 전 체크 — 질문 4개, '강아지 좋죠.', '준비됐어요…' (약 35초)"
b --ez reset true --es clock 21:04 --es gear P --ei speed 0 --ef timeScale 60 --ez c3 false
a "응, 부산까지." 1000; a "삼." 1000; a "십이." 1000; a "강아지." 1000; a "김치찌개요" 1000
wait_log "tts_spoken vid=V-06" 90 && echo "   ✓ (응답 시간 기록)" && "$ADB" logcat -d -s 'Wakeon:*' | grep -o "precheck_q idx=[0-9] latency_ms=[0-9]* correct=[a-z]*" | sed 's/^/     /'

echo "2) 대화 — 점심 줄기 → 강아지 줄기 → '혹시 졸리세요?' → '아니, 괜찮아.' → '알겠어요. 그럼 보리 얘기 하나만 더요…' (약 60초)"
b --es gear D --ei speed 98 --ef routeKm 226 --ei driveMin 102 --es clock 22:46
sleep 2
b --ei driveMin 124 --es clock 23:08
a "네, 맛있었어요." 1000; a "돼지국밥이요." 1000
a "네, 한 마리요." 1200; a "보리요." 1700; a "…세 살… 인가?" 2800
a "아니, 괜찮아." 1500
a "__none__" 0; a "__none__" 0   # "보리는 산책 좋아해요?"와 "제 말 들리세요?"에 대답 없음
wait_log "tts_start vid=T-PET-4" 120 && echo "   ✓"

echo "3) 경고 — 대답 없음 → '제 말 들리세요?' → 대답 없음 → 경고음 1·2·3단계 → 쉼터 제안 (약 35초)"
wait_log "tts_start vid=V-21" 90 && echo "   ✓"
sleep 8

if [ -n "$REC" ]; then
  bash "$DIR/record.sh" stop "$REC"
fi

echo "4) 정리 — 홈으로 되돌립니다"
b --ez reset true --es gear P --ei speed 0
sleep 1
b --ez reset true
echo "완료"
