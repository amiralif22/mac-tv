import SwiftUI

/// A keyboard sheet that types into the TV's focused field.
///
/// It sends one `text` frame per keystroke change and uses `\b` for deletion: no IME state is
/// mirrored, so what the TV has is always exactly what was sent.
struct KeyboardSheet: View {
    @ObservedObject var connection: RemoteConnection
    @Environment(\.dismiss) private var dismiss
    @State private var buffer = ""
    @State private var lastSent = ""
    @FocusState private var focused: Bool

    var body: some View {
        ZStack {
            Theme.panel.ignoresSafeArea()

            VStack(spacing: 14) {
                HStack {
                    Text("Type on the TV")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(Theme.ink)
                    Spacer()
                    Button("Done") { dismiss() }
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.phosphor)
                }

                TextField("Search, a title, a password…", text: $buffer)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($focused)
                    .font(.system(size: 15, design: .monospaced))
                    .foregroundColor(Theme.ink)
                    .padding(12)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.06)))
                    .onChange(of: buffer) { newValue in
                        // Only the difference is sent: an appended run of characters, or deletions.
                        if newValue.hasPrefix(lastSent), newValue.count > lastSent.count {
                            connection.type(String(newValue.dropFirst(lastSent.count)))
                        } else if lastSent.hasPrefix(newValue), lastSent.count > newValue.count {
                            connection.backspace(lastSent.count - newValue.count)
                        } else if !newValue.isEmpty {
                            // An edit in the middle: clear and retype is the honest, predictable option.
                            connection.backspace(lastSent.count)
                            connection.type(newValue)
                        }
                        lastSent = newValue
                    }
                    .onSubmit { connection.submit() }

                HStack(spacing: 8) {
                    Button {
                        connection.submit()
                        Haptics.press()
                    } label: {
                        Text("Go")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(RoundedRectangle(cornerRadius: 12).fill(Theme.phosphor.opacity(0.2)))
                            .foregroundColor(Theme.phosphor)
                    }
                    .buttonStyle(.plain)

                    Button {
                        connection.backspace(4)
                        buffer = String(buffer.dropLast(min(4, buffer.count)))
                        lastSent = buffer
                        Haptics.tap()
                    } label: {
                        Text("Left of cursor")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.06)))
                            .foregroundColor(Theme.dim)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(18)
        }
        .preferredColorScheme(.dark)
        .onAppear { focused = true }
    }
}
