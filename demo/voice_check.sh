#!/bin/bash
# 단계 4 소리 확인용. 에뮬레이터를 켜고 Wakeon 창이 보이는 상태에서 실행한다.
#   bash demo/voice_check.sh
# 1) 출발 전 체크: 질문 5개와 "준비됐어요…"가 들린다(시연 대답을 넣으므로 말하지 않아도 된다).
# 2) 경고: 질문 → 무응답 두 번 → 경고음 1→2→3단계 → "졸음 신호가 보여요…" → 쉼터 제안.
# 화면에는 시연 표시가 나오지 않는다. 진행은 이 터미널에 출력된다.

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"

b() { "$ADB" shell am broadcast -a com.wakeon.app.DEMO -p com.wakeon.app "$@" >/dev/null; }

# 앱 로그에 [패턴]이 나올 때까지 기다린다(최대 [초]).
wait_log() {
  for _ in $(seq 1 $(( $2 * 2 ))); do
    "$ADB" logcat -d -s 'Wakeon:*' | grep -q -E "$1" && return 0
    sleep 0.5
  done
  echo "  (기다리던 로그가 없음: $1)"
  return 1
}

"$ADB" get-state >/dev/null 2>&1 || { echo "에뮬레이터가 연결되지 않았습니다. 에뮬레이터를 먼저 켜 주세요."; exit 1; }
"$ADB" shell am start -n com.wakeon.app/.MainActivity >/dev/null
sleep 2
"$ADB" logcat -c

echo "1) 출발 전 체크 — 질문 5개와 '준비됐어요. 졸릴 만한 때 제가 먼저 말 걸게요.' (약 40초, 첫 문장은 엔진이 깨어나느라 몇 초 늦을 수 있음)"
b --ez reset true --es clock 21:04 --es gear P --ei speed 0 --es answer "응, 부산까지." --ei latencyMs 1000
for a in "삼." "십이." "김치찌개요" "네 시간 반이요"; do b --es answer "$a" --ei latencyMs 1000; done
wait_log "tts_spoken vid=V-06" 90 && echo "   ✓ 끝 (응답 시간 기록:)" && "$ADB" logcat -d -s 'Wakeon:*' | grep -o "precheck_q idx=[0-9] latency_ms=[0-9]* correct=[a-z]*" | sed 's/^/     /'

echo "2) 경고 — 질문 2개(문항 간격은 시연 배속 60으로 4초) → 대답 없음 → '제 말 들리세요?' → 대답 없음 → 경고음 1·2·3단계 → 쉼터 제안 (약 50초)"
b --es gear D --ei speed 98 --ef routeKm 226 --ei driveMin 102 --ef timeScale 60
sleep 2
b --ei driveMin 124 --es answer "구" --ei latencyMs 1000
b --es answer "__none__"
b --es answer "__none__"
wait_log "tts_start vid=V-21" 120 && echo "   ✓ 끝"
sleep 8

echo "3) 정리 — 홈으로 되돌립니다"
b --ez reset true --es gear P --ei speed 0
sleep 1
b --ez reset true
echo "완료"
