import SwiftUI
import RemoteKit

/// The pairing code, entered once per TV.
///
/// The field is a single hidden text field behind six boxes: typing works, pasting works, and the
/// boxes show progress without ever guessing what the TV will accept.
struct PairSheet: View {
    @ObservedObject var connection: RemoteConnection
    let tvName: String
    let onCancel: () -> Void

    @State private var code = ""
    @FocusState private var focused: Bool

    var body: some View {
        ZStack {
            Theme.canvas.ignoresSafeArea()

            VStack(spacing: 20) {
                VStack(spacing: 6) {
                    Text("Pair with \(tvName)")
                        .font(Theme.title(20))
                        .foregroundColor(Theme.ink)
                    Text("The six-digit code is on the TV, under Settings › Phone remote.")
                        .font(.system(size: 13))
                        .foregroundColor(Theme.dim)
                        .multilineTextAlignment(.center)
                }

                // Six boxes drawn by us, with one real text field on top: pasting the whole code,
                // the numeric keypad and the system's one-time-code autofill all keep working.
                ZStack {
                    HStack(spacing: 10) {
                        ForEach(0..<PairingCode.length, id: \.self) { index in
                            box(at: index)
                        }
                    }
                    TextField("", text: $code)
                        .keyboardType(.numberPad)
                        .textContentType(.oneTimeCode)
                        .focused($focused)
                        .foregroundColor(.clear)
                        .tint(.clear)
                        .accentColor(.clear)
                        .onChange(of: code) { newValue in
                            let sanitized = PairingCode.sanitize(newValue)
                            if sanitized != newValue { code = sanitized }
                            // Six digits means the code is complete: send it once, as soon as it lands.
                            if PairingCode.isComplete(sanitized) {
                                connection.submitPairingCode(sanitized)
                                Haptics.press()
                            }
                        }
                        .accessibilityLabel("Pairing code")
                }

                if let hint = PairingCode.hint(for: code, wrongAttempts: connection.pairingAttempts) ?? connection.lastError {
                    Text(hint)
                        .font(.system(size: 12))
                        .foregroundColor(Theme.warning)
                        .multilineTextAlignment(.center)
                }

                HStack(spacing: 10) {
                    Button("Cancel") { onCancel() }
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(Theme.dim)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 11)
                        .background(Capsule().fill(Color.white.opacity(0.06)))

                    Button {
                        connection.submitPairingCode(code)
                        Haptics.press()
                    } label: {
                        Text("Pair")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(PairingCode.isComplete(code) ? Theme.canvas : Theme.faint)
                            .padding(.horizontal, 22)
                            .padding(.vertical, 11)
                            .background(
                                Capsule().fill(PairingCode.isComplete(code) ? Theme.phosphor : Color.white.opacity(0.06))
                            )
                    }
                    .disabled(!PairingCode.isComplete(code))
                }
            }
            .padding(24)
        }
        .preferredColorScheme(.dark)
        .onAppear { focused = true }
    }

    private func box(at index: Int) -> some View {
        let digits = Array(PairingCode.sanitize(code))
        let filled = index < digits.count
        return Text(filled ? String(digits[index]) : " ")
            .font(.system(size: 24, weight: .semibold, design: .monospaced))
            .foregroundColor(Theme.ink)
            .frame(width: 46, height: 58)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white.opacity(filled ? 0.10 : 0.05))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(index == min(digits.count, PairingCode.length - 1) ? Theme.phosphor.opacity(0.6) : Theme.hairline,
                                  lineWidth: 1)
            )
    }
}
