import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

let args = CommandLine.arguments
guard args.count == 3 else {
    fputs("usage: render_pdf_pages <input.pdf> <output_dir>\n", stderr)
    exit(2)
}

let input = URL(fileURLWithPath: args[1])
let outDir = URL(fileURLWithPath: args[2], isDirectory: true)
try FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)

guard let doc = CGPDFDocument(input as CFURL) else {
    fputs("failed to open PDF\n", stderr)
    exit(1)
}

let pageCount = doc.numberOfPages
for pageNumber in 1...pageCount {
    guard let page = doc.page(at: pageNumber) else { continue }
    let box = page.getBoxRect(.mediaBox)
    let scale: CGFloat = 2.0
    let width = Int(box.width * scale)
    let height = Int(box.height * scale)
    guard let ctx = CGContext(
        data: nil,
        width: width,
        height: height,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    ) else {
        continue
    }
    ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
    ctx.fill(CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height)))
    ctx.saveGState()
    let target = CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height))
    let transform = page.getDrawingTransform(.mediaBox, rect: target, rotate: 0, preserveAspectRatio: true)
    ctx.concatenate(transform)
    ctx.drawPDFPage(page)
    ctx.restoreGState()

    guard let image = ctx.makeImage() else { continue }
    let out = outDir.appendingPathComponent(String(format: "page-%02d.png", pageNumber))
    guard let dest = CGImageDestinationCreateWithURL(out as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        continue
    }
    CGImageDestinationAddImage(dest, image, nil)
    CGImageDestinationFinalize(dest)
}

print("rendered \(pageCount) pages")
