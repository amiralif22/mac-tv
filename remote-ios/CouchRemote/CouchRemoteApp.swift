import SwiftUI

@main
struct CouchRemoteApp: App {
    @StateObject private var model = RemoteModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView(model: model)
                .preferredColorScheme(.dark)
                .onChange(of: scenePhase) { phase in
                    if phase == .active { model.foregrounded() }
                }
        }
    }
}

/// Three states, one screen each: find a TV, prove you may drive it, drive it.
struct RootView: View {
    @ObservedObject var model: RemoteModel

    var body: some View {
        Group {
            if model.isConnected {
                RemoteScreen(model: model)
                    .transition(.opacity)
            } else if model.connection.pairingRequired || isAwaitingPairing {
                PairSheet(
                    connection: model.connection,
                    tvName: model.lastTV?.name ?? "your TV",
                    onCancel: {
                        model.disconnect()
                    }
                )
                .transition(.opacity)
            } else {
                DevicesScreen(model: model)
                    .transition(.opacity)
            }
        }
        .animation(Theme.sheet, value: model.isConnected)
        .background(Theme.canvas.ignoresSafeArea())
    }

    private var isAwaitingPairing: Bool {
        if case .awaitingPairing = model.connection.state { return true }
        return false
    }
}
