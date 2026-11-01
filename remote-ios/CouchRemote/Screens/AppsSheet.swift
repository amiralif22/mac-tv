import SwiftUI
import UIKit
import RemoteKit

/// The TV's home-screen apps, as shortcuts, with icons fetched over HTTP from the same port.
///
/// Tapping one asks the TV to launch it; if the app is not on the home screen the TV answers
/// `error`/`not-allowed`, which the remote shows rather than swallowing.
struct AppsSheet: View {
    @ObservedObject var connection: RemoteConnection
    let apps: [Wire.TVApp]
    @Environment(\.dismiss) private var dismiss

    private let columns = [GridItem(.adaptive(minimum: 84), spacing: 12)]

    var body: some View {
        ZStack {
            Theme.panel.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Text("On your TV")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(Theme.ink)
                        Spacer()
                        Button("Close") { dismiss() }
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(Theme.dim)
                    }

                    if apps.isEmpty {
                        Text("The TV has not sent its app list yet. It appears once the remote is paired.")
                            .font(.system(size: 13))
                            .foregroundColor(Theme.dim)
                    } else {
                        LazyVGrid(columns: columns, spacing: 14) {
                            ForEach(sorted) { app in
                                Button {
                                    connection.launch(app.package)
                                    Haptics.press()
                                    dismiss()
                                } label: {
                                    VStack(spacing: 6) {
                                        AppIcon(app: app, connection: connection)
                                        Text(app.name)
                                            .font(.system(size: 11, weight: .medium))
                                            .foregroundColor(Theme.dim)
                                            .lineLimit(1)
                                    }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(18)
            }
        }
        .preferredColorScheme(.dark)
    }

    /// Favourites first, then alphabetical — the same order the TV shows.
    private var sorted: [Wire.TVApp] {
        apps.sorted {
            if $0.favourite != $1.favourite { return $0.favourite }
            return $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
        }
    }
}

private struct AppIcon: View {
    let app: Wire.TVApp
    let connection: RemoteConnection
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(hexString: app.color).opacity(0.35))
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            } else {
                Text(initials)
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundColor(.white.opacity(0.9))
            }
        }
        .frame(width: 58, height: 58)
        .task(id: app.package) { await load() }
    }

    private var initials: String {
        String(app.name.prefix(2)).uppercased()
    }

    private func load() async {
        guard
            let url = connection.tv?.iconURL(for: app.package),
            let (data, response) = try? await URLSession.shared.data(from: url),
            (response as? HTTPURLResponse)?.statusCode == 200,
            let decoded = UIImage(data: data)
        else { return }
        image = decoded
    }
}
