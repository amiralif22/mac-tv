import Foundation
import Security

/// Where pairing tokens live.
///
/// A token is a capability: whoever holds it can drive the TV. It therefore goes in the keychain,
/// marked as this-device-only so it never rides an iCloud backup onto another phone.
protocol TokenStore {
    func token(for deviceId: String) -> String?
    func save(token: String, for deviceId: String)
    func clear(_ deviceId: String)
}

struct KeychainTokenStore: TokenStore {
    private let service = "dev.couchtv.remote.tokens"

    func token(for deviceId: String) -> String? {
        var query = baseQuery(deviceId)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data
        else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func save(token: String, for deviceId: String) {
        let data = Data(token.utf8)
        let query = baseQuery(deviceId)
        let attributes: [String: Any] = [kSecValueData as String: data]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var insert = query
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(insert as CFDictionary, nil)
        }
    }

    func clear(_ deviceId: String) {
        SecItemDelete(baseQuery(deviceId) as CFDictionary)
    }

    private func baseQuery(_ deviceId: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: deviceId,
        ]
    }
}

/// For the simulator and for tests: nothing survives a launch, and nothing is written to disk.
final class InMemoryTokenStore: TokenStore {
    private var tokens: [String: String] = [:]

    func token(for deviceId: String) -> String? { tokens[deviceId] }
    func save(token: String, for deviceId: String) { tokens[deviceId] = token }
    func clear(_ deviceId: String) { tokens[deviceId] = nil }
}
