// swift-tools-version:5.9
import PackageDescription

/// The platform-neutral half of Couch Remote: the wire protocol, pointer ballistics and pairing
/// input rules. Nothing here imports UIKit or Network, so it builds and is tested on macOS and Linux
/// (`swift test`) — which is what CI does for every pull request.
let package = Package(
    name: "RemoteKit",
    platforms: [.iOS(.v15), .macOS(.v12)],
    products: [
        .library(name: "RemoteKit", targets: ["RemoteKit"]),
    ],
    targets: [
        .target(name: "RemoteKit"),
        .testTarget(name: "RemoteKitTests", dependencies: ["RemoteKit"]),
    ]
)
