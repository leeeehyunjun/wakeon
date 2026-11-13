"""docs/design_svg(피그마에서 받은 SVG)로 app/src/main/res/drawable/wk_*.xml을 만든다.

사용: python3 -I tools/make_drawables.py
SVG는 Design 페이지 앱 창 node의 get_design_context 자산 그대로다(2026-11-13). 그림자·블러는 VectorDrawable이 못 그려 빠진다.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import svg2vd  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "docs", "design_svg")
DST = os.path.join(ROOT, "app", "src", "main", "res", "drawable")


def s(name):
    return os.path.join(SRC, name + ".svg")


def d(name):
    return os.path.join(DST, name + ".xml")


# 점 파형 5겹(시안 W-05 114:2578~2582). dots 1 그림의 왼쪽 위를 기준으로 겹친 위치.
DOTS = [("1", 0, 0), ("2", 36.059, 17.9), ("3", 54.1555, 35.7951), ("4", 90.2341, 53.695), ("5", 108.3313, 71.5947)]

# 쓰지 않는 시안 그림(등받이·되돌리기)은 docs/design_svg에만 둔다. 2차에서 쓰면 여기에 더한다.
SINGLE = {
    "wk_logo": "logo",
    "wk_wave_line": "center_line",
    "wk_wave_line_paused": "pcenter_line",
    "wk_ic_coffee": "ic_coffee",
    "wk_ic_light": "ic_light",
    "wk_ic_air": "ic_air",
    "wk_ic_timer": "ic_timer_done",
}

# 그림자 여백을 잘라 낸 상태 표시(시안의 Union): (출력, 입력, 여백, 너비, 높이)
CROPPED = [
    ("wk_status_wave", "status_union", 14, 50, 11.37),
    ("wk_status_line", "pstatus_union", 14, 38.4989, 2.0375),
    ("wk_status_dot", "w10_dot", 14, 11.3691, 11.3701),
    ("wk_chip_handle", "chip_handle", 10.1192, 8.2179, 8.2179),
    ("wk_chip_union", "chip_union", 10.1192, 33.7971, 8.2178),
]


def main():
    os.makedirs(DST, exist_ok=True)
    for out, src in SINGLE.items():
        svg2vd.convert(s(src), d(out))
    for out, prefix in (("wk_wave_dots", "dots"), ("wk_wave_dots_paused", "pdots")):
        svg2vd.combine([(s(prefix + n), dx, dy) for n, dx, dy in DOTS], d(out), 267.798, 192,
                       f"{prefix}1~5.svg 겹침(시안 점 파형)")
    for out, src, margin, w, h in CROPPED:
        svg2vd.combine([(s(src), -margin, -margin)], d(out), w, h, f"{src}.svg (그림자 여백 뺌)")
    print("만든 파일:", len(SINGLE) + 2 + len(CROPPED))


if __name__ == "__main__":
    main()
