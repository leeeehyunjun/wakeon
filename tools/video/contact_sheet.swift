// 프레임 모음(BUILD 단계 9 확인용): 폴더의 PNG를 이름 순서로 격자에 놓고 각 칸에 영상 시각을 적는다.
//   swift tools/video/contact_sheet.swift 프레임폴더 간격초 열수 출력.png
// 프레임은 ffmpeg로 뽑는다: ffmpeg -i 영상.mp4 -vf "fps=1/5,scale=480:-1" 폴더/f_%04d.png  (첫 장 = 0초)
import AppKit
import Foundation

let args = CommandLine.arguments
guard args.count == 5, let interval = Double(args[2]), let cols = Int(args[3]) else {
    print("사용법: swift tools/video/contact_sheet.swift 프레임폴더 간격초 열수 출력.png")
    exit(1)
}
let dir = URL(fileURLWithPath: args[1])
let files = try! FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)
    .filter { $0.pathExtension.lowercased() == "png" }
    .sorted { $0.lastPathComponent < $1.lastPathComponent }
guard let first = files.first, let img0 = NSImage(contentsOf: first) else { exit(2) }
let tw = img0.size.width, th = img0.size.height
let pad: CGFloat = 12, labelH: CGFloat = 34
let rows = Int(ceil(Double(files.count) / Double(cols)))
let W = CGFloat(cols) * (tw + pad) + pad
let H = CGFloat(rows) * (th + labelH + pad) + pad
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(W), pixelsHigh: Int(H), bitsPerSample: 8,
                           samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB,
                           bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
NSColor(white: 0.12, alpha: 1).setFill()
NSRect(x: 0, y: 0, width: W, height: H).fill()
let font = NSFont(name: "Pretendard-SemiBold", size: 22) ?? NSFont.boldSystemFont(ofSize: 22)
for (i, url) in files.enumerated() {
    guard let img = NSImage(contentsOf: url) else { continue }
    let r = i / cols, c = i % cols
    let x = pad + CGFloat(c) * (tw + pad)
    let yTop = pad + CGFloat(r) * (th + labelH + pad)
    let t = Double(i) * interval
    let label = String(format: "%d:%02d", Int(t) / 60, Int(t) % 60)
    let a = NSAttributedString(string: label, attributes: [.font: font, .foregroundColor: NSColor.white])
    a.draw(at: NSPoint(x: x + 2, y: H - yTop - labelH + 6))
    img.draw(in: NSRect(x: x, y: H - yTop - labelH - th, width: tw, height: th))
}
NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: args[4]))
print("프레임 \(files.count)장 → \(args[4]) (\(Int(W))×\(Int(H)))")
