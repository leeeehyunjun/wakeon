#!/bin/bash
# 시연 녹화: 에뮬레이터 자체 화면 녹화(화면 + 기기 안 소리)를 쓴다.
# 에뮬레이터는 녹화 모드로 켠다: bash demo/start_emulator.sh --record
#   bash demo/record.sh start 이름    → recordings/이름_<시각>.webm 녹화 시작, 경로를 출력
#   bash demo/record.sh stop 경로      → 녹화를 멈추고 같은 이름의 .mp4(영상+소리)와 .wav(소리만)를 만든다
#   bash demo/record.sh convert 경로   → 이미 녹화한 .webm을 다시 변환만 한다
#
# 왜 이 방식인가(2026-11-13, docs/DECISIONS.md · docs/PLEOS_NOTES.md 10~12장):
# - scrcpy는 이 차량용 에뮬레이터의 소리를 녹음하지 못한다(소리가 차량용 출력 버스로만 나가 무음으로 녹음됨).
# - 에뮬레이터 자체 녹화는 기기 출력을 그대로 담는다(같은 실행의 기기 출력 PCM과 비교해 차이 없음).
# - 녹화 소리는 무음 구간을 건너뛰고 시각만 기록하므로, 변환할 때 빈 곳을 무음으로 채워 영상과 맞춘다.
# - 소리 가공은 노이즈 게이트 하나만 한다: 말 사이의 아주 작은 잡음을 0으로 만든다.
#   게이트 닫힘 60ms(더 짧으면 말끝에서 딸깍 소리). 음량은 start_emulator.sh가 차량 음량을 40/45로 올려
#   원본이 이미 −6.6dBFS이므로 키우지 않는다(게인·리미터 없음. 리미터는 원래도 한 번도 작동하지 않았다).
#   게이트 기준 0.008은 이전 기준 0.0008(+20dB 키우기 전)과 같은 크기다. 검사: python3 -I tools/click_check.py
# - mp4 소리는 AAC 320k. 192k·256k는 게이트가 열리는 말 시작 프레임 앞에 압축 번짐이 생겨 검사 기준(순간 잡음 0.5%)을 넘었다.

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GATE="${GATE:-0.008}"
AF="aresample=async=1:first_pts=0,agate=threshold=${GATE}:ratio=50:range=0:attack=2:release=60:detection=rms"
mkdir -p "$ROOT/recordings"

case "$1" in
  start)
    out="$ROOT/recordings/${2:-demo}_$(date +%Y%m%d_%H%M%S).webm"
    "$ADB" emu screenrecord start --time-limit 900 --fps 30 --bit-rate 8M "$out" >/dev/null
    echo "$out"
    ;;
  stop|convert)
    in="$2"
    if [ "$1" = "stop" ]; then
      "$ADB" emu screenrecord stop >/dev/null
      sleep 2
    fi
    base="${in%.webm}"
    if command -v ffmpeg >/dev/null; then
      ffmpeg -v error -y -i "$in" -c:v libx264 -preset fast -crf 18 -pix_fmt yuv420p \
        -af "$AF" -c:a aac -b:a 320k "$base.mp4" \
        && echo "영상: $base.mp4"
      ffmpeg -v error -y -i "$in" -vn -af "$AF" -ar 48000 -c:a pcm_s16le "$base.wav" \
        && echo "소리: $base.wav"
      # 차량 음량을 올리지 않고 녹화했으면(맥 스피커 모드 등) 소리가 작다 → 알린다
      peak=$(ffmpeg -hide_banner -i "$base.wav" -af astats=measure_overall=Peak_level:measure_perchannel=none -f null - 2>&1 \
        | awk '/Peak level dB/ {print int($NF)}' | tail -1)
      if [ -n "$peak" ] && [ "$peak" -lt -15 ]; then
        echo "주의: 소리 최고값 ${peak}dBFS로 작습니다. bash demo/start_emulator.sh --record 로 켜고 다시 녹화하세요."
      fi
    else
      echo "ffmpeg가 없어 webm만 남깁니다: $in (brew install ffmpeg)"
    fi
    ;;
  *)
    echo "사용법: bash demo/record.sh start 이름 | stop 경로 | convert 경로"
    exit 1
    ;;
esac
