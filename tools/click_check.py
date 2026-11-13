"""순간 잡음·배경 잡음 검사 (1초 단위). 시연 녹화 소리가 깨끗한지 숫자로 본다.

사용:
  python3 -I tools/click_check.py 소리.wav                 # 1초마다 표와 합격 여부
  python3 -I tools/click_check.py 소리.wav --focus 0-6,48-68,81-105   # 지정 구간만 따로 요약

정의(16bit 정수 기준, 20ms 프레임):
- 소리 프레임: 프레임 RMS ≥ VOICED_RMS.
- 순간 잡음: 소리 프레임 안에서 2차 차분 |x[n]-2x[n-1]+x[n-2]|가 그 프레임 2차 차분의 robust 크기(중앙값 × 1.4826)의
  CLICK_K배를 넘고 CLICK_MIN 이상인 샘플(파형이 갑자기 꺾이는 곳). ±NEAR_ZERO 이하 무음은 기준 계산에서 뺀다.
  비율 = 순간 잡음 샘플 / 그 초의 소리 프레임 샘플.
- 무음 구간 배경 잡음: 말 사이 조용한 구간(프레임 RMS < GAP_RMS가 GAP_MIN_FRAMES 이상 이어짐)의 안쪽
  (양 끝 GAP_EDGE_FRAMES 프레임 = 80ms 뺌) RMS. 말끝이 줄어드는 꼬리는 빼고 "말 사이"만 잰다. 조용한 구간이 없는 초는 표시하지 않음.
기준: 순간 잡음 ≤ 0.5%, 무음 RMS ≤ 2.

보정(2026-11-13): 이전 녹음 recordings/voice_check_20261113_080926.wav에서 이현준이 귀로 찾은 잡음 구간(0-6, 48-68, 81-105초)과
나머지를 비교해 정했다. 순간 잡음은 깨끗한 말소리 평균 0.008%, 잡음 구간 평균 0.053%(약 6배). 무음 RMS는 깨끗한 구간 거의 0, 잡음 구간 5~14.
"""
import math
import struct
import sys
import wave

FRAME_S = 0.02
VOICED_RMS = 60
GAP_RMS = 40
GAP_MIN_FRAMES = 10
GAP_EDGE_FRAMES = 4
CLICK_K = 24.0
CLICK_MIN = 512
NEAR_ZERO = 4
LIMIT_CLICK = 0.5  # %
LIMIT_SILENCE = 2.0


def load(path):
    w = wave.open(path, "rb")
    ch, sw, rate, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
    if sw != 2:
        raise SystemExit("16bit WAV만 읽습니다.")
    data = struct.unpack("<%dh" % (n * ch), w.readframes(n))
    if ch == 1:
        return list(data), rate
    return [sum(data[i:i + ch]) // ch for i in range(0, len(data), ch)], rate


def analyze(x, rate):
    flen = int(rate * FRAME_S)
    frames = [x[i:i + flen] for i in range(0, len(x) - flen, flen)]
    rms = [math.sqrt(sum(v * v for v in f) / flen) for f in frames]
    # 말 사이 조용한 구간의 안쪽 프레임
    gap = [False] * len(frames)
    i = 0
    while i < len(frames):
        if rms[i] < GAP_RMS:
            j = i
            while j < len(frames) and rms[j] < GAP_RMS:
                j += 1
            if j - i >= GAP_MIN_FRAMES:
                for k in range(i + GAP_EDGE_FRAMES, j - GAP_EDGE_FRAMES):
                    gap[k] = True
            i = j
        else:
            i += 1
    per_sec = {}
    for idx, f in enumerate(frames):
        sec = idx * flen // rate
        st = per_sec.setdefault(sec, {"voiced": 0, "clicks": 0, "sil_sq": 0.0, "sil_n": 0})
        if gap[idx]:
            st["sil_sq"] += sum(v * v for v in f)
            st["sil_n"] += flen
        if rms[idx] < VOICED_RMS:
            continue
        # 무음(±NEAR_ZERO 이하, 압축 소리의 0 근처 포함) 구간은 빼고 잰다. 무음 → 말소리 시작 프레임에서 기준이
        # 0에 가깝게 잡혀 정상적인 말 시작을 순간 잡음으로 세는 착시를 막는다(2026-11-13 보정).
        d2 = [abs(f[k] - 2 * f[k - 1] + f[k - 2]) for k in range(2, flen)
              if max(abs(f[k]), abs(f[k - 1]), abs(f[k - 2])) > NEAR_ZERO]
        if not d2:
            continue
        scale = sorted(d2)[len(d2) // 2] * 1.4826
        thr = max(CLICK_K * scale, CLICK_MIN)
        st["voiced"] += len(d2)
        st["clicks"] += sum(1 for v in d2 if v > thr)
    rows = []
    for sec in sorted(per_sec):
        st = per_sec[sec]
        click = 100.0 * st["clicks"] / st["voiced"] if st["voiced"] else 0.0
        sil = math.sqrt(st["sil_sq"] / st["sil_n"]) if st["sil_n"] else None
        rows.append((sec, click, sil, st["voiced"] > 0))
    return rows


def ok(click, sil):
    return click <= LIMIT_CLICK and (sil is None or sil <= LIMIT_SILENCE)


def summarize(rows, label):
    clicks = [r[1] for r in rows if r[3]]
    sils = [r[2] for r in rows if r[2] is not None]
    bad = [r[0] for r in rows if not ok(r[1], r[2])]
    print(f"{label}: {len(rows)}초 · 순간 잡음 최대 {max(clicks, default=0):.3f}% 평균 {sum(clicks) / max(1, len(clicks)):.3f}% · "
          f"무음 RMS 최대 {max(sils, default=0):.1f} 평균 {sum(sils) / max(1, len(sils)):.1f} · 기준 넘은 초 {len(bad)}개"
          + (f" ({', '.join(f'{s}s' for s in bad[:15])}{' …' if len(bad) > 15 else ''})" if bad else ""))
    return not bad


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    path = sys.argv[1]
    focus = []
    if "--focus" in sys.argv:
        spec = sys.argv[sys.argv.index("--focus") + 1]
        focus = [tuple(int(v) for v in part.split("-")) for part in spec.split(",")]
    x, rate = load(path)
    rows = analyze(x, rate)
    print(f"{path} · {rate}Hz · {len(x) / rate:.1f}초 · 기준: 순간 잡음 ≤ {LIMIT_CLICK}%, 무음 RMS ≤ {LIMIT_SILENCE}")
    print(" 초 | 순간잡음% | 무음RMS | 판정")
    for sec, click, sil, voiced in rows:
        sil_s = f"{sil:7.1f}" if sil is not None else "      -"
        click_s = f"{click:8.3f}" if voiced else "       -"
        print(f"{sec:3d} | {click_s} | {sil_s} | {'OK' if ok(click, sil) else '넘음'}")
    all_ok = summarize(rows, "전체")
    for a, b in focus:
        summarize([r for r in rows if a <= r[0] < b], f"구간 {a}-{b}초")
    print("결과:", "합격" if all_ok else "불합격")


if __name__ == "__main__":
    main()
