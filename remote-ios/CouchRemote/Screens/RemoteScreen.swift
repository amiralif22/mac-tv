import SwiftUI
import RemoteKit

/// The remote proper. Everything here is reachable with one thumb, in the order a hand expects:
/// touchpad first, then the D-pad, then the system row, and transport only when something is playing.
struct RemoteScreen: View {
    @ObservedObject var model: RemoteModel
    @State private var showKeyboard = false
    @State private var showApps = false
    @State private var showVoice = false
    @State private var showSettings = false

    private var connection: RemoteConnection { model.connection }

    var body: some View {
        ZStack {
            Theme.canvas.ignoresSafeArea()

            VStack(spacing: 14) {
                header

                TouchpadView(model: model)
                    .frame(maxHeight: .infinity)

                dpad

                primaryRow

                if let media = connection.tvState.media {
                    transport(for: media)
                }

                systemRow
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 10)
        }
        .preferredColorScheme(.dark)
        .sheet(isPresented: $showKeyboard) {
            KeyboardSheet(connection: connection)
        }
        .sheet(isPresented: $showApps) {
            AppsSheet(connection: connection, apps: connection.apps)
        }
        .sheet(isPresented: $showVoice) {
            VoiceSheet { text in
                connection.voice(text)
                connection.submit()
            }
        }
        .sheet(isPresented: $showSettings) {
            RemoteSettingsSheet(model: model)
        }
    }

    // MARK: - Pieces

    private var header: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(statusColor)
                .frame(width: 8, height: 8)

            VStack(alignment: .leading, spacing: 2) {
                Text(connection.state.title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.ink)
                    .lineLimit(1)
                Text(subtitle)
                    .font(Theme.mono)
                    .foregroundColor(Theme.faint)
                    .lineLimit(1)
            }

            Spacer()

            Button {
                showSettings = true
                Haptics.tap()
            } label: {
                Image(systemName: "slider.horizontal.3")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(Theme.dim)
                    .frame(width: 34, height: 34)
                    .glass(radius: 12)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remote settings")
        }
        .padding(.top, 6)
    }

    private var subtitle: String {
        var parts: [String] = []
        if let rtt = connection.roundTripMs { parts.append("\(rtt) ms") }
        if let screen = connection.tvState.screen { parts.append(screen) }
        if let volume = connection.tvState.volume {
            parts.append(connection.tvState.muted == true ? "muted" : "\(Int(volume * 100))%")
        }
        return parts.isEmpty ? "—" : parts.joined(separator: " · ")
    }

    private var statusColor: Color {
        switch connection.state {
        case .paired: return Theme.phosphor
        case .connecting, .handshaking: return Color.orange
        case .reconnecting: return Color.orange
        case .awaitingPairing: return Theme.warning
        case .failed: return Theme.warning
        case .idle: return Theme.faint
        }
    }

    private var dpad: some View {
        VStack(spacing: 8) {
            arrow(.up)
            HStack(spacing: 8) {
                arrow(.left)
                HardwareButton(action: {
                    connection.key(.ok)
                }, height: 74) {
                    Text("OK")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(Theme.ink)
                }
                .frame(width: 108)
                arrow(.right)
            }
            arrow(.down)
        }
    }

    private func arrow(_ key: Protocol.Key) -> some View {
        HardwareButton(action: {
            connection.key(key)
        }, haptic: { Haptics.tap() }, height: 48) {
            Image(systemName: key == .up ? "chevron.up" : key == .down ? "chevron.down" : key == .left ? "chevron.left" : "chevron.right")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Theme.ink)
        }
        .frame(maxWidth: 108)
    }

    private var primaryRow: some View {
        HStack(spacing: 8) {
            HardwareButton(action: { connection.key(.back) }, haptic: { Haptics.tap() }) {
                pill("chevron.backward", "Back")
            }
            HardwareButton(action: { connection.key(.home) }, haptic: { Haptics.tap() }) {
                pill("house", "Home")
            }
            HardwareButton(action: { showKeyboard = true }) {
                pill("keyboard", "Keys")
            }
            HardwareButton(action: { showVoice = true }) {
                pill("mic", "Voice")
            }
            HardwareButton(action: { showApps = true }) {
                pill("square.grid.2x2", "Apps")
            }
        }
    }

    private func transport(for media: Protocol.TVState.Media) -> some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Text(media.title)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Theme.ink)
                    .lineLimit(1)
                Spacer()
                if let subtitle = media.subtitle {
                    Text(subtitle)
                        .font(Theme.mono)
                        .foregroundColor(Theme.faint)
                        .lineLimit(1)
                }
            }

            HStack(spacing: 8) {
                HardwareButton(action: { connection.key(.prev) }, haptic: { Haptics.tap() }, height: 44) {
                    Image(systemName: "backward.end.fill").foregroundColor(Theme.ink)
                }
                HardwareButton(action: { connection.key(.rewind) }, haptic: { Haptics.tap() }, height: 44) {
                    Image(systemName: "gobackward.15").foregroundColor(Theme.ink)
                }
                HardwareButton(action: { connection.key(.playpause) }, height: 44) {
                    Image(systemName: media.playing ? "pause.fill" : "play.fill").foregroundColor(Theme.ink)
                }
                HardwareButton(action: { connection.key(.forward) }, haptic: { Haptics.tap() }, height: 44) {
                    Image(systemName: "goforward.15").foregroundColor(Theme.ink)
                }
                HardwareButton(action: { connection.key(.next) }, haptic: { Haptics.tap() }, height: 44) {
                    Image(systemName: "forward.end.fill").foregroundColor(Theme.ink)
                }
            }
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 10)
        .glass(radius: 14)
    }

    private var systemRow: some View {
        HStack(spacing: 8) {
            HardwareButton(action: { connection.key(.volup) }, haptic: { Haptics.tap() }, height: 44) {
                Image(systemName: "speaker.plus").foregroundColor(Theme.ink)
            }
            HardwareButton(action: { connection.key(.voldown) }, haptic: { Haptics.tap() }, height: 44) {
                Image(systemName: "speaker.minus").foregroundColor(Theme.ink)
            }
            HardwareButton(action: { connection.key(.mute) }, haptic: { Haptics.tap() }, height: 44) {
                Image(systemName: connection.tvState.muted == true ? "speaker.slash.fill" : "speaker.slash")
                    .foregroundColor(connection.tvState.muted == true ? Theme.phosphor : Theme.ink)
            }
            HardwareButton(action: { connection.key(.power) }, haptic: { Haptics.tap() }, height: 44) {
                Image(systemName: "power").foregroundColor(Theme.ink)
            }
        }
    }

    private func pill(_ symbol: String, _ title: String) -> some View {
        VStack(spacing: 4) {
            Image(systemName: symbol).font(.system(size: 15, weight: .semibold))
            Text(title).font(Theme.label)
        }
        .foregroundColor(Theme.ink)
    }
}

/// Sensitivity and haptics, plus the way out of a pairing.
struct RemoteSettingsSheet: View {
    @ObservedObject var model: RemoteModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Theme.panel.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 20) {
                Text("Remote")
                    .font(Theme.title(20))
                    .foregroundColor(Theme.ink)

                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Pointer speed").foregroundColor(Theme.dim)
                        Spacer()
                        Text(String(format: "%.1f×", model.sensitivity))
                            .font(Theme.mono)
                            .foregroundColor(Theme.ink)
                    }
                    .font(.system(size: 14))
                    Slider(value: $model.sensitivity, in: 0.4...2.5)
                        .tint(Theme.phosphor)
                }

                Toggle(isOn: $model.hapticsEnabled) {
                    Text("Haptics").foregroundColor(Theme.dim)
                }
                .tint(Theme.phosphor)

                Spacer()

                Button(role: .destructive) {
                    model.disconnect()
                    dismiss()
                } label: {
                    Text("Forget this TV")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .tint(Theme.warning.opacity(0.2))
                .foregroundColor(Theme.warning)
            }
            .font(.system(size: 14))
            .padding(20)
        }
    }
}
