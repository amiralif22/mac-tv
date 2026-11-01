import Foundation

/// The wire protocol, v1, implemented exactly as `shared/remote-protocol.md` defines it.
///
/// Two rules shape everything here:
///
/// 1. **Encoding is total.** A remote that cannot build a frame has nothing to say; every builder
///    returns a `String` and clamps its own inputs to the protocol's limits.
/// 2. **Decoding is forgiving.** Unknown types, unknown keys and wrong types return `nil` rather
///    than throwing, so a newer TV can talk to this app without an update.
public enum Protocol {
    public static let version = 1
    public static let maxText = 500
    public static let maxBackspace = 32
    public static let maxPointerBatch = 8
    /// Wrong pairing codes allowed per address per ten minutes, mirrored so the UI can explain itself.
    public static let maxPairingAttempts = 5

    // MARK: - Keys

    /// Every key the TV understands. The raw value is the wire name, never the display name.
    public enum Key: String, CaseIterable, Sendable {
        case up, down, left, right, ok, back, home, menu, info, settings, search
        case play, pause, playpause, stop, rewind, forward, next, prev
        case volup, voldown, mute
        case power, wake, sleep
    }

    // MARK: - Encoding (client → TV)

    public static func hello(
        deviceId: String,
        name: String,
        platform: String,
        app: String,
        token: String?
    ) -> String {
        var payload: [String: Any] = [
            "t": "hello",
            "v": version,
            "id": deviceId,
            "name": name,
            "platform": platform,
            "app": app,
        ]
        if let token, !token.isEmpty { payload["token"] = token }
        return encode(payload)
    }

    public static func pair(pin: String, deviceId: String, name: String, platform: String) -> String {
        encode([
            "t": "pair",
            "pin": pin.filter(\.isNumber),
            "id": deviceId,
            "name": name,
            "platform": platform,
        ])
    }

    public static func key(_ key: Key, hold: Bool = false) -> String {
        var payload: [String: Any] = ["t": "key", "k": key.rawValue]
        if hold { payload["hold"] = true }
        return encode(payload)
    }

    /// One pointer sample, as a fraction of the screen. Values are clamped to ±1 by contract.
    public static func pointer(dx: Double, dy: Double, ts: Int64 = nowMillis()) -> String {
        encode(["t": "pointer", "dx": clampUnit(dx), "dy": clampUnit(dy), "ts": ts])
    }

    /// Up to eight samples coalesced into one frame — the 60 Hz path.
    public static func pointerBatch(_ samples: [PointerSample]) -> String? {
        let trimmed = samples.suffix(maxPointerBatch)
        guard !trimmed.isEmpty else { return nil }
        let array = trimmed.map { [clampUnit($0.dx), clampUnit($0.dy), $0.ts] as [Any] }
        return encode(["t": "pointer", "batch": array])
    }

    public static func click() -> String { encode(["t": "click"]) }
    public static func longPress() -> String { encode(["t": "longpress"]) }
    public static func scroll(dy: Double) -> String { encode(["t": "scroll", "dy": dy]) }

    public static func text(_ value: String) -> String? {
        let trimmed = String(value.prefix(maxText))
        guard !trimmed.isEmpty else { return nil }
        return encode(["t": "text", "v": trimmed])
    }

    public static func backspace(_ count: Int = 1) -> String {
        encode(["t": "backspace", "n": count.clamped(to: 1...maxBackspace)])
    }

    public static func submit() -> String { encode(["t": "submit"]) }

    /// Voice is transcribed on the phone; only text is sent.
    public static func voice(_ transcription: String, final: Bool = true) -> String? {
        let trimmed = String(transcription.prefix(maxText))
        guard !trimmed.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return encode(["t": "voice", "text": trimmed, "final": final])
    }

    public static func launch(package: String) -> String? {
        guard !package.isEmpty else { return nil }
        return encode(["t": "launch", "pkg": package])
    }

    public static func command(_ action: String, query: String? = nil) -> String {
        var payload: [String: Any] = ["t": "command", "a": action]
        if let query, !query.isEmpty { payload["q"] = query }
        return encode(payload)
    }

    public static func ping(ts: Int64 = nowMillis()) -> String {
        encode(["t": "ping", "ts": ts])
    }

    // MARK: - Decoding (TV → client)

    /// Anything the TV can say. Every case carries only what the protocol guarantees.
    public enum ServerMessage: Equatable, Sendable {
        case welcome(Welcome)
        case paired(Welcome)
        case pairRequired(device: String, tvName: String, digits: Int)
        case error(code: String, message: String?, attempts: Int?)
        case state(TVState)
        case apps([TVApp])
        case toast(String)
        case event(String)
        case pong(ts: Int64, tv: Int64?)
    }

    public struct Welcome: Equatable, Sendable {
        public var device: String
        public var token: String
        public var name: String
        public var model: String
        public var width: Int
        public var height: Int
        public var caps: [String]
    }

    public struct TVState: Equatable, Sendable {
        public var screen: String?
        public var focusKind: String?
        public var focusLabel: String?
        public var media: Media?
        public var volume: Float?
        public var muted: Bool?
        public var pointer: Bool?
        public var pin: Bool?

        public struct Media: Equatable, Sendable {
            public var playing: Bool
            public var title: String
            public var subtitle: String?
            public var positionMs: Int64
            public var durationMs: Int64
            public var app: String?
        }
    }

    public struct TVApp: Equatable, Sendable, Identifiable {
        public var id: String { package }
        public var package: String
        public var name: String
        public var color: String
        public var favourite: Bool
    }

    public static func decode(_ frame: String) -> ServerMessage? {
        guard
            let data = frame.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data),
            let root = object as? [String: Any],
            let type = root["t"] as? String
        else { return nil }

        switch type {
        case "welcome":
            return welcome(from: root).map { .welcome($0) }
        case "paired":
            return welcome(from: root).map { .paired($0) }
        case "pair-required":
            return .pairRequired(
                device: root["device"] as? String ?? "",
                tvName: root["tvName"] as? String ?? "TV",
                digits: root["pin"] as? Int ?? 6
            )
        case "error":
            return .error(
                code: root["code"] as? String ?? "internal",
                message: root["message"] as? String,
                attempts: root["attempts"] as? Int
            )
        case "state":
            return state(from: root).map { .state($0) }
        case "apps":
            let list = (root["apps"] as? [[String: Any]] ?? []).compactMap { entry -> TVApp? in
                guard let pkg = entry["pkg"] as? String, !pkg.isEmpty else { return nil }
                return TVApp(
                    package: pkg,
                    name: entry["name"] as? String ?? pkg,
                    color: entry["color"] as? String ?? "#6E7482",
                    favourite: entry["fav"] as? Bool ?? false
                )
            }
            return .apps(list)
        case "toast":
            return (root["text"] as? String).map { .toast($0) }
        case "event":
            return (root["name"] as? String).map { .event($0) }
        case "pong":
            return .pong(ts: int(root["ts"]) ?? 0, tv: int(root["tv"]))
        default:
            // Unknown types are ignored by contract: a newer TV must not break an older remote.
            return nil
        }
    }

    // MARK: - Helpers

    public struct PointerSample: Equatable, Sendable {
        public var dx: Double
        public var dy: Double
        public var ts: Int64

        public init(dx: Double, dy: Double, ts: Int64 = Protocol.nowMillis()) {
            self.dx = dx
            self.dy = dy
            self.ts = ts
        }
    }

    public static func nowMillis() -> Int64 {
        Int64((Date().timeIntervalSince1970 * 1000).rounded())
    }

    private static func encode(_ payload: [String: Any]) -> String {
        guard
            JSONSerialization.isValidJSONObject(payload),
            let data = try? JSONSerialization.data(withJSONObject: payload),
            let text = String(data: data, encoding: .utf8)
        else {
            // Should not happen: every builder above produces JSON-safe values. An empty frame is
            // ignored by the TV, which is safer than crashing the remote mid-gesture.
            return "{\"t\":\"noop\"}"
        }
        return text
    }

    private static func clampUnit(_ value: Double) -> Double {
        guard value.isFinite else { return 0 }
        return min(1, max(-1, value))
    }

    private static func welcome(from root: [String: Any]) -> Welcome? {
        guard let token = root["token"] as? String, !token.isEmpty else { return nil }
        let tv = root["tv"] as? [String: Any] ?? [:]
        return Welcome(
            device: root["device"] as? String ?? "",
            token: token,
            name: tv["name"] as? String ?? "TV",
            model: tv["model"] as? String ?? "",
            width: tv["w"] as? Int ?? 1920,
            height: tv["h"] as? Int ?? 1080,
            caps: root["caps"] as? [String] ?? []
        )
    }

    private static func state(from root: [String: Any]) -> TVState? {
        var state = TVState()
        state.screen = root["screen"] as? String
        if let focus = root["focus"] as? [String: Any] {
            state.focusKind = focus["kind"] as? String
            state.focusLabel = focus["label"] as? String
        }
        if let media = root["media"] as? [String: Any], let title = media["title"] as? String {
            state.media = TVState.Media(
                playing: media["playing"] as? Bool ?? false,
                title: title,
                subtitle: media["subtitle"] as? String,
                positionMs: int(media["positionMs"]) ?? 0,
                durationMs: int(media["durationMs"]) ?? 0,
                app: media["app"] as? String
            )
        }
        state.volume = float(root["volume"])
        state.muted = root["muted"] as? Bool
        state.pointer = root["pointer"] as? Bool
        state.pin = root["pin"] as? Bool
        return state
    }

    private static func int(_ any: Any?) -> Int64? {
        switch any {
        case let value as Int64: return value
        case let value as Int: return Int64(value)
        case let value as Double: return Int64(value)
        case let value as NSNumber: return value.int64Value
        default: return nil
        }
    }

    private static func float(_ any: Any?) -> Float? {
        switch any {
        case let value as Double: return Float(value)
        case let value as NSNumber: return value.floatValue
        default: return nil
        }
    }
}

extension Comparable {
    /// Small and dependency-free: used for both pointer clamping and digit counts.
    public func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
