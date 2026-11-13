#!/bin/bash
# Pleos 에뮬레이터를 소리가 덜 깨지는 설정으로 켠다.
#   bash demo/start_emulator.sh            # 맥 스피커로 소리 재생 (확인·연습용)
#   bash demo/start_emulator.sh --record   # 시연 녹화용: 맥 스피커로 내보내지 않음 → 녹화 소리가 깨끗하다
#   bash demo/start_emulator.sh --mic      # 맥 마이크를 에뮬레이터에 연결(실제 음성 인식을 시험할 때만)
#   bash demo/start_emulator.sh --wav 파일  # 소리를 스피커 대신 WAV 파일로 저장 (깨짐 진단용)
#
# 2026-11-13 진단(docs/DECISIONS.md, docs/PLEOS_NOTES.md 10~12장):
# - 맥 CoreAudio로 내보내는 버퍼가 작아(기본 512프레임 × 4개) 재생이 끊긴다 → 버퍼를 키운다.
# - 맥 스피커로 재생하면서 녹화하면 목소리 안 순간 잡음(갈라짐)이 4~5배 많다(같은 흐름·같은 음량에서
#   tools/click_check.py 평균 0.040% 대 0.009%). 맥 오디오로 내보내는 단계가 원인으로 보이나 정확한 지점은 못 찾았다
#   (기기 안 음성 버스 데이터 부족 카운터는 두 방식이 비슷: 3,071 대 3,028). 그래서 녹화할 때는 --record로 켠다:
#   소리를 맥 스피커 대신 파일(recordings/emu_pcm_<시각>.wav, 기기 출력 그대로)로 내보낸다.
#   녹화 중에는 맥 스피커로 소리가 나오지 않는다. 녹화본(record.sh)으로 확인한다.
# - 차량 음량(음성·알람 그룹)을 20 → 40(최대 45)으로 올린다. 20이면 기기 출력이 −26dBFS로 작아 녹화 뒤에 +20dB를
#   키워야 했다. 40이면 녹화 원본이 바로 −6.6dBFS라 키울 필요가 없다. 음량 차이는 TTS·경고음이 같아 서로의 비율은 그대로.
# - 마이크가 열리면 오디오가 입출력 동시 모드가 되어 출력에 잡음이 낄 수 있다 → 맥 마이크 입력은 끈다
#   (Extended Controls → Microphone → "Virtual microphone uses host audio input" 끔과 같음, 콘솔 `avd hostmicoff`).
# AVD는 6코어·6GB로 올려 두었다.

EMULATOR="${EMULATOR:-$HOME/Library/Android/sdk/emulator/emulator}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
AVD="${AVD:-Pleos_Connect_v2.1.0}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VOLUME_PERCENT="${VOLUME_PERCENT:-89}"   # 0~100. 89% = 음량 단계 40/45
MIC=off
EXTRA=()

case "$1" in
  --record)
    mkdir -p "$ROOT/recordings"
    export QEMU_AUDIO_DRV=wav
    export QEMU_WAV_PATH="$ROOT/recordings/emu_pcm_$(date +%Y%m%d_%H%M%S).wav"
    echo "녹화 모드: 소리를 맥 스피커 대신 $QEMU_WAV_PATH 로 내보냅니다(스피커로는 나오지 않음)."
    ;;
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

# 맥이 IPv6 전용 핫스폿 등에 있어 에뮬레이터가 DNS 목록을 못 읽으면("Failed to retrieve DNS servers list") 직접 지정한다.
#   DNS_SERVER=8.8.8.8,1.1.1.1 bash demo/start_emulator.sh --record
[ -n "$DNS_SERVER" ] && EXTRA+=(-dns-server "$DNS_SERVER")

if [ -z "$QEMU_AUDIO_DRV" ]; then
  export QEMU_COREAUDIO_BUFFER_SIZE="${QEMU_COREAUDIO_BUFFER_SIZE:-2048}"
  export QEMU_COREAUDIO_BUFFER_COUNT="${QEMU_COREAUDIO_BUFFER_COUNT:-8}"
  echo "CoreAudio 버퍼 ${QEMU_COREAUDIO_BUFFER_SIZE}프레임 × ${QEMU_COREAUDIO_BUFFER_COUNT}개, 맥 마이크 입력 ${MIC}."
fi

"$EMULATOR" -avd "$AVD" -netdelay none -netspeed full -no-snapstorage "${EXTRA[@]}" &
EMU_PID=$!

# 부팅이 끝나면 맥 마이크 입력을 끄고(기본값도 꺼짐이지만 확실히 한다) 차량 음량을 맞춘다.
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
if [ "$MIC" = "off" ]; then
  "$ADB" emu avd hostmicoff >/dev/null && echo "부팅 완료 · 맥 마이크 입력 꺼짐(avd hostmicoff)"
else
  "$ADB" emu avd hostmicon >/dev/null && echo "부팅 완료 · 맥 마이크 입력 켜짐(avd hostmicon)"
fi
# 음량 그룹 2 = 음성(bus2_voice_command_out, TTS), 3 = 알람(bus5_alarm_out, 경고음·알람). 앱이 아니라 adb(개발 환경)에서 바꾼다.
for group in 2 3; do
  "$ADB" shell cmd car_service set-group-volume 0 "$group" "$VOLUME_PERCENT" >/dev/null
done
echo "차량 음량(음성·알람) ${VOLUME_PERCENT}% 로 맞춤"

wait $EMU_PID
