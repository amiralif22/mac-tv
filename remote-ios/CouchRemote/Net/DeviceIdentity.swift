import Foundation
import UIKit

/// Who this phone is, for the TV's device list.
///
/// The identifier is random and local: it is not IDFA, not the vendor identifier, and never leaves
/// the LAN. It only has to be stable enough that the TV can remember which phone was paired.
enum DeviceIdentity {
    private static let key = "couchremote.deviceId"

    static func loadOrCreate() -> String {
        if let existing = UserDefaults.standard.string(forKey: key), !existing.isEmpty {
            return existing
        }
        let fresh = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        UserDefaults.standard.set(fresh, forKey: key)
        return fresh
    }

    static func currentName() -> String {
        UIDevice.current.name.isEmpty ? "iPhone" : UIDevice.current.name
    }
}
