// 시연 영상 편집용 그림 만들기(BUILD 단계 9). ffmpeg에 글자 그리기(drawtext·libass)가 없어 맥 AppKit으로 PNG를 그린다.
//   swift tools/video/render_overlays.swift 사양.json 출력폴더
// 사양(JSON): {"images":[{"file":"이름.png","kind":"card"|"overlay", ...}]}
//   card    : 검은 바탕 1920×1080. "small"(작은 회색 줄), "big"(큰 흰 줄), "order":"small-big"|"big-small"
//   overlay : 투명 1920×1080. 아래 자막 띠(시스템 GNB 자리), 오른쪽 위 표기, 필요하면 내비 가림.
//             "rows":[{"speaker":"차"|"나"|"자막","text":..., "note":대답 옆 편집 자막(선택)}] 최대 2줄,
//             "navCover":[x,y,w,h] (선택)
// 글꼴은 Pretendard(설치돼 있어야 함). 색은 앱 시안과 같은 계열: 운전자 대답 #4FD0EE.
import AppKit
import Foundation

let W: CGFloat = 1920, H: CGFloat = 1080
let LABEL = "Pleos Connect 에뮬레이터 v2.1.0 실제 동작 화면 · 운전자 대답은 시연 신호로 입력"
let BAND_TOP: CGFloat = 975          // 원본 1300px(GNB 시작) × 0.75
let ANSWER = NSColor(srgbRed: 0x4F / 255, green: 0xD0 / 255, blue: 0xEE / 255, alpha: 1)
let CAPTION = NSColor(srgbRed: 0xC9 / 255, green: 0xCF / 255, blue: 0xD1 / 255, alpha: 1)

func font(_ name: String, _ size: CGFloat) -> NSFont {
    guard let f = NSFont(name: name, size: size) else {
        FileHandle.standardError.write("글꼴 없음: \(name)\n".data(using: .utf8)!)
        exit(2)
    }
    return f
}

func attr(_ s: String, _ f: NSFont, _ c: NSColor) -> NSAttributedString {
    NSAttributedString(string: s, attributes: [.font: f, .foregroundColor: c])
}

/// 위에서부터 잰 y(픽셀)를 AppKit 좌표(아래가 0)로.
func flip(_ y: CGFloat, _ h: CGFloat = 0) -> CGFloat { H - y - h }

/// 글자 한 줄을 [cx, cy](가운데, 위에서 잰 값)에 그린다.
func drawCentered(_ a: NSAttributedString, cx: CGFloat, cy: CGFloat) {
    let size = a.size()
    a.draw(at: NSPoint(x: cx - size.width / 2, y: flip(cy) - size.height / 2))
}

func roundRect(_ r: NSRect, radius: CGFloat, fill: NSColor?, stroke: NSColor? = nil, width: CGFloat = 2) {
    let p = NSBezierPath(roundedRect: r, xRadius: radius, yRadius: radius)
    if let fill { fill.setFill(); p.fill() }
    if let stroke { stroke.setStroke(); p.lineWidth = width; p.stroke() }
}

func render(_ spec: [String: Any], to url: URL) {
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(W), pixelsHigh: Int(H), bitsPerSample: 8,
                               samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB,
                               bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    let kind = spec["kind"] as? String ?? "overlay"

    if kind == "card" {
        NSColor.black.setFill(); NSRect(x: 0, y: 0, width: W, height: H).fill()
        let small = attr(spec["small"] as? String ?? "", font("Pretendard-Medium", 34), NSColor(white: 0.62, alpha: 1))
        let big = attr(spec["big"] as? String ?? "", font("Pretendard-Bold", 72), .white)
        if (spec["order"] as? String) == "big-small" {
            drawCentered(big, cx: W / 2, cy: H / 2 - 30)
            drawCentered(small, cx: W / 2, cy: H / 2 + 60)
        } else {
            drawCentered(small, cx: W / 2, cy: H / 2 - 70)
            drawCentered(big, cx: W / 2, cy: H / 2 + 10)
        }
        let label = attr(LABEL, font("Pretendard-Regular", 20), NSColor(white: 0.45, alpha: 1))
        drawCentered(label, cx: W / 2, cy: H - 48)
    } else {
        // 내비 가림(로딩 화면일 때만): 어둡게 덮고 "내비(시스템 앱)"
        if let nc = spec["navCover"] as? [Double], nc.count == 4 {
            let r = NSRect(x: nc[0], y: flip(nc[1], nc[3]), width: nc[2], height: nc[3])
            NSColor(white: 0.07, alpha: 0.94).setFill(); r.fill()
            drawCentered(attr("내비(시스템 앱)", font("Pretendard-Medium", 30), NSColor(white: 0.6, alpha: 1)),
                         cx: CGFloat(nc[0] + nc[2] / 2), cy: CGFloat(nc[1] + nc[3] / 2))
        }
        // 아래 자막 띠: 시스템 GNB 자리(y 975~1080)를 덮는다.
        NSColor(white: 0.04, alpha: 1).setFill()
        NSRect(x: 0, y: 0, width: W, height: H - BAND_TOP).fill()
        // 한 줄이면 띠 가운데, 두 줄이면 위·아래로 나눈다. 줄: {"speaker":"차"|"나"|"자막","text":...}
        var rows = spec["rows"] as? [[String: String]] ?? []
        if rows.isEmpty, let t = spec["text"] as? String, !t.isEmpty {
            rows = [["speaker": spec["speaker"] as? String ?? "", "text": t]]
        }
        let two = rows.count >= 2
        let bandH = H - BAND_TOP
        for (i, row) in rows.prefix(2).enumerated() {
            let speaker = row["speaker"] ?? ""
            let text = row["text"] ?? ""
            let cy = two ? BAND_TOP + bandH * (i == 0 ? 0.27 : 0.75) : BAND_TOP + bandH / 2
            let isAnswer = speaker == "나"
            let isCaption = speaker == "자막" || speaker.isEmpty
            let size: CGFloat = two ? 31 : 38
            let body: NSAttributedString
            if isCaption {
                body = attr(text, font("Pretendard-Medium", size - 2), CAPTION)
            } else {
                body = attr("“\(text)”", font("Pretendard-SemiBold", size), isAnswer ? ANSWER : .white)
            }
            let bodySize = body.size()
            if isCaption {
                drawCentered(body, cx: W / 2, cy: cy)
                continue
            }
            // 대답 옆 편집 자막(예: 응답 2.8초 · 평소 1.0초): 같은 줄 오른쪽에 회색으로
            let noteText = row["note"] ?? ""
            let note = attr(noteText, font("Pretendard-Medium", size - 4), CAPTION)
            let noteGap: CGFloat = noteText.isEmpty ? 0 : 28
            let noteW = noteText.isEmpty ? 0 : note.size().width
            let badgeW: CGFloat = two ? 42 : 52, badgeH: CGFloat = two ? 36 : 44, gap: CGFloat = two ? 14 : 18
            let total = badgeW + gap + bodySize.width + noteGap + noteW
            let x0 = W / 2 - total / 2
            let badge = NSRect(x: x0, y: flip(cy) - badgeH / 2, width: badgeW, height: badgeH)
            let badgeFont = font("Pretendard-Bold", two ? 22 : 26)
            if isAnswer {
                roundRect(badge.insetBy(dx: 1, dy: 1), radius: 7, fill: nil, stroke: ANSWER, width: 2)
                drawCentered(attr(speaker, badgeFont, ANSWER), cx: badge.midX, cy: cy)
            } else {
                roundRect(badge, radius: 7, fill: .white)
                drawCentered(attr(speaker, badgeFont, .black), cx: badge.midX, cy: cy)
            }
            body.draw(at: NSPoint(x: x0 + badgeW + gap, y: flip(cy) - bodySize.height / 2))
            if !noteText.isEmpty {
                note.draw(at: NSPoint(x: x0 + badgeW + gap + bodySize.width + noteGap, y: flip(cy) - note.size().height / 2))
            }
        }
        // 오른쪽 위 표기: 상태 표시줄 오른쪽(기기 시계 자리)을 덮는다.
        let label = attr(LABEL, font("Pretendard-Medium", 19), NSColor(white: 0.86, alpha: 1))
        let ls = label.size()
        let pill = NSRect(x: W - ls.width - 40, y: flip(12, 44), width: ls.width + 60, height: 44)
        roundRect(pill, radius: 20, fill: NSColor(white: 0.08, alpha: 1))
        label.draw(at: NSPoint(x: pill.minX + 16, y: pill.midY - ls.height / 2))
    }
    NSGraphicsContext.restoreGraphicsState()
    let png = rep.representation(using: .png, properties: [:])!
    try! png.write(to: url)
}

let args = CommandLine.arguments
guard args.count == 3 else {
    print("사용법: swift tools/video/render_overlays.swift 사양.json 출력폴더")
    exit(1)
}
let data = try! Data(contentsOf: URL(fileURLWithPath: args[1]))
let root = try! JSONSerialization.jsonObject(with: data) as! [String: Any]
let outDir = URL(fileURLWithPath: args[2])
try? FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)
let images = root["images"] as! [[String: Any]]
for spec in images {
    render(spec, to: outDir.appendingPathComponent(spec["file"] as! String))
}
print("그림 \(images.count)장 → \(outDir.path)")
