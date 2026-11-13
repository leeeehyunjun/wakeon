#!/bin/bash
# release APK 만들기(BUILD 단계 7): 서명된 release 빌드 → 서명 확인 → apk/wakeoneteam_Wakeon_release.apk
#   bash tools/release.sh
# 서명 키 정보는 저장소 루트의 keystore.properties(저장소에 올리지 않음)에서 읽는다.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
OUT="$ROOT/apk/wakeoneteam_Wakeon_release.apk"

if [ ! -f keystore.properties ]; then
  echo "keystore.properties가 없습니다. 저장소 루트에 만들고 storeFile·storePassword·keyAlias·keyPassword를 적어 주세요."
  exit 1
fi

./gradlew -q :app:clean :app:assembleRelease || { echo "release 빌드 실패"; exit 1; }
APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
  echo "서명된 APK가 없습니다(app-release-unsigned.apk만 있으면 keystore.properties 값을 확인)."
  exit 1
fi

APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
if [ -n "$APKSIGNER" ]; then
  "$APKSIGNER" verify --print-certs "$APK" | grep -E "Signer #1 certificate (DN|SHA-256)" || { echo "서명 확인 실패"; exit 1; }
fi

mkdir -p "$ROOT/apk"
cp "$APK" "$OUT"
echo "완료: $OUT ($(du -h "$OUT" | cut -f1), sha256 $(shasum -a 256 "$OUT" | cut -c1-16)…)"
