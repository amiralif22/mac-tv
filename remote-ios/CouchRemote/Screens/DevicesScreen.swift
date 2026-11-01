import SwiftUI

/// First screen: find a TV, or type its address.
///
/// Discovery is presented as a list that fills itself in, with the manual field always visible — a
/// list that says "no TVs found" and offers nothing else is the single most common complaint about
/// companion remotes, and the fix costs one text field.
struct DevicesScreen: View {
    @ObservedObject var model: RemoteModel
    @State private var address = ""
    @State private var manualError: String?

    private var all: [DiscoveredTV] { model.discovery.tvs + model.discovery.manual }

    var body: some View {
        ZStack {
            Theme.canvas.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    header

                    if all.isEmpty {
                        emptyState
                    } else {
                        VStack(spacing: 10) {
                            ForEach(all) { tv in
                                TVRow(tv: tv) { model.connect(to: tv) }
                            }
                        }
                    }

                    manualEntry

                    Text("Both devices must be on the same Wi-Fi. The remote never leaves your network.")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.faint)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(20)
            }
        }
        .preferredColorScheme(.dark)
        .onAppear { model.startDiscovery() }
        .onDisappear { model.stopDiscovery() }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Circle()
                    .fill(model.discovery.browsing ? Theme.phosphor : Theme.faint)
                    .frame(width: 7, height: 7)
                Text(model.discovery.browsing ? "Looking for TVs" : "Discovery stopped")
                    .font(Theme.label)
                    .tracking(0.7)
                    .foregroundColor(Theme.faint)
                Spacer()
                Button {
                    model.discovery.refresh()
                    Haptics.tap()
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.dim)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Search again")
            }

            Text("Couch Remote")
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundColor(Theme.ink)
            Text("Choose the TV you want to drive.")
                .font(.system(size: 14))
                .foregroundColor(Theme.dim)
        }
        .padding(.top, 12)
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("No TVs yet")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(Theme.ink)
            Text("Open Couch TV on the television, then Settings › Phone remote. If the TV does not appear here, type the address it shows.")
                .font(.system(size: 13))
                .foregroundColor(Theme.dim)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .glass()
    }

    private var manualEntry: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Add by address")
                .font(Theme.label)
                .tracking(0.7)
                .foregroundColor(Theme.faint)

            HStack(spacing: 8) {
                TextField("192.168.1.42:8765", text: $address)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .font(Theme.mono)
                    .foregroundColor(Theme.ink)
                    .padding(12)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.06)))

                Button {
                    if let tv = model.addManual(address) {
                        address = ""
                        manualError = nil
                        model.connect(to: tv)
                    } else {
                        manualError = "That is not an address. Try 192.168.1.42 or 192.168.1.42:8765."
                        Haptics.refused()
                    }
                } label: {
                    Text("Add")
                        .font(.system(size: 14, weight: .semibold))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Theme.phosphor.opacity(0.18)))
                        .foregroundColor(Theme.phosphor)
                }
                .buttonStyle(.plain)
            }

            if let manualError {
                Text(manualError)
                    .font(.system(size: 12))
                    .foregroundColor(Theme.warning)
            }
        }
    }
}

private struct TVRow: View {
    let tv: DiscoveredTV
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color.white.opacity(0.06))
                    Image(systemName: "tv")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundColor(Theme.dim)
                }
                .frame(width: 44, height: 34)

                VStack(alignment: .leading, spacing: 3) {
                    Text(tv.name)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(Theme.ink)
                    Text("\(tv.model) · \(tv.displayAddress)")
                        .font(Theme.mono)
                        .foregroundColor(Theme.faint)
                        .lineLimit(1)
                }

                Spacer()

                if tv.pairing {
                    Text("Pairing")
                        .font(Theme.label)
                        .foregroundColor(Theme.phosphor)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Capsule().fill(Theme.phosphor.opacity(0.16)))
                } else {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Theme.faint)
                }
            }
            .padding(12)
            .glass(radius: 14)
        }
        .buttonStyle(.plain)
        .accessibilityHint("Connects to this TV")
    }
}
