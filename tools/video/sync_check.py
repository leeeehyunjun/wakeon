"""완성본 자막 싱크 검사 (BUILD 단계 9). 자막이 화면에 실제로 뜬 시각과 소리가 실제로 시작된 시각을 문장마다 잰다.

  python3 -I tools/video/sync_check.py recordings/final/wakeoneteam_Wakeon_demo.mp4 [--subs 자막.json] [--out 표.md]

- 자막 목록: edit_demo.py가 남긴 recordings/final/work/subtitles.json (출력 시각, 종류 차·나·자막, 문장)
- 자막이 뜬 시각: 완성본 영상의 자막 띠(아래 105px)를 프레임마다 비교해 바뀐 프레임을 찾는다(1/30초 단위).
  자막 목록의 예정 시각 ±0.25초 안에서 가장 가까운 바뀜을 그 자막의 실제 시작으로 본다.
- 소리 시작: 완성본 소리(AAC)를 10ms 단위로 보고, 예정 시각에 가장 가까운 '60ms 이상 조용 → 소리' 전환.
- 차(앱 대사): 자막 시작 − 소리 시작. 나(운전자 대답, 목소리 없음): 자막 시작 − 대답 시각(앱 로그).
- 기준: 차이 0.1초 이하. 넘는 문장이 있으면 종료 코드 1.
"""
import argparse
import array
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FPS = 30
BIN = 0.01
ACTIVE_RMS = 150
LIMIT = 0.1
BAND = (0, 975, 1920, 105)      # 자막 띠 x, y, w, h (1920×1080)
SMALL = (320, 18)               # 비교용으로 줄인 크기
CHANGE = 0.8                    # 프레임 사이 평균 차이(0~255)가 이보다 크면 자막이 바뀐 것


def audio_activity(video):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", video, "-vn", "-ac", "1", "-ar", "8000", "-f", "s16le", "-"],
                         check=True, capture_output=True).stdout
    x = array.array("h")
    x.frombytes(raw)
    step = int(8000 * BIN)
    act = []
    for i in range(0, len(x) - step + 1, step):
        seg = x[i:i + step]
        act.append(1 if (sum(v * v for v in seg) / step) ** 0.5 >= ACTIVE_RMS else 0)
    return act


def onset_near(act, t, before=0.6, after=0.8, quiet=6):
    i0, i1 = max(quiet, int((t - before) / BIN)), min(len(act), int((t + after) / BIN))
    best = None
    for k in range(i0, i1):
        if act[k] and not any(act[k - quiet:k]):
            if best is None or abs(k * BIN - t) < abs(best - t):
                best = k * BIN
    return best


def offset_after(act, t, limit=None, quiet=120):
    """t 이후 말이 끝나는 곳: 1.2초 이상 조용해지기 직전(한 대사 안의 문장 사이는 0.5초 쉼 + 합성 지연으로 1초 안). limit(다음 앱 대사) 전까지만 본다."""
    k = int(t / BIN)
    end = min(len(act), int(limit / BIN)) if limit is not None else len(act)
    last = None
    q = 0
    while k < end:
        if act[k]:
            last, q = k, 0
        else:
            q += 1
            if last is not None and q >= quiet:
                break
        k += 1
    return (last + 1) * BIN if last is not None else None


def band_changes(video):
    x, y, w, h = BAND
    sw, sh = SMALL
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", video, "-an", "-vf",
                          f"crop={w}:{h}:{x}:{y},scale={sw}:{sh}:flags=area,format=gray", "-f", "rawvideo", "-"],
                         check=True, capture_output=True).stdout
    n = sw * sh
    frames = [raw[i:i + n] for i in range(0, len(raw) - n + 1, n)]
    changes = []
    for k in range(1, len(frames)):
        a, b = frames[k - 1], frames[k]
        d = sum(abs(p - q) for p, q in zip(a, b)) / n
        if d > CHANGE:
            changes.append(k / FPS)
    return changes


APP = (675, 112, 562, 825)      # 1920×1080에서 앱 안쪽(원본 900,150,750,1100 × 0.75), 화면 전환 찾기용


def app_changes(video, t, before=0.6, after=0.6, thr=4.0):
    """t 근처에서 앱 화면이 크게 바뀐 프레임(차이가 가장 큰 프레임)의 시각."""
    x, y, w, h = APP
    a = max(0.0, t - before)
    raw = subprocess.run(["ffmpeg", "-v", "error", "-ss", f"{a:.3f}", "-t", f"{before + after:.3f}", "-i", video, "-an", "-vf",
                          f"fps={FPS},crop={w}:{h}:{x}:{y},scale=75:110:flags=area,format=gray", "-f", "rawvideo", "-"],
                         check=True, capture_output=True).stdout
    n = 75 * 110
    frames = [raw[i:i + n] for i in range(0, len(raw) - n + 1, n)]
    best = None
    for k in range(1, len(frames)):
        d = sum(abs(p - q) for p, q in zip(frames[k - 1], frames[k])) / n
        if d > thr and (best is None or d > best[1]):
            best = (a + k / FPS, d)
    return best[0] if best else None


def nearest(changes, t, tol=0.25):
    best = None
    for c in changes:
        if abs(c - t) <= tol and (best is None or abs(c - t) < abs(best - t)):
            best = c
    return best


def mmss(t):
    return f"{int(t // 60)}:{t % 60:05.2f}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("video")
    ap.add_argument("--subs")
    ap.add_argument("--out")
    args = ap.parse_args()
    video = os.path.abspath(args.video)
    subs = args.subs or os.path.join(os.path.dirname(video), "work", "subtitles.json")
    out = args.out or os.path.join(os.path.dirname(video), "subtitle_sync.md")
    plan = json.load(open(subs, encoding="utf-8"))
    items, cuts = plan["items"], sorted(plan.get("cuts", []))

    act = audio_activity(video)
    changes = band_changes(video)

    rows, bad = [], 0
    car_starts = sorted(i["a"] for i in items if i["kind"] == "차")
    for it in items:
        if it["kind"] not in ("차", "나"):
            continue
        shown = nearest(changes, it["a"])
        if it["kind"] == "차":
            ref = onset_near(act, it["a"])
            ref_name = "소리 시작"
            # 말 끝: 다음 앱 대사 전, 편집 도구가 본 말 끝(end_hint) + 0.4초 전까지(뒤이은 알람·경고음을 말로 세지 않게)
            nxt = next((a for a in car_starts if a > it["a"] + 0.05), None)
            limits = [x for x in (nxt - 0.05 if nxt else None, it["end_hint"] + 0.4 if "end_hint" in it else None) if x]
            end_ref = offset_after(act, ref, limit=min(limits) if limits else None) if ref is not None else None
        else:
            ref = it.get("ref", it["a"])
            ref_name = it.get("ref_name", "대답 시각(로그)")
            end_ref = None
        if shown is None or ref is None:
            diff = None
        else:
            diff = shown - ref
        ok = diff is not None and abs(diff) <= LIMIT + 1e-6
        bad += 0 if ok else 1
        shown_end = nearest(changes, it["b"])
        rows.append((it, shown, ref, ref_name, diff, ok, shown_end, end_ref))

    with open(out, "w", encoding="utf-8") as f:
        f.write("# 자막 싱크 표\n\n")
        f.write(f"영상: `{os.path.relpath(video, ROOT)}` · 검사: `python3 -I tools/video/sync_check.py`\n\n")
        f.write("- 자막 시작: 완성본 화면에서 자막 띠가 바뀐 프레임(1/30초 단위)\n")
        f.write("- 차(앱 대사): 기준 = 완성본 소리에서 말소리가 시작된 시각(10ms 단위)\n")
        f.write("- 나(운전자 대답, 목소리 없음): 기준 = 앱 로그의 대답 시각(듣기 시작 + 응답 시간)을 영상 시각으로 옮긴 값\n")
        f.write(f"- 기준: 차이 {LIMIT:.1f}초 이하 · 결과: {len(rows) - bad}/{len(rows)} 통과\n\n")
        f.write("| # | 종류 | 문장 | 자막 시작 | 기준 시각 | 차이(초) | 판정 | 자막 끝 | 말 끝 | 자막 끝 − 말 끝 |\n")
        f.write("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n")
        for k, (it, shown, ref, ref_name, diff, ok, shown_end, end_ref) in enumerate(rows, 1):
            text = it["text"].replace("|", "\\|")
            f.write(f"| {k} | {it['kind']} | {text} | {mmss(shown) if shown is not None else '못 찾음'} | "
                    f"{mmss(ref) if ref is not None else '못 찾음'} ({ref_name}) | "
                    f"{diff:+.3f} | {'✓' if ok else '✗'} | "
                    f"{mmss(shown_end) if shown_end is not None else '—'} | {mmss(end_ref) if end_ref is not None else '—'} | "
                    f"{f'{shown_end - end_ref:+.2f}' if shown_end is not None and end_ref is not None else '—'} |\n"
                    if diff is not None else
                    f"| {k} | {it['kind']} | {text} | {mmss(shown) if shown is not None else '못 찾음'} | "
                    f"{mmss(ref) if ref is not None else '못 찾음'} ({ref_name}) | — | ✗ | — | — | — |\n")
    # 운전자 자막 규칙(2026-11-14): 뜬 뒤 같은 컷에 0.8초 이상 머문다 / 다음 컷이나 다음 앱 말까지 남지 않는다.
    # 차 자막도 다음 컷까지 남지 않는지 같이 본다. 자막 시작·끝은 완성본 화면에서 잰 값(못 찾으면 계획 값).
    shown_of = {id(it): (shown, shown_end) for it, shown, _, _, _, _, shown_end, _ in rows}
    car_rows = sorted((shown if shown is not None else it["a"]) for it, shown, *_ in rows if it["kind"] == "차")
    stay_rows, linger_rows = [], []
    if cuts:
        for it, shown, *_rest in rows:
            st, en = shown_of[id(it)]
            st = st if st is not None else it["a"]
            en = en if en is not None else it["b"]
            next_cut = next((c for c in cuts if c > st + 1e-3), None)
            if it["kind"] == "나":
                stay = next_cut - st if next_cut is not None else None
                stay_rows.append((it, st, next_cut, stay, stay is not None and stay >= 0.8 - 1e-6))
                nxt_car = next((c for c in car_rows if c > st + 1e-3), None)
                limits = [x for x in (next_cut, nxt_car) if x is not None]
                limit = min(limits) if limits else None
                which = "다음 컷" if limit is not None and limit == next_cut else "다음 앱 말"
            else:
                limit, which = next_cut, "다음 컷"
            over = (en - limit) if limit is not None else None
            linger_rows.append((it, st, en, limit, which, over, over is None or over <= 1 / FPS / 2 + 1e-6))
        n_stay_bad = sum(1 for *_, ok in stay_rows if not ok)
        n_linger = sum(1 for *_, ok in linger_rows if not ok)
        with open(out, "a", encoding="utf-8") as f:
            f.write("\n## 운전자 자막이 뜬 뒤 컷이 바뀔 때까지\n\n")
            f.write(f"- 기준: 0.8초 이상 · 결과: {len(stay_rows) - n_stay_bad}/{len(stay_rows)} 통과\n\n")
            f.write("| # | 대답 | 자막 시작 | 다음 컷 | 머문 시간(초) | 판정 |\n| --- | --- | --- | --- | --- | --- |\n")
            for k, (it, st, nc, stay, ok) in enumerate(stay_rows, 1):
                f.write(f"| {k} | {it['text']} | {mmss(st)} | {mmss(nc) if nc is not None else '—'} | "
                        f"{f'{stay:.2f}' if stay is not None else '—'} | {'✓' if ok else '✗'} |\n")
            f.write("\n## 자막이 다음 컷·다음 앱 말까지 남는 곳\n\n")
            f.write("- 운전자 자막(나): 다음 컷이나 다음 앱 말(차) 자막이 뜨기 전에 내려가야 함. 앱 대사(차): 다음 컷 전에 내려가야 함\n")
            f.write(f"- 자막 끝은 완성본 화면에서 자막 띠가 바뀐 프레임 · 결과: 남는 곳 {n_linger}개\n\n")
            f.write("| # | 종류 | 문장 | 자막 시작 | 자막 끝 | 넘으면 안 되는 곳 | 시각 | 판정 |\n| --- | --- | --- | --- | --- | --- | --- | --- |\n")
            for k, (it, st, en, limit, which, over, ok) in enumerate(linger_rows, 1):
                text = it["text"].replace("|", "\\|")
                f.write(f"| {k} | {it['kind']} | {text} | {mmss(st)} | {mmss(en)} | {which if limit is not None else '—'} | "
                        f"{mmss(limit) if limit is not None else '—'} | {'✓' if ok else '✗ 남음'} |\n")
        print(f"운전자 자막 → 다음 컷: {len(stay_rows) - n_stay_bad}/{len(stay_rows)}개 0.8초 이상 · 자막 남는 곳 {n_linger}개")
        bad += n_stay_bad + n_linger

    # 화면 전환 ↔ 소리: 화면이 바뀌며 앱이 말을 시작하는 곳에서, 완성본의 (소리 시작 − 화면 바뀜)을 앱 로그의 (재생 시작 − 화면 전환)과 비교
    avp = os.path.join(os.path.dirname(subs), "av_events.json")
    if os.path.exists(avp):
        av = json.load(open(avp, encoding="utf-8"))
        res = []
        for e in av["events"]:
            fr = app_changes(video, e["frame"])
            snd = onset_near(act, fr + e["log_gap"], before=0.5, after=0.5) if fr is not None else None
            res.append((e, fr, snd, (snd - fr - e["log_gap"]) if fr is not None and snd is not None else None))
        vals = sorted(x for *_, x in res if x is not None)
        with open(out, "a", encoding="utf-8") as f:
            f.write(f"\n## 화면 전환 ↔ 소리\n\n편집에서 소리를 {av['shift']:.3f}초 당김(녹화본 측정: 화면이 소리보다 {av['lead']:.3f}초 앞섬).\n\n")
            f.write("- 화면 바뀜: 완성본에서 앱 화면이 크게 바뀐 프레임 · 소리 시작: 완성본 소리에서 그 대사가 시작된 곳\n")
            f.write("- 로그 간격: 앱 로그의 (재생 시작 − 화면 전환). 차이 = (소리 시작 − 화면 바뀜) − 로그 간격. 0이면 화면과 소리가 앱 안에서처럼 맞음\n")
            if vals:
                f.write(f"- {len(vals)}곳 · 차이 중앙값 {vals[len(vals) // 2]:+.3f}초 · 범위 {vals[0]:+.3f} ~ {vals[-1]:+.3f}초\n\n")
            f.write("| # | 화면 | 대사 | 화면 바뀜 | 소리 시작 | 실제 간격 | 로그 간격 | 차이 |\n| --- | --- | --- | --- | --- | --- | --- | --- |\n")
            for k, (e, fr, snd, x) in enumerate(res, 1):
                f.write(f"| {k} | {e['screen']} | {e['vid']} | {mmss(fr) if fr is not None else '—'} | {mmss(snd) if snd is not None else '—'} | "
                        f"{f'{snd - fr:+.3f}' if x is not None else '—'} | {e['log_gap']:+.3f} | {f'{x:+.3f}' if x is not None else '—'} |\n")
        if vals:
            print(f"화면 전환 ↔ 소리 {len(vals)}곳: 차이 중앙값 {vals[len(vals) // 2]:+.3f}초, 범위 {vals[0]:+.3f} ~ {vals[-1]:+.3f}")
    print(f"자막 {len(rows)}개 · 통과 {len(rows) - bad} · 넘음 {bad} → {out}")
    for it, shown, ref, _, diff, ok, _, _ in rows:
        if not ok:
            print(f"  ✗ {it['kind']} {it['text']}  자막 {shown} 기준 {ref} 차이 {diff}")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
