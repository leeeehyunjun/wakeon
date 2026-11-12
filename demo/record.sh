#!/bin/bash
# 시연 녹화: 에뮬레이터 자체 화면 녹화(화면 + 기기 안 소리)를 쓴다.
#   bash demo/record.sh start 이름   → recordings/이름_<시각>.webm 녹화 시작, 경로를 출력
#   bash demo/record.sh stop 경로     → 녹화를 멈추고 같은 이름의 .mp4(영상+소리)와 .wav(소리만)를 만든다
#
# 왜 이 방식인가(2026-11-13, docs/DECISIONS.md):
# - scrcpy는 이 차량용 에뮬레이터의 소리를 녹음하지 못한다(소리가 차량용 출력 버스로만 나가 무음으로 녹음됨).
# - 에뮬레이터 자체 녹화는 맥 스피커로 나가기 전의 소리를 담아, 스피커에서 깨져 들려도 녹화본은 깨끗하다.
# - 녹화 소리는 무음 구간을 건너뛰고 시각만 기록하므로, 변환할 때 빈 곳을 무음으로 채워 영상과 맞춘다.

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/recordings"

case "$1" in
  start)
    out="$ROOT/recordings/${2:-demo}_$(date +%Y%m%d_%H%M%S).webm"
    "$ADB" emu screenrecord start --time-limit 900 --fps 30 --bit-rate 8M "$out" >/dev/null
    echo "$out"
    ;;
  stop)
    in="$2"
    "$ADB" emu screenrecord stop >/dev/null
    sleep 2
    base="${in%.webm}"
    if command -v ffmpeg >/dev/null; then
      ffmpeg -v error -y -i "$in" -c:v libx264 -preset fast -crf 18 -pix_fmt yuv420p \
        -af "aresample=async=1:first_pts=0,loudnorm=I=-16:TP=-1.5" -c:a aac -b:a 192k "$base.mp4" \
        && echo "영상: $base.mp4"
      ffmpeg -v error -y -i "$in" -vn -af "aresample=async=1:first_pts=0,loudnorm=I=-16:TP=-1.5" -ar 48000 -c:a pcm_s16le "$base.wav" \
        && echo "소리: $base.wav"
    else
      echo "ffmpeg가 없어 webm만 남깁니다: $in (brew install ffmpeg)"
    fi
    ;;
  *)
    echo "사용법: bash demo/record.sh start 이름 | stop 경로"
    exit 1
    ;;
esac
