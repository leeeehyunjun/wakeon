"""시연 영상 편집 (BUILD 단계 9). 녹화본 하나를 2분 20초 안팎의 제출용 영상으로 만든다.

  python3 -I tools/video/edit_demo.py recordings/demo_<시각>.mp4            # 편집 + 인코딩
  python3 -I tools/video/edit_demo.py recordings/demo_<시각>.mp4 --plan     # 자를 곳·자막 시각만 출력

입력: 같은 실행의 녹화(.mp4 영상, .wav 소리)와 앱 로그(recordings/scenario_<시각>.log, 녹화 시작 바로 전에 생긴 것).
출력: recordings/final/wakeoneteam_Wakeon_demo.mp4, 같은 폴더의 scene_times.md(장면별 시작 시각)

하는 일
1. 앱 로그(장면 1의 reset부터)에서 말 시작·끝(tts_start·tts_spoken), 시연 대답(demo answer → listen demo_answer),
   경고음·알람(sound_play), 화면 전환(screen W-xx) 시각을 읽는다.
2. 녹화 소리에서 말소리가 있는 곳(10ms 단위)을 찾아 기기 시각과 영상 시각의 차이를 맞춘다.
   말마다 실제 소리가 시작·끝나는 곳도 소리에서 찾는다.
3. 장면표(scenes())대로 쓸 구간을 고른다. 경계는 말 사이 조용한 곳에만 둔다(앱 문장을 중간에 자르지 않음).
   빼는 것: 장면 6 앞 점심 대화, 출발 전 체크 3/4·4/4, 재출발 2/3·3/3, 기다리는 시간.
4. 자막(차 = 앱 대사, 나 = 운전자 대답, 편집 자막)과 표기 그림을 render_overlays.swift로 그리고 ffmpeg로 이어 붙인다.
   소리는 녹화 소리 그대로(배경 음악 없음). 구간 경계마다 12ms 페이드로 딸깍 소리를 막는다.
"""
import argparse
import array
import bisect
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FPS = 30
BIN = 0.01            # 소리 분석 단위(초)
ACTIVE_RMS = 150      # 16bit 기준. 녹화 소리는 게이트로 무음이 0이라 말소리와 잘 갈린다
HOLD_CAR = 0.6        # 앱 대사 자막을 말이 끝난 뒤 더 보이는 시간
DUR_ANSWER = 1.6      # 운전자 대답 자막 시간
DUR_ANSWER_NOTE = 2.6 # 응답 시간 자막이 붙은 대답
TITLE_S, END_S = 4.0, 5.0
NAV_RECT = [1288, 67, 632, 908]   # 1:1 창일 때 내비 자리(원본 [1718,90]-[2559,1300] × 0.75)


def hms(s):
    s = max(0.0, s)
    return f"{int(s // 60)}:{s % 60:05.2f}"


# ─────────────────────────────── 앱 로그
class Log:
    LINE = re.compile(r"^\d\d-\d\d (\d\d):(\d\d):(\d\d\.\d+)\s+(\d+)\s+\d+ [IWDE] Wakeon\s*: (.*)$")

    def __init__(self, path):
        rows = []
        for line in open(path, encoding="utf-8", errors="replace"):
            m = self.LINE.match(line.rstrip("\n"))
            if m:
                t = int(m[1]) * 3600 + int(m[2]) * 60 + float(m[3])
                rows.append((t, int(m[4]), m[5]))
        # 장면 1의 reset(시계 21:04)부터, 그 실행(같은 pid)만
        start = next(i for i, r in enumerate(rows) if "reset=true" in r[2] and "clock=21:04" in r[2])
        pid = rows[start][1]
        self.ev = [(t, msg) for t, p, msg in rows[start:] if p == pid]
        self.reset_t = self.ev[0][0]
        self._calls()
        self._answers()

    def find(self, pattern, after=None, n=1):
        rx = re.compile(pattern)
        for t, msg in self.ev:
            if after is not None and t < after:
                continue
            if rx.search(msg):
                n -= 1
                if n == 0:
                    return t
        raise SystemExit(f"로그에 없음: {pattern} (after {after})")

    def _calls(self):
        """한 번에 말한 문장들(tts_start 여러 줄, 같은 시각) → tts_spoken 하나."""
        self.calls = []
        open_call = None
        for t, msg in self.ev:
            m = re.match(r"tts_start vid=(\S+) .*?text=(.*)$", msg)
            if m:
                if open_call and abs(open_call["t"] - t) < 0.01 and open_call["end"] is None:
                    open_call["vids"].append(m[1]); open_call["texts"].append(m[2])
                else:
                    open_call = {"t": t, "vids": [m[1]], "texts": [m[2]], "end": None}
                    self.calls.append(open_call)
                continue
            m = re.match(r"tts_spoken vid=(\S+)", msg)
            if m and open_call and open_call["end"] is None and m[1] == ",".join(open_call["vids"]):
                open_call["end"] = t
        self.calls = [c for c in self.calls if c["end"] is not None]

    def call(self, vid, after=None):
        for c in self.calls:
            if vid in c["vids"] and (after is None or c["t"] >= after):
                return c
        raise SystemExit(f"말한 기록 없음: {vid}")

    def _answers(self):
        """시연 대답: 넣은 순서대로 듣기 창이 열릴 때 하나씩 쓴다. 대답 시각 = 듣기 시작 + 응답 시간."""
        queue, self.answers = [], []
        for t, msg in self.ev:
            m = re.match(r"demo answer=(.*) latencyMs=(\d+)$", msg)
            if m:
                queue.append(m[1]); continue
            m = re.match(r"listen demo_answer latency_ms=(\d+)", msg)
            if m and queue:
                text = queue.pop(0)
                if text != "__none__":
                    self.answers.append({"t": t + int(m[1]) / 1000, "text": text, "latency": int(m[1])})

    def answer(self, after):
        for a in self.answers:
            if a["t"] >= after:
                return a
        raise SystemExit(f"대답 기록 없음 (after {after})")

    def sound(self, name, after=None):
        return self.find(r"sound_play file=sounds/" + re.escape(name), after)


# ─────────────────────────────── 녹화 소리
class Audio:
    def __init__(self, wav):
        raw = subprocess.run(["ffmpeg", "-v", "error", "-i", wav, "-ac", "1", "-ar", "8000", "-f", "s16le", "-"],
                             check=True, capture_output=True).stdout
        x = array.array("h"); x.frombytes(raw)
        step = int(8000 * BIN)
        self.rms = []
        for i in range(0, len(x) - step + 1, step):
            seg = x[i:i + step]
            self.rms.append((sum(v * v for v in seg) / step) ** 0.5)
        self.active = [1 if r >= ACTIVE_RMS else 0 for r in self.rms]
        self.cum = [0]
        for a in self.active:
            self.cum.append(self.cum[-1] + a)
        self.duration = len(self.rms) * BIN

    def frac(self, a, b):
        """영상 시각 [a, b) 안에서 소리가 있는 비율."""
        i, j = max(0, int(a / BIN)), min(len(self.active), int(b / BIN))
        return (self.cum[j] - self.cum[i]) / (j - i) if j > i else 0.0

    def onset(self, a, b):
        """[a, b)에서 소리가 시작되는 곳. a에서 이미 다른 소리(알람 등)가 나고 있으면 그 소리가 끝난 뒤(50ms 이상 조용)부터 찾는다."""
        i, end = max(0, int(a / BIN)), min(len(self.active), int(b / BIN))
        if i < end and self.active[i]:
            quiet = 0
            while i < end and quiet < 5:
                quiet = quiet + 1 if not self.active[i] else 0
                i += 1
        for k in range(i, end):
            if self.active[k]:
                return k * BIN
        return None

    def offset(self, a, b):
        for i in range(min(len(self.active), int(b / BIN)) - 1, max(0, int(a / BIN)) - 1, -1):
            if self.active[i]:
                return (i + 1) * BIN
        return None


def sync(log, audio, video_name):
    """기기 시각 = 영상 시각 + D. 파일 이름(맥 시각)으로 어림잡고 말소리로 ±3초 안에서 맞춘다."""
    m = re.search(r"_(\d{8})_(\d\d)(\d\d)(\d\d)", video_name)
    host = int(m[2]) * 3600 + int(m[3]) * 60 + int(m[4])
    hours = round(((log.reset_t - host) % 86400) / 3600) % 24
    d0 = (host + hours * 3600) % 86400
    best = None
    for k in range(-300, 301):
        d = d0 + k * BIN
        score = 0.0
        for c in log.calls:
            a, b = c["t"] - d, c["end"] - d
            if a < 0 or b > audio.duration:
                continue
            score += audio.frac(a + 0.15, b - 0.1) * (b - a)       # 말하는 동안 소리가 있어야 하고
            score -= 2 * audio.frac(a - 0.6, a - 0.02) * 0.58     # 말 시작 직전엔 없어야 한다
        if best is None or score > best[0]:
            best = (score, d)
    return best[1], best[1] - d0


# ─────────────────────────────── 장면표
class Plan:
    def __init__(self, log, audio, d):
        self.log, self.audio, self.d = log, audio, d
        self.scenes = []      # (번호, 화면, 제목, [(a,b) 영상 시각])
        self.captions = []    # (영상 시각, 글, 출력 길이)
        self.extra = []       # 시연 신호로 넣지 않은 운전자 말(자막만): (영상 시각, 글)
        self.notes = {}       # 대답 시각 → 옆에 붙일 편집 자막

    def v(self, t):
        return t - self.d

    def on(self, c):
        """말 c의 실제 소리 시작(영상 시각)."""
        a = self.v(c["t"])
        return self.audio.onset(a, a + 4) or a

    def off(self, c):
        """말 c의 실제 소리 끝(영상 시각). 다음 말이 시작되기 전까지만 본다."""
        a, b = self.v(c["t"]), self.v(c["end"])
        nxt = [self.v(x["t"]) for x in self.log.calls if x["t"] > c["t"]]
        limit = min([b + 0.3] + nxt)
        return self.audio.offset(a, limit) or b

    def snd(self, t, length):
        """소리 파일 재생(기기 시각 t) → (소리 시작, 끝) 영상 시각."""
        a = self.v(t)
        o = self.audio.onset(a, a + 2) or a
        return o, o + length

    def scene(self, no, screen, title, *segs):
        self.scenes.append((no, screen, title, [(a, b) for a, b in segs]))


def scenes(p):
    L, v = p.log, p.v
    # 1 · W-02 홈: V-02 → "응, 부산까지."(1.0초) → 체크 시작
    v02 = L.call("V-02", L.reset_t)
    pc = L.find("precheck_start", L.reset_t)
    p.scene(1, "W-02", "홈 · 출발 전", (p.on(v02) - 0.35, v(pc)))
    # 2 · W-03 1/4: V-03 → "삼."
    qp1 = L.call("Q-P1", pc)
    p.scene(2, "W-03", "출발 전 체크 1/4 · 졸린 정도", (v(pc), v(qp1["t"])))
    # 3 · W-03 2/4: "7 더하기 5는요?" → "십이."(1.0초). 3/4·4/4는 뺀다
    qp2 = L.call("Q-P2", qp1["t"])
    a3 = L.answer(qp1["end"])
    p.notes[round(a3["t"], 3)] = "응답 1.0초 — 이 속도가 오늘의 ‘평소’"
    p.scene(3, "W-03", "출발 전 체크 2/4 · 정답형", (v(qp1["t"]), v(qp2["t"]) - 0.05))
    # 4 · W-03b 준비 완료: V-06
    v06 = L.call("V-06", qp2["t"])
    s03b = L.find(r"screen W-03b", v06["t"] - 1)
    p.scene(4, "W-03b", "준비 완료 · 기준선 1.0초", (min(v(s03b) + 0.05, p.on(v06) - 0.1), p.off(v06) + 1.2))
    # 5 · W-04 주행 대기(1:1 창으로 바뀐 뒤)
    s04 = L.find(r"screen W-04", v06["end"])
    p.scene(5, "W-04", "주행 대기 · 연속 1시간 42분", (v(s04) + 0.8, v(s04) + 5.3))
    p.captions.append((v(s04) + 0.8, "1시간 42분 뒤, 경부고속도로", 4.5))
    # 6 · W-05 강아지 줄기(점심 대화는 뺀다): "키우세요?" → "네, 한 마리요."(1.2초)
    pt1 = L.call("T-PET-1", s04)
    a6 = L.answer(pt1["end"])
    p.scene(6, "W-05", "대화 3/6 · 강아지 줄기", (p.on(pt1) - 0.35, v(a6["t"])))
    # 7 · W-05b 대답 인식 → "이름이 뭐예요?" → "보리요."(1.7초)
    paused = L.find(r"talk_paused", a6["t"])
    p.scene(7, "W-05b", "대답 인식 → 이름", (v(a6["t"]), v(paused)))
    # 8 · W-06 대화 멈춤(브레이크 + 방향지시등) 4초, 다시 말하기 전 기다림은 뺀다
    p.scene(8, "W-06", "대화 멈춤 · 브레이크 + 방향지시등", (v(paused), v(paused) + 4.0))
    # 9 · W-05 "보리는 몇 살이에요?" → "…세 살… 인가?"(2.8초)
    pt3 = L.call("T-PET-3", paused)
    a9 = L.answer(pt3["end"])
    p.notes[round(a9["t"], 3)] = "응답 2.8초 · 평소 1.0초"
    p.scene(9, "W-05", "대답이 늦어짐 (2.8초)", (p.on(pt3) - 0.45, v(a9["t"])))
    # 10 · W-07 의심 확인 → "아니, 괜찮아."
    v15 = L.call("V-15", pt3["end"])
    a10 = L.answer(v15["end"])
    p.scene(10, "W-07", "의심 확인", (v(a9["t"]), v(a10["t"])))
    # 11 · W-05 "알겠어요. 그럼 … 산책 좋아해요?" → 5초 무응답
    pt4 = L.call("T-PET-4", v15["end"])
    reprompt = L.find(r"^reprompt", pt4["end"])
    p.scene(11, "W-05", "'아니' → 하나만 더 (무응답)", (v(a10["t"]), v(reprompt)))
    p.captions.append((p.off(pt4) + HOLD_CAR, "… 5초 동안 대답이 없음", v(reprompt) - p.off(pt4) - HOLD_CAR))
    # 12 · W-07b "제 말 들리세요?" → 다시 무응답(기다림 2.5초만)
    v14 = L.call("V-14", reprompt)
    wl1 = L.find(r"warn_level level=1", v14["end"])
    p.scene(12, "W-07b", "무응답 · 한 번 더", (v(reprompt), p.off(v14) + 2.5))
    # 13 · W-08 알림음 1→2→3단계(소리 사이 기다림만 뺀다) → V-20
    w1 = p.snd(L.sound("warn_1", wl1), 1.54)
    w2 = p.snd(L.sound("warn_2", wl1), 1.86)
    w3 = p.snd(L.sound("warn_3", wl1), 3.27)
    v20 = L.call("V-20", wl1)
    s08 = L.find(r"screen W-08", wl1)
    p.scene(13, "W-08", "단계적 경고 1→3", (min(v(s08), w1[0]) - 0.3, w1[1] + 0.25), (w2[0] - 0.2, w2[1] + 0.25),
            (w3[0] - 0.2, w3[1] + 0.3), (p.on(v20) - 0.35, p.off(v20) + 0.3))
    # 14 · W-09 쉼터 제안 → "어… 응."(1.3초)
    yes = L.find(r"rest_offer_answer answer=yes", v20["end"])
    v21 = L.call("V-21", v20["end"])
    p.scene(14, "W-09", "쉼터 제안", (p.off(v20) + 0.3, v(v21["t"]) + 0.3), (p.on(v21) - 0.35, v(yes)))
    # 15 · W-10 경유지 추가(내비 응답 기다림은 뺀다) → V-24
    v24 = L.call("V-24", yes)
    p.scene(15, "W-10", "경유지 추가", (v(yes), v(yes) + 1.3), (p.on(v24) - 0.4, p.off(v24) + 0.5))
    # 16 · W-10b 1km 전
    v25 = L.call("V-25", v24["end"])
    p.scene(16, "W-10b", "1km 전", (p.on(v25) - 0.5, p.off(v25) + 0.6))
    # 17 · W-12 도착(2:0 창) → "응, 잘게."(1.2초)
    v30 = L.call("V-30", v25["end"])
    choice = L.find(r"rest_choice choice=rest", v30["end"])
    p.scene(17, "W-12", "도착 · 휴식 제안", (p.on(v30) - 0.4, v(choice)))
    # 18 · W-13 휴식: 15:00부터 줄어드는 타이머(말이 끝나면 곧 넘긴다) + "— 15분 뒤 —"
    rest = L.find(r"rest_started", choice)
    v33 = L.call("V-33", rest)
    p.scene(18, "W-13", "휴식 15분", (v(choice), p.off(v33) + 1.5))
    p.captions.append((p.off(v33) + HOLD_CAR, "— 15분 뒤 —", 1.5))
    # 19 · W-13b 기상 알람 1단계(앞 2.2초) → V-35 → 2단계 → "으음… 일어났어."(2.0초)
    done = L.find(r"rest_completed", v33["end"])
    a1 = p.snd(L.sound("alarm_1", done), 4.56)
    v35 = L.call("V-35", done)
    awake = L.find(r"rest_awake", v35["end"])
    # 대답 바로 뒤에 "등받이를 세워 주세요."(V-34)가 시작되므로 대답 0.1초 뒤에서 끊는다(알람 소리가 겹쳐 소리로는 못 가름)
    p.scene(19, "W-13b", "기상 알람", (v(done) - 0.05, a1[0] + 2.2), (p.on(v35) - 0.35, v(awake) + 0.1))
    # 20 · W-15 재출발 체크: V-50 + "12 빼기 4는요?" → "팔."(1.1초). 2/3·3/3은 뺀다
    v50 = L.call("V-50", awake)
    qr2 = L.call("Q-R2", v50["end"])
    p.scene(20, "W-15", "재출발 체크 1/3", (p.on(v50) - 0.4, v(qr2["t"]) - 0.05))
    # 21 · W-16 출발: V-52 (+ 운전자 "고마워." 자막만 — 앱은 반응하지 않아 시연 신호로 넣지 않음)
    v52 = L.call("V-52", qr2["t"])
    rr = L.find(r"recheck_result", qr2["t"])
    p.scene(21, "W-16", "출발 · 다음 쉼터", (max(p.on(v52) - 0.4, v(rr) + 0.05), p.off(v52) + 1.6))
    p.extra.append((p.off(v52) + 0.3, "고마워."))
    # 22 · W-04 다시 대기(1:1 창) · 연속 0분
    s04b = L.find(r"screen W-04", v52["end"])
    p.scene(22, "W-04", "다시 대기 · 연속 0분", (v(s04b) + 0.8, v(s04b) + 4.8))
    p.captions.append((v(s04b) + 0.8, "연속 주행 시간이 0부터", 4.0))


# ─────────────────────────────── 시각 옮기기·자막
def snap(t):
    return round(t * FPS) / FPS


def build_timeline(p):
    segs = []   # (원본 a, 원본 b, 출력 시작, 장면 번호)
    out = 0.0
    for no, _, _, ss in p.scenes:
        for a, b in ss:
            a, b = snap(a), snap(b)
            if b - a < 1 / FPS:
                continue
            if segs and a <= segs[-1][1] + 1e-6:              # 이어지거나 겹치면 한 구간으로
                pa, pb, po, pn = segs[-1]
                if b <= pb:
                    continue
                segs[-1] = (pa, b, po, pn)
                out += b - pb
            else:
                segs.append((a, b, out, no))
                out += b - a
    return segs, out


def to_out(segs, t):
    """원본 시각 → 출력 시각(빠진 곳이면 다음 구간 시작)."""
    for a, b, o, _ in segs:
        if t < a:
            return o
        if t <= b:
            return o + (t - a)
    return segs[-1][2] + segs[-1][1] - segs[-1][0]


def inside(segs, a, b):
    return any(sa - 1e-3 <= a and b <= sb + 1e-3 for sa, sb, _, _ in segs)


def subtitle_items(p, segs):
    items, warn = [], []
    calls = [c for c in p.log.calls]
    for c in calls:
        on, off = p.on(c), p.off(c)
        if not any(sa <= on <= sb for sa, sb, _, _ in segs):
            continue
        if not inside(segs, on - 0.05, off + 0.02):
            warn.append(f"문장이 잘림: {c['vids']} {hms(on)}~{hms(off)}")
        items.append({"kind": "차", "text": " ".join(c["texts"]), "a": to_out(segs, on), "b": to_out(segs, off) + HOLD_CAR})
    for ans in p.log.answers:
        t = p.v(ans["t"])
        if not any(sa + 0.2 <= t <= sb for sa, sb, _, _ in segs):
            continue
        note = p.notes.get(round(ans["t"], 3))
        a = to_out(segs, t)
        items.append({"kind": "나", "text": ans["text"], "note": note, "a": a, "b": a + (DUR_ANSWER_NOTE if note else DUR_ANSWER)})
    for t, text in p.extra:
        a = to_out(segs, t)
        items.append({"kind": "나", "text": text, "a": a, "b": a + DUR_ANSWER})
    for t, text, dur in p.captions:
        a = to_out(segs, t)
        items.append({"kind": "자막", "text": text, "a": a, "b": a + dur})
    # 같은 종류는 다음 것이 나오면 끝낸다
    for kind in ("차", "나", "자막"):
        same = sorted([i for i in items if i["kind"] == kind], key=lambda i: i["a"])
        for x, y in zip(same, same[1:]):
            x["b"] = min(x["b"], y["a"])
    return sorted(items, key=lambda i: i["a"]), warn


def overlay_track(items, total, nav_cover):
    """시각마다 보이는 줄(최대 2줄: 차 위, 나·자막 아래)을 정해 그림 목록과 길이를 만든다."""
    cuts = sorted({0.0, total} | {min(max(i["a"], 0), total) for i in items} | {min(max(i["b"], 0), total) for i in items})
    states, dropped = [], []
    order = {"차": 0, "나": 1, "자막": 2}
    for a, b in zip(cuts, cuts[1:]):
        if b - a < 1e-4:
            continue
        mid = (a + b) / 2
        act = sorted([i for i in items if i["a"] <= mid < i["b"]], key=lambda i: (order[i["kind"]], i["a"]))
        if len(act) > 2:
            dropped.append((a, [i["text"] for i in act[2:]]))
            act = act[:2]
        rows = tuple((i["kind"], i["text"], i.get("note") or "") for i in act)
        cover = tuple(nav_cover(mid)) if nav_cover(mid) else None
        if states and states[-1][2] == (rows, cover):
            states[-1] = (states[-1][0], b, states[-1][2])
        else:
            states.append((a, b, (rows, cover)))
    return states, dropped


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("video")
    ap.add_argument("--log")
    ap.add_argument("--plan", action="store_true")
    ap.add_argument("--out", default=os.path.join(ROOT, "recordings/final/wakeoneteam_Wakeon_demo.mp4"))
    ap.add_argument("--nav-cover", choices=["auto", "on", "off"], default="auto")
    args = ap.parse_args()

    video = os.path.abspath(args.video)
    wav = video[:-4] + ".wav"
    if not args.log:
        stamp = re.search(r"_(\d{8}_\d{6})", video)[1]
        logs = sorted(f for f in os.listdir(os.path.join(ROOT, "recordings")) if f.startswith("scenario_") and f[9:24] <= stamp)
        args.log = os.path.join(ROOT, "recordings", logs[-1])
    log = Log(args.log)
    audio = Audio(wav)
    d, shift = sync(log, audio, os.path.basename(video))
    p = Plan(log, audio, d)
    scenes(p)
    segs, body = build_timeline(p)
    items, warn = subtitle_items(p, segs)

    # 내비가 지도 서버에 붙지 않은 녹화면 1:1 창 장면의 내비 자리를 덮는다
    nav_ok = any("navi route_started" in m for t, m in log.ev) or any(
        "navi route_started" in line for line in open(args.log, encoding="utf-8", errors="replace"))
    cover_on = args.nav_cover == "on" or (args.nav_cover == "auto" and not nav_ok)
    portrait_out = []
    for no, screen, _, ss in p.scenes:
        if 5 <= no <= 16 or no == 22:
            portrait_out.append((to_out(segs, snap(ss[0][0])), to_out(segs, snap(ss[-1][1]))))
    nav_cover = (lambda t: NAV_RECT if cover_on and any(a <= t < b for a, b in portrait_out) else None)
    states, dropped = overlay_track(items, body, nav_cover)

    total = TITLE_S + body + END_S
    print(f"앱 로그: {os.path.relpath(args.log, ROOT)}  · 시각 맞춤: 파일 이름 기준 {shift:+.2f}초")
    print(f"내비: {'지도 연결됨(route_started)' if nav_ok else '연결 안 됨'} → 내비 가림 {'켬' if cover_on else '끔'}")
    print(f"본편 {body:.1f}초 + 타이틀 {TITLE_S:.0f} + 엔드 {END_S:.0f} = {total:.1f}초 ({hms(total)}) · 구간 {len(segs)}개")
    rows = []
    starts = [to_out(segs, snap(ss[0][0])) + TITLE_S for _, _, _, ss in p.scenes] + [TITLE_S + body]
    for k, (no, screen, title, ss) in enumerate(p.scenes):
        merged, base = [], ss[0][0]
        for a, b in sorted((max(a, base), b) for a, b in ss):
            if merged and a <= merged[-1][1] + 1e-6:
                merged[-1] = (merged[-1][0], max(merged[-1][1], b))
            else:
                merged.append((a, b))
        rows.append((no, screen, title, starts[k], starts[k + 1] - starts[k], merged))
    for no, screen, title, o, dur, ss in rows:
        src = " + ".join(f"{hms(a)}~{hms(b)}" for a, b in ss)
        print(f"  {no:>2} {screen:<6} {hms(o)}  {dur:4.1f}초  {title}   [원본 {src}]")
    for w in warn:
        print("  ⚠", w)
    for t, texts in dropped:
        print(f"  ⚠ {hms(t + TITLE_S)} 자막 3줄 → 뺌: {texts}")
    if args.plan:
        for i in items:
            print(f"    {hms(i['a'] + TITLE_S)}~{hms(i['b'] + TITLE_S)} {i['kind']} {i['text']}" + (f"  [{i['note']}]" if i.get("note") else ""))
        return

    out_dir = os.path.dirname(args.out)
    work = os.path.join(out_dir, "work")
    os.makedirs(work, exist_ok=True)
    # 그림
    images = [
        {"file": "title.png", "kind": "card", "small": "Wakeon · 시연 영상", "big": "금요일 밤 9시, 서울에서 부산 본가까지"},
        {"file": "end.png", "kind": "card", "order": "big-small", "big": "졸음을 진단하지 말고, 쉼터까지 데려가라",
         "small": "Wakeon · 경고 → 쉼터 → 휴식 → 재출발을 하나의 대화로"},
    ]
    names, concat = {}, []
    for a, b, key in states:
        if key not in names:
            names[key] = f"ov_{len(names):03d}.png"
            rows_, cover = key
            spec = {"file": names[key], "kind": "overlay", "rows": [{"speaker": k, "text": t, "note": n} for k, t, n in rows_]}
            if cover:
                spec["navCover"] = list(cover)
            images.append(spec)
        concat.append((names[key], b - a))
    with open(os.path.join(work, "overlays.json"), "w", encoding="utf-8") as f:
        json.dump({"images": images}, f, ensure_ascii=False)
    subprocess.run(["swift", os.path.join(ROOT, "tools/video/render_overlays.swift"), os.path.join(work, "overlays.json"), work], check=True)
    with open(os.path.join(work, "overlays.txt"), "w") as f:
        f.write("ffconcat version 1.0\n")
        for name, dur in concat:
            f.write(f"file '{name}'\nduration {dur:.4f}\n")
        f.write(f"file '{concat[-1][0]}'\n")

    # 1) 구간마다 잘라 1080p·30fps로 맞춘다(영상은 mp4, 소리는 wav에서 같은 시각). 경계에 12ms 페이드.
    seg_list = os.path.join(work, "segments.txt")
    with open(seg_list, "w") as f:
        f.write("ffconcat version 1.0\n")
        for k, (a, b, _, _) in enumerate(segs):
            dur = b - a
            seg = os.path.join(work, f"seg_{k:03d}.mkv")
            fc = (f"[0:v]setpts=PTS-STARTPTS,fps={FPS},scale=1920:1080:flags=lanczos,setsar=1,"
                  f"trim=duration={dur:.4f},setpts=PTS-STARTPTS,format=yuv420p[v];"
                  f"[1:a]aformat=sample_rates=48000:channel_layouts=stereo,asetpts=PTS-STARTPTS,"
                  f"apad=whole_dur={dur:.4f},atrim=duration={dur:.4f},afade=t=in:d=0.012,"
                  f"afade=t=out:st={max(0, dur - 0.012):.4f}:d=0.012[a]")
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", f"{a:.4f}", "-to", f"{b + 0.2:.4f}", "-i", video,
                            "-ss", f"{a:.4f}", "-to", f"{b:.4f}", "-i", wav, "-filter_complex", fc, "-map", "[v]", "-map", "[a]",
                            "-c:v", "libx264", "-preset", "veryfast", "-crf", "10", "-r", str(FPS),
                            "-c:a", "pcm_s16le", seg], check=True)
            f.write(f"file 'seg_{k:03d}.mkv'\n")
            print(f"\r  구간 자르기 {k + 1}/{len(segs)}", end="", flush=True)
    print()

    # 2) 타이틀 → 본편(구간 이어 붙임 + 자막 그림) → 엔드 카드
    cmd = ["ffmpeg", "-v", "error", "-stats", "-y",
           "-loop", "1", "-framerate", str(FPS), "-t", str(TITLE_S), "-i", os.path.join(work, "title.png"),
           "-loop", "1", "-framerate", str(FPS), "-t", str(END_S), "-i", os.path.join(work, "end.png"),
           "-f", "lavfi", "-t", str(TITLE_S), "-i", "anullsrc=r=48000:cl=stereo",
           "-f", "lavfi", "-t", str(END_S), "-i", "anullsrc=r=48000:cl=stereo",
           "-f", "concat", "-safe", "0", "-i", os.path.join(work, "overlays.txt"),
           "-f", "concat", "-safe", "0", "-i", seg_list]
    fc = [f"[5:v]setpts=PTS-STARTPTS[bv]",
          f"[5:a]asetpts=PTS-STARTPTS[ba]",
          f"[4:v]fps={FPS},format=rgba,setpts=PTS-STARTPTS[ov]",
          "[bv][ov]overlay=0:0:eof_action=pass:format=auto,format=yuv420p,setsar=1[body]",
          f"[0:v]fps={FPS},format=yuv420p,setsar=1[tv]",
          f"[1:v]fps={FPS},format=yuv420p,setsar=1[ev]",
          "[tv][2:a][body][ba][ev][3:a]concat=n=3:v=1:a=1[outv][outa]"]
    cmd += ["-filter_complex", ";".join(fc), "-map", "[outv]", "-map", "[outa]",
            "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-profile:v", "high", "-pix_fmt", "yuv420p", "-r", str(FPS),
            "-c:a", "aac", "-b:a", "320k", "-ar", "48000", "-movflags", "+faststart", args.out]
    subprocess.run(cmd, check=True)

    # 장면별 시작 시각 표
    md = os.path.join(out_dir, "scene_times.md")
    with open(md, "w", encoding="utf-8") as f:
        f.write(f"# 시연 영상 장면별 시작 시각\n\n영상: `{os.path.relpath(args.out, ROOT)}` · 길이 {hms(total)} · 원본 녹화 `{os.path.relpath(video, ROOT)}`\n\n")
        f.write("| 장면 | 화면 | 시작 | 길이 | 내용 | 원본 구간 |\n| --- | --- | --- | --- | --- | --- |\n")
        f.write(f"| 0 | 타이틀 | 0:00.00 | {TITLE_S:.1f}초 | Wakeon · 시연 영상 / 금요일 밤 9시, 서울에서 부산 본가까지 | — |\n")
        for no, screen, title, o, dur, ss in rows:
            src = " + ".join(f"{hms(a)}~{hms(b)}" for a, b in ss)
            f.write(f"| {no} | {screen} | {hms(o)} | {dur:.1f}초 | {title} | {src} |\n")
        f.write(f"| 23 | 엔드 카드 | {hms(TITLE_S + body)} | {END_S:.1f}초 | 졸음을 진단하지 말고, 쉼터까지 데려가라 | — |\n")
    print(f"완성: {args.out}\n장면표: {md}")


if __name__ == "__main__":
    main()
