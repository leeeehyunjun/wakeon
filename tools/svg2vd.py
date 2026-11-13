"""피그마에서 받은 SVG를 안드로이드 VectorDrawable XML로 바꾼다(표준 파이썬만 사용).

사용: python3 -I tools/svg2vd.py 입력.svg 출력.xml

지원: path · rect · circle · g, 단색·투명도·선 두께, 선형/원형 그라데이션(userSpaceOnUse).
filter(그림자·블러)는 VectorDrawable이 지원하지 않아 버린다(필요하면 Compose에서 따로 그린다).
"""
import re
import sys
import xml.etree.ElementTree as ET

SVG = "{http://www.w3.org/2000/svg}"
NAMED = {"white": "#FFFFFF", "black": "#000000"}


def color(value, alpha=1.0):
    v = NAMED.get(value, value)
    if not v.startswith("#"):
        raise SystemExit(f"모르는 색: {value}")
    v = v[1:]
    if len(v) == 3:
        v = "".join(c * 2 for c in v)
    a = round(max(0.0, min(1.0, alpha)) * 255)
    return f"#{a:02X}{v.upper()}"


def num(v, default=0.0):
    return float(v) if v not in (None, "") else default


def rect_path(e):
    x, y, w, h = num(e.get("x")), num(e.get("y")), num(e.get("width")), num(e.get("height"))
    r = min(num(e.get("rx")), w / 2, h / 2)
    if r <= 0:
        return f"M{x},{y}h{w}v{h}h{-w}z"
    return (f"M{x + r},{y}h{w - 2 * r}a{r},{r} 0 0 1 {r},{r}v{h - 2 * r}a{r},{r} 0 0 1 {-r},{r}"
            f"h{-(w - 2 * r)}a{r},{r} 0 0 1 {-r},{-r}v{-(h - 2 * r)}a{r},{r} 0 0 1 {r},{-r}z")


def circle_path(e):
    cx, cy, r = num(e.get("cx")), num(e.get("cy")), num(e.get("r"))
    return f"M{cx - r},{cy}a{r},{r} 0 1 0 {2 * r},0a{r},{r} 0 1 0 {-2 * r},0z"


def gradients(root):
    out = {}
    for g in root.iter():
        tag = g.tag.replace(SVG, "")
        if tag not in ("linearGradient", "radialGradient"):
            continue
        stops = []
        for s in g.findall(f"{SVG}stop"):
            stops.append((num(s.get("offset")), color(s.get("stop-color", "black"), num(s.get("stop-opacity"), 1.0))))
        if tag == "linearGradient":
            attrs = dict(type="linear", startX=g.get("x1"), startY=g.get("y1"), endX=g.get("x2"), endY=g.get("y2"))
        else:
            t = g.get("gradientTransform", "")
            m = re.search(r"translate\(([-\d.e]+)[ ,]+([-\d.e]+)\)", t)
            s = re.search(r"scale\(([-\d.e]+)(?:[ ,]+([-\d.e]+))?\)", t)
            cx, cy = (float(m.group(1)), float(m.group(2))) if m else (num(g.get("cx")), num(g.get("cy")))
            sx = float(s.group(1)) if s else num(g.get("r"), 1)
            sy = float(s.group(2)) if s and s.group(2) else sx
            attrs = dict(type="radial", centerX=str(cx), centerY=str(cy), gradientRadius=str((sx + sy) / 2))
        out[g.get("id")] = (attrs, stops)
    return out


def paint(name, value, alpha, grads, indent):
    """fillColor / strokeColor 속성 문자열 또는 aapt:attr 블록."""
    if value.startswith("url(#"):
        gid = value[5:-1]
        attrs, stops = grads[gid]
        a = " ".join(f'android:{k}="{v}"' for k, v in attrs.items())
        items = "\n".join(f'{indent}      <item android:offset="{o}" android:color="{c}"/>' for o, c in stops)
        return None, (f'{indent}  <aapt:attr name="android:{name}">\n{indent}    <gradient {a}>\n'
                      f'{items}\n{indent}    </gradient>\n{indent}  </aapt:attr>')
    return f'android:{name}="{color(value, alpha)}"', None


def walk(e, opacity, grads, out, inherited):
    tag = e.tag.replace(SVG, "")
    op = opacity * num(e.get("opacity"), 1.0)
    style = dict(inherited)
    for k in ("fill", "stroke", "stroke-width", "fill-rule", "fill-opacity", "stroke-opacity"):
        if e.get(k) is not None:
            style[k] = e.get(k)
    if tag in ("svg", "g"):
        for c in e:
            walk(c, op, grads, out, style)
        return
    if tag == "path":
        d = e.get("d")
    elif tag == "rect":
        d = rect_path(e)
    elif tag == "circle":
        d = circle_path(e)
    else:
        return
    attrs, blocks = [f'android:pathData="{d}"'], []
    fill = style.get("fill", "black")
    if fill != "none":
        a, b = paint("fillColor", fill, op * num(style.get("fill-opacity"), 1.0), grads, "    ")
        attrs.append(a) if a else blocks.append(b)
        if b and op < 1:
            attrs.append(f'android:fillAlpha="{op:.3f}"')
        if style.get("fill-rule") == "evenodd":
            attrs.append('android:fillType="evenOdd"')
    stroke = style.get("stroke", "none")
    if stroke != "none":
        a, b = paint("strokeColor", stroke, op * num(style.get("stroke-opacity"), 1.0), grads, "    ")
        attrs.append(a) if a else blocks.append(b)
        if b and op < 1:
            attrs.append(f'android:strokeAlpha="{op:.3f}"')
        attrs.append(f'android:strokeWidth="{num(style.get("stroke-width"), 1.0)}"')
    if blocks:
        out.append("  <path " + "\n      ".join(attrs) + ">\n" + "\n".join(blocks) + "\n  </path>")
    else:
        out.append("  <path " + "\n      ".join(attrs) + "/>")


def convert(src, dst):
    root = ET.parse(src).getroot()
    vb = [float(v) for v in root.get("viewBox").split()]
    w, h = num(root.get("width"), vb[2]), num(root.get("height"), vb[3])
    grads = gradients(root)
    body = []
    for c in root:
        if c.tag.replace(SVG, "") != "defs":
            walk(c, 1.0, grads, body, {"fill": root.get("fill", "black")})
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           f"<!-- tools/svg2vd.py로 {src.split('/')[-1]}에서 만듦. 피그마 시안 그림 그대로(그림자·블러 제외). -->\n"
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt"\n'
           f'    android:width="{w}dp" android:height="{h}dp"\n'
           f'    android:viewportWidth="{vb[2]}" android:viewportHeight="{vb[3]}">\n'
           + "\n".join(body) + "\n</vector>\n")
    open(dst, "w", encoding="utf-8").write(xml)


def combine(parts, dst, width, height, note):
    """여러 SVG를 (dx, dy)만큼 옮겨 한 장으로 합친다. 음수 이동과 작은 width/height로 여백(그림자 자리)을 잘라 낼 수도 있다."""
    body = []
    for src, dx, dy in parts:
        root = ET.parse(src).getroot()
        grads = gradients(root)
        inner = []
        for c in root:
            if c.tag.replace(SVG, "") != "defs":
                walk(c, 1.0, grads, inner, {"fill": root.get("fill", "black")})
        body.append(f'  <group android:translateX="{dx}" android:translateY="{dy}">\n' + "\n".join(inner) + "\n  </group>")
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           f"<!-- tools/svg2vd.py: {note} -->\n"
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt"\n'
           f'    android:width="{width}dp" android:height="{height}dp"\n'
           f'    android:viewportWidth="{width}" android:viewportHeight="{height}">\n'
           + "\n".join(body) + "\n</vector>\n")
    open(dst, "w", encoding="utf-8").write(xml)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    convert(sys.argv[1], sys.argv[2])
