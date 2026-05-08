import AVFoundation
import Foundation

func transcode(inputPath: String, outputPath: String, volume: Float) -> Int32 {
    let input = URL(fileURLWithPath: inputPath)
    let output = URL(fileURLWithPath: outputPath)
    try? FileManager.default.removeItem(at: output)

    let asset = AVURLAsset(url: input)
    let composition = AVMutableComposition()
    guard
        let sourceTrack = asset.tracks(withMediaType: .audio).first,
        let track = composition.addMutableTrack(
            withMediaType: .audio,
            preferredTrackID: kCMPersistentTrackID_Invalid
        )
    else {
        FileHandle.standardError.write(Data("failed to load audio track\n".utf8))
        return 1
    }

    do {
        try track.insertTimeRange(
            CMTimeRange(start: .zero, duration: asset.duration),
            of: sourceTrack,
            at: .zero
        )
    } catch {
        FileHandle.standardError.write(Data("failed to compose track: \(error)\n".utf8))
        return 2
    }

    let parameters = AVMutableAudioMixInputParameters(track: track)
    parameters.setVolume(volume, at: .zero)
    let mix = AVMutableAudioMix()
    mix.inputParameters = [parameters]

    guard let exporter = AVAssetExportSession(
        asset: composition,
        presetName: AVAssetExportPresetAppleM4A
    ) else {
        FileHandle.standardError.write(Data("failed to create exporter\n".utf8))
        return 3
    }

    exporter.outputURL = output
    exporter.outputFileType = .m4a
    exporter.audioMix = mix

    let semaphore = DispatchSemaphore(value: 0)
    exporter.exportAsynchronously { semaphore.signal() }
    semaphore.wait()

    if exporter.status == .completed {
        return 0
    }

    let message =
        "export failed: \(exporter.status.rawValue) \(exporter.error?.localizedDescription ?? "unknown")\n"
    FileHandle.standardError.write(Data(message.utf8))
    return 4
}

guard CommandLine.arguments.count >= 3 else {
    FileHandle.standardError.write(
        Data("usage: swift amplify_transcode.swift input output [volume]\n".utf8)
    )
    exit(64)
}

let volume = CommandLine.arguments.count >= 4
    ? (Float(CommandLine.arguments[3]) ?? 2.5)
    : 2.5

exit(transcode(
    inputPath: CommandLine.arguments[1],
    outputPath: CommandLine.arguments[2],
    volume: volume
))
