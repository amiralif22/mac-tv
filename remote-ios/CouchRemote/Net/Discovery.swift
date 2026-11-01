import Foundation
import Darwin

/// A TV found on the local network, from its Bonjour advertisement (`_couchremote._tcp`).
struct DiscoveredTV: Identifiable, Equatable, Hashable {
    let id: String            // stable device UUID from TXT `id`
    let name: String          // human name, e.g. "Living room"
    let model: String         // "Android TV", "Tablet"…
    let host: String          // resolved IP or hostname
    let port: Int
    let version: Int
    let pairing: Bool         // the TV is showing a pairing code right now
    let manual: Bool          // added by hand, not discovered

    private var authority: String { host.contains(":") ? "[\(host)]" : host }

    /// `ws://<host>:<port>/remote`, with brackets around IPv6 literals.
    var websocketURL: URL? {
        var components = URLComponents()
        components.scheme = "ws"
        components.host = host
        components.port = port
        components.path = "/remote"
        return components.url
    }

    /// Icons come over HTTP from the same port: `GET /icon?pkg=<package>`.
    func iconURL(for package: String) -> URL? {
        var components = URLComponents()
        components.scheme = "http"
        components.host = host
        components.port = port
        components.path = "/icon"
        components.queryItems = [URLQueryItem(name: "pkg", value: package)]
        return components.url
    }

    var displayAddress: String { "\(authority):\(port)" }
}

/// Browses for TVs with Bonjour and republishes them.
///
/// `NetService` rather than `NWBrowser` because it hands over a resolved host and port directly,
/// which is exactly what a WebSocket needs. Discovery is a convenience: the screen always offers
/// manual entry, because guest networks and some mesh systems drop multicast entirely.
@MainActor
final class TVDiscovery: NSObject, ObservableObject {
    @Published private(set) var tvs: [DiscoveredTV] = []
    @Published private(set) var browsing = false
    @Published private(set) var manual: [DiscoveredTV] = []

    private let browser = NetServiceBrowser()
    private var resolving: [NetService] = []

    override init() {
        super.init()
        browser.delegate = self
    }

    func start() {
        guard !browsing else { return }
        browsing = true
        browser.searchForServices(ofType: "_couchremote._tcp.", inDomain: "local.")
    }

    func stop() {
        browser.stop()
        resolving.forEach { $0.stop() }
        resolving.removeAll()
        browsing = false
        tvs = []
    }

    func refresh() {
        stop()
        start()
    }

    /// Adds a TV by address ("192.168.1.20", "192.168.1.20:8765", "[fe80::1]:8765"). Nil if unparsable.
    @discardableResult
    func addManual(address: String) -> DiscoveredTV? {
        let trimmed = address.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        var host = trimmed
        var port = 8765
        if trimmed.hasPrefix("[") {                       // [fe80::1]:8765
            guard let closing = trimmed.firstIndex(of: "]") else { return nil }
            host = String(trimmed[trimmed.index(after: trimmed.startIndex)..<closing])
            let rest = trimmed[trimmed.index(after: closing)...]
            if rest.hasPrefix(":"), let parsed = Int(rest.dropFirst()) { port = parsed }
        } else if trimmed.filter({ $0 == ":" }).count == 1,  // host:port
                  let separator = trimmed.lastIndex(of: ":"),
                  let parsed = Int(trimmed[trimmed.index(after: separator)...]) {
            host = String(trimmed[..<separator])
            port = parsed
        }

        guard !host.isEmpty, (1...65535).contains(port) else { return nil }
        let tv = DiscoveredTV(
            id: "manual:\(host):\(port)",
            name: host,
            model: "Entered by hand",
            host: host,
            port: port,
            version: 1,
            pairing: false,
            manual: true
        )
        manual = manual.filter { $0.id != tv.id } + [tv]
        return tv
    }

    func forgetManual() { manual = [] }

    fileprivate func resolved(_ service: NetService) {
        let txt = Self.txt(from: service)
        let host = service.hostName?.trimmingCharacters(in: CharacterSet(charactersIn: "."))
            ?? Self.ipv4(from: service)
        guard let host, !host.isEmpty, service.port > 0 else { return }

        let tv = DiscoveredTV(
            id: txt["id"] ?? service.name,
            name: txt["name"] ?? service.name,
            model: txt["model"] ?? "Android TV",
            host: host,
            port: service.port,
            version: txt["v"].flatMap(Int.init) ?? 1,
            pairing: txt["pair"] == "1",
            manual: false
        )

        // Keep the list stable: a re-resolve replaces the entry rather than appending a duplicate.
        var updated = tvs.filter { $0.id != tv.id }
        updated.append(tv)
        tvs = updated.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    private static func txt(from service: NetService) -> [String: String] {
        guard let data = service.txtRecordData() else { return [:] }
        return NetService.dictionary(fromTXTRecord: data).reduce(into: [String: String]()) { out, entry in
            out[entry.key] = String(data: entry.value, encoding: .utf8)
        }
    }

    /// Some TVs publish no hostname; the A record is in `addresses`. Take the first IPv4 literal.
    private static func ipv4(from service: NetService) -> String? {
        service.addresses?.lazy.compactMap { data -> String? in
            data.withUnsafeBytes { raw -> String? in
                guard let base = raw.baseAddress else { return nil }
                let family = base.assumingMemoryBound(to: sockaddr.self).pointee.sa_family
                guard family == sa_family_t(AF_INET) else { return nil }
                var address = base.assumingMemoryBound(to: sockaddr_in.self).pointee.sin_addr
                var buffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
                guard inet_ntop(AF_INET, &address, &buffer, socklen_t(INET_ADDRSTRLEN)) != nil else { return nil }
                return String(cString: buffer)
            }
        }.first
    }
}

extension TVDiscovery: NetServiceBrowserDelegate {
    nonisolated func netServiceBrowser(_ browser: NetServiceBrowser, didFind service: NetService, moreComing: Bool) {
        Task { @MainActor in
            service.delegate = self
            resolving.append(service)
            service.resolve(withTimeout: 4)
        }
    }

    nonisolated func netServiceBrowser(_ browser: NetServiceBrowser, didRemove service: NetService, moreComing: Bool) {
        Task { @MainActor in
            resolving.removeAll { $0 == service }
            tvs.removeAll { $0.id == service.name }
        }
    }

    nonisolated func netServiceBrowserDidStopSearch(_ browser: NetServiceBrowser) {
        Task { @MainActor in
            browsing = false
            tvs = []
        }
    }
}

extension TVDiscovery: NetServiceDelegate {
    nonisolated func netServiceDidResolveAddress(_ sender: NetService) {
        Task { @MainActor in
            resolved(sender)
            resolving.removeAll { $0 == sender }
        }
    }

    nonisolated func netService(_ sender: NetService, didNotResolve errorDict: [String: NSNumber]) {
        Task { @MainActor in
            resolving.removeAll { $0 == sender }
        }
    }
}
