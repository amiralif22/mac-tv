import Foundation
import RemoteKit

/// The state of the link, in words a person would use.
enum LinkState: Equatable {
    case idle
    case connecting
    case handshaking
    case awaitingPairing
    case paired(String)          // TV name
    case reconnecting(attempt: Int)
    case failed(String)

    var isConnected: Bool {
        if case .paired = self { return true }
        return false
    }

    var title: String {
        switch self {
        case .idle: return "Not connected"
        case .connecting: return "Connecting…"
        case .handshaking: return "Signing in…"
        case .awaitingPairing: return "Enter the code from your TV"
        case let .paired(name): return name
        case let .reconnecting(attempt): return "Reconnecting (\(attempt))…"
        case let .failed(message): return message
        }
    }
}

/// One WebSocket link to one TV: handshake, pairing, input, state and keep-alive.
///
/// Everything is delivered to the main actor because it drives UI, but nothing blocks it: sends are
/// fire-and-forget, and the receive loop is a single `Task` that ends with the socket.
@MainActor
final class RemoteConnection: ObservableObject {
    @Published private(set) var state: LinkState = .idle
    @Published private(set) var tvState = Wire.TVState()
    @Published private(set) var apps: [Wire.TVApp] = []
    @Published private(set) var roundTripMs: Int?
    @Published private(set) var pairingRequired = false
    /// Wrong codes so far for this TV, so the pairing screen can explain itself.
    @Published private(set) var pairingAttempts = 0
    @Published var lastError: String?

    private(set) var tv: DiscoveredTV?
    private var socket: URLSessionWebSocketTask?
    private var session: URLSession?
    private var receiveTask: Task<Void, Never>?
    private var pingTask: Task<Void, Never>?
    private var reconnectTask: Task<Void, Never>?
    private var attempt = 0
    private var wantsConnection = false

    private let deviceId: String
    private let deviceName: String
    private let store: TokenStore
    private let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"

    init(store: TokenStore = KeychainTokenStore()) {
        self.store = store
        self.deviceId = DeviceIdentity.loadOrCreate()
        self.deviceName = DeviceIdentity.currentName()
    }

    // MARK: - Lifecycle

    func connect(to tv: DiscoveredTV) {
        self.tv = tv
        wantsConnection = true
        attempt = 0
        openSocket()
    }

    func disconnect() {
        wantsConnection = false
        reconnectTask?.cancel()
        receiveTask?.cancel()
        pingTask?.cancel()
        socket?.cancel(with: .normalClosure, reason: nil)
        socket = nil
        session?.invalidateAndCancel()
        session = nil
        state = .idle
        tvState = Wire.TVState()
        apps = []
        pairingRequired = false
        roundTripMs = nil
    }

    /// Called when the app returns to the foreground: an immediate retry, per the protocol.
    func retryNow() {
        guard wantsConnection, !state.isConnected else { return }
        reconnectTask?.cancel()
        attempt = 0
        openSocket()
    }

    private func openSocket() {
        guard let tv, let url = tv.websocketURL else {
            state = .failed("That address is not usable.")
            return
        }
        receiveTask?.cancel()
        pingTask?.cancel()
        socket?.cancel(with: .goingAway, reason: nil)

        let configuration = URLSessionConfiguration.default
        configuration.waitsForConnectivity = false
        configuration.timeoutIntervalForRequest = 10
        let session = URLSession(configuration: configuration)
        let task = session.webSocketTask(with: url)
        self.session = session
        self.socket = task
        state = .connecting
        task.resume()

        receiveLoop(task)
        sendHello()
        schedulePings()
    }

    private func schedulePings() {
        pingTask?.cancel()
        pingTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 15 * 1_000_000_000)
                guard !Task.isCancelled else { return }
                self?.send(Wire.ping())
            }
        }
    }

    private func receiveLoop(_ task: URLSessionWebSocketTask) {
        receiveTask = Task { [weak self] in
            while !Task.isCancelled {
                do {
                    let message = try await task.receive()
                    guard let self else { return }
                    if case let .string(text) = message {
                        await self.handle(text)
                    }
                    // Binary frames are not part of v1; ignore them.
                } catch {
                    guard let self else { return }
                    self.handleDrop(error)
                    return
                }
            }
        }
    }

    private func handleDrop(_ error: Error) {
        socket = nil
        session?.invalidateAndCancel()
        session = nil
        pingTask?.cancel()
        guard wantsConnection else { return }
        if case .awaitingPairing = state { return } // the user must act; do not hammer the TV
        attempt += 1
        state = .reconnecting(attempt: attempt)
        scheduleReconnect()
    }

    private func scheduleReconnect() {
        reconnectTask?.cancel()
        // 0.5, 1, 2, 4, 8, 10 s with ±20 % jitter, per the protocol.
        let base = min(10.0, pow(2.0, Double(max(0, attempt - 1))) * 0.5)
        let jittered = base * Double.random(in: 0.8...1.2)
        reconnectTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(jittered * 1_000_000_000))
            guard !Task.isCancelled else { return }
            await MainActor.run { self?.openSocket() }
        }
    }

    // MARK: - Sending

    private func sendHello() {
        state = .handshaking
        let token = tv.flatMap { store.token(for: $0.id) }
        send(
            Wire.hello(
                deviceId: deviceId,
                name: deviceName,
                platform: "ios",
                app: "Couch Remote/\(appVersion)",
                token: token
            )
        )
    }

    func submitPairingCode(_ code: String) {
        guard let tv else { return }
        state = .handshaking
        send(Wire.pair(pin: code, deviceId: deviceId, name: deviceName, platform: "ios"))
        _ = tv
    }

    /// Fire-and-forget send. A send that fails is handled by the receive loop, not here, so a gesture
    /// never blocks and never throws into the UI.
    func send(_ frame: String?) {
        guard let frame, let socket else { return }
        socket.send(.string(frame)) { [weak self] error in
            guard error != nil else { return }
            Task { @MainActor in self?.lastError = "The TV stopped responding." }
        }
    }

    // Input, in the vocabulary the rest of the app speaks.
    func key(_ key: Wire.Key, hold: Bool = false) { send(Wire.key(key, hold: hold)) }
    func pointer(dx: Double, dy: Double) { send(Wire.pointer(dx: dx, dy: dy)) }
    func pointer(samples: [Wire.PointerSample]) { send(Wire.pointerBatch(samples)) }
    func click() { send(Wire.click()) }
    func longPress() { send(Wire.longPress()) }
    func scroll(dy: Double) { send(Wire.scroll(dy: dy)) }
    func type(_ text: String) { send(Wire.text(text)) }
    func backspace(_ count: Int = 1) { send(Wire.backspace(count)) }
    func submit() { send(Wire.submit()) }
    func voice(_ text: String) { send(Wire.voice(text)) }
    func launch(_ package: String) { send(Wire.launch(package: package)) }
    func command(_ action: String, query: String? = nil) { send(Wire.command(action, query: query)) }

    // MARK: - Receiving

    private func handle(_ text: String) async {
        guard let message = Wire.decode(text) else { return }
        switch message {
        case let .welcome(welcome), let .paired(welcome):
            store.save(token: welcome.token, for: welcome.device.isEmpty ? (tv?.id ?? "") : welcome.device)
            attempt = 0
            pairingRequired = false
            pairingAttempts = 0
            state = .paired(welcome.name)
            lastError = nil

        case .pairRequired:
            pairingRequired = true
            state = .awaitingPairing

        case let .error(code, message, attempts):
            pairingRequired = attempts != nil
            if let attempts {
                // The TV counts down from 5; mirror the number it reports rather than our own tally.
                pairingAttempts = Wire.maxPairingAttempts - attempts
            }
            lastError = message ?? Self.friendly(code)
            if code == "auth" {
                // The token was revoked or rotated away: ask to pair again rather than looping.
                if let id = tv?.id { store.clear(id) }
                state = .awaitingPairing
                pairingRequired = true
                wantsConnection = false
            } else if code == "pin" {
                state = .awaitingPairing
            } else if code == "busy" {
                state = .failed("The TV is busy. Try again in a few minutes.")
                wantsConnection = false
            } else {
                state = .failed(Self.friendly(code))
            }

        case let .state(state):
            tvState = state

        case let .apps(list):
            apps = list

        case let .toast(text):
            lastError = text

        case let .event(name):
            if name == "paired-elsewhere" || name == "revoked" {
                if let id = tv?.id { store.clear(id) }
                disconnect()
                state = .failed("This phone was removed from the TV.")
            }

        case let .pong(ts, _):
            let now = Wire.nowMillis()
            roundTripMs = Int(max(0, now - ts))
        }
    }

    private static func friendly(_ code: String) -> String {
        switch code {
        case "auth": return "This phone is no longer paired with that TV."
        case "busy": return "The TV is busy. Try again shortly."
        case "version": return "Update Couch Remote to talk to this TV."
        case "pin": return "That code was not accepted."
        case "not-allowed": return "That app is not on the TV's home screen."
        default: return "Something went wrong. Try reconnecting."
        }
    }
}
