import Flutter
import UIKit
#if canImport(AlarmKit)
import AlarmKit
import AppIntents
import ActivityKit
import SwiftUI
#endif

private let birdAlarmChannelName = "bird_alarm/system_alarm"
private let birdAlarmDefaultsSuite = "bird_alarm_ios"
private let scheduledAlarmIdKey = "scheduled_alarm_id"
private let launchAlarmKey = "launch_alarm"
private let ringingAssetKey = "ringing_asset"

#if canImport(AlarmKit)
@available(iOS 26.0, *)
struct BirdAlarmMetadata: AlarmMetadata {
  let alarmId: String
  let assetPath: String?
}

@available(iOS 26.0, *)
public struct OpenBirdAlarmIntent: LiveActivityIntent {
  public static var title: LocalizedStringResource = "打开鸟瘾闹钟"
  public static var description = IntentDescription("打开鸟瘾闹钟并进入认鸟挑战")
  public static var openAppWhenRun = true

  @Parameter(title: "alarmID")
  public var alarmID: String

  @Parameter(title: "assetPath")
  public var assetPath: String

  public init(alarmID: String, assetPath: String) {
    self.alarmID = alarmID
    self.assetPath = assetPath
  }

  public init() {
    self.alarmID = ""
    self.assetPath = ""
  }

  public func perform() async throws -> some IntentResult {
    let defaults = UserDefaults.standard
    defaults.set(true, forKey: launchAlarmKey)
    if !assetPath.isEmpty {
      defaults.set(assetPath, forKey: ringingAssetKey)
    }
    return .result()
  }
}
#endif

@main
@objc class AppDelegate: FlutterAppDelegate {
  private var channel: FlutterMethodChannel?

  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    GeneratedPluginRegistrant.register(with: self)
    if let controller = window?.rootViewController as? FlutterViewController {
      channel = FlutterMethodChannel(
        name: birdAlarmChannelName,
        binaryMessenger: controller.binaryMessenger
      )
      channel?.setMethodCallHandler { [weak self] call, result in
        self?.handleAlarmMethodCall(call, result: result)
      }
    }
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  private func handleAlarmMethodCall(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "requestAlarmPermissions":
      requestAlarmPermissions(result: result)
    case "scheduleAlarmAt":
      guard
        let arguments = call.arguments as? [String: Any],
        let triggerAtMillis = (arguments["triggerAtMillis"] as? NSNumber)?.doubleValue
      else {
        result(FlutterError(code: "missing_trigger", message: "triggerAtMillis is required", details: nil))
        return
      }
      let label = arguments["label"] as? String ?? "鸟瘾闹钟"
      let assetPath = arguments["assetPath"] as? String
      scheduleAlarm(triggerAtMillis: triggerAtMillis, label: label, assetPath: assetPath, result: result)
    case "cancelAlarm":
      cancelAlarm(result: result)
    case "consumeLaunchAlarm":
      let defaults = UserDefaults.standard
      let launched = defaults.bool(forKey: launchAlarmKey)
      let assetPath = defaults.string(forKey: ringingAssetKey)
      defaults.set(false, forKey: launchAlarmKey)
      defaults.removeObject(forKey: ringingAssetKey)
      result(["launched": launched, "assetPath": assetPath as Any])
    case "stopAlarmSound":
      cancelAlarm(result: result)
    case "prepareAlarmWindow", "testSystemAlarm":
      result(nil)
    default:
      result(FlutterMethodNotImplemented)
    }
  }

  private func requestAlarmPermissions(result: @escaping FlutterResult) {
    #if canImport(AlarmKit)
    if #available(iOS 26.0, *) {
      Task {
        do {
          let state = try await AlarmManager.shared.requestAuthorization()
          result(state == .authorized)
        } catch {
          result(FlutterError(code: "alarmkit_auth_failed", message: error.localizedDescription, details: nil))
        }
      }
      return
    }
    #endif
    result(false)
  }

  private func scheduleAlarm(
    triggerAtMillis: Double,
    label: String,
    assetPath: String?,
    result: @escaping FlutterResult
  ) {
    #if canImport(AlarmKit)
    if #available(iOS 26.0, *) {
      Task {
        do {
          try cancelStoredAlarmIfNeeded()
          let alarmId = UUID()
          let date = Date(timeIntervalSince1970: triggerAtMillis / 1000.0)
          let schedule = Alarm.Schedule.fixed(date)
          let stopButton = AlarmButton(
            text: "停止",
            textColor: .white,
            systemImageName: "stop.circle"
          )
          let openButton = AlarmButton(
            text: "认鸟",
            textColor: .white,
            systemImageName: "bird"
          )
          let alert = AlarmPresentation.Alert(
            title: LocalizedStringResource(stringLiteral: label),
            stopButton: stopButton,
            secondaryButton: openButton,
            secondaryButtonBehavior: .custom
          )
          let attributes = AlarmAttributes<BirdAlarmMetadata>(
            presentation: AlarmPresentation(alert: alert),
            metadata: BirdAlarmMetadata(alarmId: alarmId.uuidString, assetPath: assetPath),
            tintColor: Color(red: 0.09, green: 0.29, blue: 0.27)
          )
          let copiedSoundName = copyAlarmSoundToLibrary(assetPath: assetPath)
          let sound = copiedSoundName.map { AlertConfiguration.AlertSound.named($0) }
          let configuration: AlarmManager.AlarmConfiguration<BirdAlarmMetadata>
          if let sound {
            configuration = AlarmManager.AlarmConfiguration<BirdAlarmMetadata>(
              schedule: schedule,
              attributes: attributes,
              secondaryIntent: OpenBirdAlarmIntent(alarmID: alarmId.uuidString, assetPath: assetPath ?? ""),
              sound: sound
            )
          } else {
            configuration = AlarmManager.AlarmConfiguration<BirdAlarmMetadata>(
              schedule: schedule,
              attributes: attributes,
              secondaryIntent: OpenBirdAlarmIntent(alarmID: alarmId.uuidString, assetPath: assetPath ?? "")
            )
          }
          _ = try await AlarmManager.shared.schedule(id: alarmId, configuration: configuration)
          UserDefaults.standard.set(alarmId.uuidString, forKey: scheduledAlarmIdKey)
          result(nil)
        } catch {
          result(FlutterError(code: "alarmkit_schedule_failed", message: error.localizedDescription, details: nil))
        }
      }
      return
    }
    #endif
    result(FlutterError(code: "alarmkit_unavailable", message: "AlarmKit requires iOS 26 or later", details: nil))
  }

  private func cancelAlarm(result: @escaping FlutterResult) {
    #if canImport(AlarmKit)
    if #available(iOS 26.0, *) {
      do {
        try cancelStoredAlarmIfNeeded()
        result(nil)
      } catch {
        result(FlutterError(code: "alarmkit_cancel_failed", message: error.localizedDescription, details: nil))
      }
      return
    }
    #endif
    result(nil)
  }

  #if canImport(AlarmKit)
  @available(iOS 26.0, *)
  private func cancelStoredAlarmIfNeeded() throws {
    let defaults = UserDefaults.standard
    guard
      let rawId = defaults.string(forKey: scheduledAlarmIdKey),
      let alarmId = UUID(uuidString: rawId)
    else {
      return
    }
    try AlarmManager.shared.cancel(id: alarmId)
    defaults.removeObject(forKey: scheduledAlarmIdKey)
  }

  @available(iOS 26.0, *)
  private func copyAlarmSoundToLibrary(assetPath: String?) -> String? {
    guard let assetPath, let fileName = assetPath.split(separator: "/").last else {
      return nil
    }
    let soundName = String(fileName)
    let subdirectory = "Frameworks/App.framework/flutter_assets/assets/sounds"
    guard let source = Bundle.main.url(forResource: soundName, withExtension: nil, subdirectory: subdirectory) else {
      return nil
    }
    guard let library = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask).first else {
      return nil
    }
    let soundsDirectory = library.appendingPathComponent("Sounds", isDirectory: true)
    let destination = soundsDirectory.appendingPathComponent(soundName)
    do {
      try FileManager.default.createDirectory(at: soundsDirectory, withIntermediateDirectories: true)
      if FileManager.default.fileExists(atPath: destination.path) {
        try FileManager.default.removeItem(at: destination)
      }
      try FileManager.default.copyItem(at: source, to: destination)
      return soundName
    } catch {
      return nil
    }
  }
  #endif
}
