"""경고음·기상 알람 소리 파일을 만든다 (BUILD 단계 4-3, SPEC F7·F10).

- 경고 warn_1~3: 부드러운 차임이 단계마다 조금씩 커지고 음 수가 늘어난다. 처음부터 최대 음량 금지(AC-7.1).
- 알람 alarm_1~3: 천천히 커지는 아르페지오.
- 48kHz · 16bit · 모노, 앞뒤 10ms 페이드(시작·끝의 딸깍 소리 방지).
- 3단계 경고음은 1·2단계 규칙으로 만든 길이의 1.5배(2026-11-13 PM 결정).
실행: python3 -I tools/make_sounds.py  →  app/src/main/assets/sounds/*.wav
"""
import math
import os
import struct
import wave

RATE = 48000
FADE_S = 0.010
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "sounds")


def chime(freq, length_s, amp):
    """종소리: 기음 + 배음, 빠른 시작과 지수 감쇠."""
    n = int(RATE * length_s)
    out = []
    for i in range(n):
        t = i / RATE
        attack = min(1.0, t / 0.008)
        decay = math.exp(-t * 4.0)
        v = (math.sin(2 * math.pi * freq * t)
             + 0.35 * math.sin(2 * math.pi * freq * 2.0 * t)
             + 0.12 * math.sin(2 * math.pi * freq * 3.01 * t))
        out.append(amp * attack * decay * v / 1.47)
    return out


def mix(notes, total_s):
    """notes: (시작 초, 샘플 목록)."""
    buf = [0.0] * int(RATE * total_s)
    for start, samples in notes:
        s = int(RATE * start)
        for i, v in enumerate(samples):
            if s + i < len(buf):
                buf[s + i] += v
    peak = max(1e-9, max(abs(v) for v in buf))
    if peak > 0.98:
        buf = [v * 0.98 / peak for v in buf]
    return buf


def fade(buf):
    """앞뒤 10ms 선형 페이드."""
    n = int(RATE * FADE_S)
    for i in range(min(n, len(buf))):
        g = i / n
        buf[i] *= g
        buf[-1 - i] *= g
    return buf


def write(name, buf):
    buf = fade(buf)
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 32767)) for v in buf))
    print(path, f"{len(buf) / RATE:.1f}s")


def main():
    # 경고: 1단계 낮고 작게(두 음) → 3단계 조금 높고 크게(네 음)
    warn = {
        1: (0.22, [784, 659]),
        2: (0.45, [880, 740, 880]),
        3: (0.80, [988, 784, 988, 784]),
    }
    step, tail = 0.32, 0.9
    for level, (amp, freqs) in warn.items():
        total = step * len(freqs) + tail
        if level == 3:
            # 3단계는 1.5배 길이. 같은 두 음을 번갈아 길이만큼 채운다.
            total *= 1.5
            count = int((total - tail) / step) + 1
            freqs = [freqs[i % 2] for i in range(count)]
        notes = [(i * step, chime(f, 1.0, amp)) for i, f in enumerate(freqs)]
        write(f"warn_{level}.wav", mix(notes, total))

    # 기상 알람: 도-미-솔-도 아르페지오, 단계마다 커지고 빨라진다
    alarm = {1: (0.20, 0.42), 2: (0.45, 0.34), 3: (0.80, 0.26)}
    freqs = [523, 659, 784, 1047]
    for level, (amp, step) in alarm.items():
        notes = [(i * step, chime(f, 1.4, amp)) for i, f in enumerate(freqs * 2)]
        write(f"alarm_{level}.wav", mix(notes, step * 8 + 1.2))


if __name__ == "__main__":
    main()
