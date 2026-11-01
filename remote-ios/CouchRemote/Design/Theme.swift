import SwiftUI

/// The remote's design tokens, taken from the same scale as Mac TV and the Android launcher: the
/// glass, the white platter, the phosphor accent, and type large enough to read while looking at a
/// television rather than the phone.
enum Theme {
    // Colour, in the dark palette the launcher uses.
    static let canvas = Color(hex: 0x07080B)
    static let panel = Color(hex: 0x0F1116)
    static let raised = Color(hex: 0x161920)
    static let ink = Color(hex: 0xF5F5F7)
    static let dim = Color(hex: 0x9EA3AF)
    static let faint = Color(hex: 0x6E7482)
    static let phosphor = Color(hex: 0x3DDC84)
    static let warning = Color(hex: 0xFF453A)
    static let hairline = Color.white.opacity(0.12)

    // Type. The remote is held closer than a TV, so the launcher's scale is compressed, not copied.
    static func title(_ size: CGFloat = 22) -> Font { .system(size: size, weight: .bold, design: .rounded) }
    static let label = Font.system(size: 11, weight: .semibold)
    static let mono = Font.system(size: 12, weight: .medium, design: .monospaced)

    // Motion: 200 ms for a press, 320 ms for a sheet. Same as the TV, so the two feel related.
    static let press = Animation.easeOut(duration: 0.2)
    static let sheet = Animation.easeInOut(duration: 0.32)

    static let corner: CGFloat = 18
}

extension Color {
    /// `Color(hex: 0x3DDC84)` — shorter than spelling out three floats, and reviewable against the
    /// palette table in `ARCHITECTURE.md`.
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }

    init(hexString: String, fallback: Color = Theme.faint) {
        let cleaned = hexString.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        guard cleaned.count == 6, let value = UInt32(cleaned, radix: 16) else {
            self = fallback
            return
        }
        self.init(hex: value)
    }
}

/// The glass panel the remote's controls sit on: an edge of light at the top, a soft shadow below.
struct GlassPanel: ViewModifier {
    var radius: CGFloat = Theme.corner

    func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .fill(Color.white.opacity(0.05))
                    .overlay(
                        RoundedRectangle(cornerRadius: radius, style: .continuous)
                            .strokeBorder(
                                LinearGradient(
                                    colors: [Color.white.opacity(0.18), Color.white.opacity(0.04)],
                                    startPoint: .top,
                                    endPoint: .bottom
                                ),
                                lineWidth: 1
                            )
                    )
            )
    }
}

extension View {
    func glass(radius: CGFloat = Theme.corner) -> some View { modifier(GlassPanel(radius: radius)) }
}

/// A button that behaves like a piece of hardware: it depresses, it ticks, and it never waits for
/// the network before it shows that it was pressed.
struct HardwareButton<Label: View>: View {
    var action: () -> Void
    var haptic: () -> Void = Haptics.press
    var height: CGFloat = 56
    @ViewBuilder var label: Label

    @State private var pressed = false

    var body: some View {
        label
            .frame(maxWidth: .infinity, minHeight: height)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.white.opacity(pressed ? 0.16 : 0.07))
            )
            .scaleEffect(pressed ? 0.96 : 1)
            .animation(Theme.press, value: pressed)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        guard !pressed else { return }
                        pressed = true
                        haptic()
                    }
                    .onEnded { _ in
                        pressed = false
                        action()
                    }
            )
            .accessibilityAddTraits(.isButton)
    }
}
