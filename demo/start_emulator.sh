#!/bin/bash
# Pleos 에뮬레이터를 소리가 덜 깨지는 설정으로 켠다.
#   bash demo/start_emulator.sh            # 맥 스피커로 소리 재생 (시연·확인용)
#   bash demo/start_emulator.sh --wav 파일  # 소리를 스피커 대신 WAV 파일로 저장 (깨짐 진단용)
#
# 원인(2026-11-13 진단): 에뮬레이터 안에서 만든 소리는 멀쩡하고(WAV로 받아 원본과 비교),
# 맥 CoreAudio로 내보내는 버퍼가 작아(기본 512프레임 × 4개) 재생이 끊긴다.
# → QEMU CoreAudio 버퍼를 키운다. AVD는 6코어·6GB로 올려 두었다(docs/DECISIONS.md).

EMULATOR="${EMULATOR:-$HOME/Library/Android/sdk/emulator/emulator}"
AVD="${AVD:-Pleos_Connect_v2.1.0}"

if [ "$1" = "--wav" ] && [ -n "$2" ]; then
  export QEMU_AUDIO_DRV=wav
  export QEMU_WAV_PATH="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
  echo "소리를 $QEMU_WAV_PATH 에 저장합니다(스피커로는 나오지 않음)."
else
  export QEMU_COREAUDIO_BUFFER_SIZE="${QEMU_COREAUDIO_BUFFER_SIZE:-2048}"
  export QEMU_COREAUDIO_BUFFER_COUNT="${QEMU_COREAUDIO_BUFFER_COUNT:-8}"
  echo "CoreAudio 버퍼 ${QEMU_COREAUDIO_BUFFER_SIZE}프레임 × ${QEMU_COREAUDIO_BUFFER_COUNT}개로 켭니다."
fi

exec "$EMULATOR" -avd "$AVD" -netdelay none -netspeed full -no-snapstorage
