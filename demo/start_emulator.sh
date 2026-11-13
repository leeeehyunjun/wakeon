#!/bin/bash
# Pleos 에뮬레이터를 소리가 덜 깨지는 설정으로 켠다.
#   bash demo/start_emulator.sh            # 맥 스피커로 소리 재생 (시연·확인용)
#   bash demo/start_emulator.sh --mic      # 맥 마이크를 에뮬레이터에 연결(실제 음성 인식을 시험할 때만)
#   bash demo/start_emulator.sh --wav 파일  # 소리를 스피커 대신 WAV 파일로 저장 (깨짐 진단용)
#
# 2026-11-13 진단(docs/DECISIONS.md, docs/PLEOS_NOTES.md 10·11장):
# - 맥 CoreAudio로 내보내는 버퍼가 작아(기본 512프레임 × 4개) 재생이 끊긴다 → 버퍼를 키운다.
# - 마이크가 열리면 오디오가 입출력 동시 모드가 되어 출력에 잡음이 낀다 → 맥 마이크 입력은 끈다
#   (Extended Controls → Microphone → "Virtual microphone uses host audio input" 끔과 같음, 콘솔 `avd hostmicoff`).
# AVD는 6코어·6GB로 올려 두었다.

EMULATOR="${EMULATOR:-$HOME/Library/Android/sdk/emulator/emulator}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
AVD="${AVD:-Pleos_Connect_v2.1.0}"
MIC=off
EXTRA=()

case "$1" in
  --wav)
    [ -n "$2" ] || { echo "사용법: bash demo/start_emulator.sh --wav 파일"; exit 1; }
    export QEMU_AUDIO_DRV=wav
    export QEMU_WAV_PATH="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
    echo "소리를 $QEMU_WAV_PATH 에 저장합니다(스피커로는 나오지 않음)."
    ;;
  --mic)
    MIC=on
    EXTRA+=(-allow-host-audio)
    ;;
esac

if [ -z "$QEMU_AUDIO_DRV" ]; then
  export QEMU_COREAUDIO_BUFFER_SIZE="${QEMU_COREAUDIO_BUFFER_SIZE:-2048}"
  export QEMU_COREAUDIO_BUFFER_COUNT="${QEMU_COREAUDIO_BUFFER_COUNT:-8}"
  echo "CoreAudio 버퍼 ${QEMU_COREAUDIO_BUFFER_SIZE}프레임 × ${QEMU_COREAUDIO_BUFFER_COUNT}개, 맥 마이크 입력 ${MIC}."
fi

"$EMULATOR" -avd "$AVD" -netdelay none -netspeed full -no-snapstorage "${EXTRA[@]}" &
EMU_PID=$!

# 부팅이 끝나면 맥 마이크 입력을 끈다(기본값도 꺼짐이지만 확실히 한다).
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
if [ "$MIC" = "off" ]; then
  "$ADB" emu avd hostmicoff >/dev/null && echo "부팅 완료 · 맥 마이크 입력 꺼짐(avd hostmicoff)"
else
  "$ADB" emu avd hostmicon >/dev/null && echo "부팅 완료 · 맥 마이크 입력 켜짐(avd hostmicon)"
fi

wait $EMU_PID
