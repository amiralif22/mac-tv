import Foundation
import SwiftUI
import RemoteKit

/// The app's single source of truth: what we found, what we are connected to, and what the TV says.
///
/// It owns the two collaborating pieces (discovery and the connection) so views never talk to the
/// network, and it remembers the last TV so a relaunch lands straight on the remote.
@MainActor
final class RemoteModel: ObservableObject {
    @Published private(set) var connection = RemoteConnection()
    @Published private(set) var discovery = TVDiscovery()
    @Published var sensitivity: Double {
        didSet { UserDefaults.standard.set(sensitivity, forKey: "couchremote.sensitivity") }
    }
    @Published var hapticsEnabled: Bool {
        didSet { UserDefaults.standard.set(hapticsEnabled, forKey: "couchremote.haptics") }
    }
    @Published private(set) var lastTV: DiscoveredTV?

    var isConnected: Bool { connection.state.isConnected }

    init() {
        let stored = UserDefaults.standard.double(forKey: "couchremote.sensitivity")
        sensitivity = stored == 0 ? 1.0 : stored
        hapticsEnabled = UserDefaults.standard.object(forKey: "couchremote.haptics") as? Bool ?? true
        lastTV = Self.loadLastTV()
    }

    // MARK: - Discovery

    func startDiscovery() { discovery.start() }
    func stopDiscovery() { discovery.stop() }

    func addManual(_ address: String) -> DiscoveredTV? {
        discovery.addManual(address: address)
    }

    // MARK: - Connection

    func connect(to tv: DiscoveredTV) {
        lastTV = tv
        Self.saveLastTV(tv)
        connection.connect(to: tv)
        Haptics.prepare()
    }

    func connectToLast() {
        if let lastTV { connect(to: lastTV) }
    }

    func disconnect() {
        connection.disconnect()
        Self.clearLastTV()
        lastTV = nil
    }

    func submitPairingCode(_ code: String) {
        connection.submitPairingCode(PairingCode.sanitize(code))
    }

    /// An immediate retry after the app comes back to the foreground.
    func foregrounded() {
        connection.retryNow()
    }

    // MARK: - Persistence

    private static let lastTVKey = "couchremote.lastTV"

    private static func saveLastTV(_ tv: DiscoveredTV) {
        let payload = ["id": tv.id, "name": tv.name, "host": tv.host, "port": "\(tv.port)", "model": tv.model]
        UserDefaults.standard.set(payload, forKey: lastTVKey)
    }

    private static func loadLastTV() -> DiscoveredTV? {
        guard
            let payload = UserDefaults.standard.dictionary(forKey: lastTVKey) as? [String: String],
            let id = payload["id"], let host = payload["host"],
            let port = payload["port"].flatMap(Int.init)
        else { return nil }
        return DiscoveredTV(
            id: id,
            name: payload["name"] ?? host,
            model: payload["model"] ?? "Android TV",
            host: host,
            port: port,
            version: 1,
            pairing: false,
            manual: true
        )
    }

    private static func clearLastTV() {
        UserDefaults.standard.removeObject(forKey: lastTVKey)
    }
}
