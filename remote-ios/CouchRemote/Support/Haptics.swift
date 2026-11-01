import UIKit

/// Haptics, used sparingly: a tick on the D-pad, a firmer one on OK, a warning on refusal.
///
/// The generators are cached — creating one per tap adds ~8 ms on older phones, which is exactly the
/// kind of latency a remote cannot afford.
enum Haptics {
    private static let light = UIImpactFeedbackGenerator(style: .light)
    private static let medium = UIImpactFeedbackGenerator(style: .medium)
    private static let notice = UINotificationFeedbackGenerator()

    static func prepare() {
        light.prepare()
        medium.prepare()
    }

    static func tap() { light.impactOccurred(intensity: 0.7) }
    static func press() { medium.impactOccurred(intensity: 0.9) }
    static func refused() { notice.notificationOccurred(.warning) }
}
