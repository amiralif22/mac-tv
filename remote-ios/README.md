# Couch Remote for iPhone and iPad

The SwiftUI remote for **Couch TV**, the Android TV launcher in this repository. It finds the TV over
Bonjour, pairs once with a six-digit code, and then behaves like hardware: D-pad, a touchpad that
moves a real cursor on the TV, transport controls, volume, keyboard and voice.

Minimum iOS 15. Dark only, because it is used in the dark, with the same type scale and glass as the
launcher and the Mac app.

## Build

The project file is generated, so nothing in a pull request is a `.pbxproj` diff:

```bash
brew install xcodegen
cd remote-ios
xcodegen generate
open CouchRemote.xcodeproj      # ⌘R on a device or simulator
```

The protocol library is a local Swift package, `RemoteKit/`, which builds without Xcode and is tested
on its own:

```bash
cd remote-ios/RemoteKit
swift test                      # protocol codec, pointer ballistics, pairing input
```

CI runs both: `swift test` on every platform, and `xcodebuild` for the app on a macOS runner.

## What is where

| Path | What it does |
|---|---|
| `CouchRemote/CouchRemoteApp.swift` | Entry point and the three-state router: find a TV, pair, drive it |
| `CouchRemote/Net/Discovery.swift` | Bonjour browsing plus manual `host:port` entry |
| `CouchRemote/Net/RemoteConnection.swift` | WebSocket client: handshake, pairing, reconnect with backoff, 15 s ping |
| `CouchRemote/Net/TokenStore.swift` | Pairing tokens in the keychain, this-device-only |
| `CouchRemote/Screens/TouchpadView.swift` | UIKit touchpad: pointer samples at 60 Hz, two-finger scroll, tap and long-press |
| `CouchRemote/Screens/RemoteScreen.swift` | D-pad, transport, system row, sheets |
| `CouchRemote/Screens/PairSheet.swift` | Six-box code entry with paste and autofill |
| `RemoteKit/Sources/RemoteKit/Protocol.swift` | The v1 codec, shared with the TV's Kotlin implementation |

`shared/remote-protocol.md` is the specification; if this app and that document disagree, the
document wins.

## Behaviour worth knowing

- **Relative pointer.** The touchpad sends `dx`/`dy` as fractions of the screen; the TV integrates
  them, so a dropped frame is a small delay rather than a jumping cursor.
- **One frame per display refresh.** Samples accumulate and are flushed by a `CADisplayLink`, at most
  eight per message.
- **Reconnect politely.** 0.5 → 10 s exponential backoff with jitter, an immediate retry when the app
  returns to the foreground, and no hammering while waiting for a pairing code.
- **Voice stays on the phone.** Speech is transcribed by iOS and sent as text; no audio crosses the
  network, and the microphone is released the moment the sheet closes.
- **Nothing is stored about what you watch.** The only persisted state is the TV's address, its
  token (keychain) and two preferences.

## Permissions, and why

| Key | Why |
|---|---|
| `NSLocalNetworkUsageDescription` + `NSBonjourServices` | Find the TV on this Wi-Fi and connect to it |
| `NSMicrophoneUsageDescription` | Voice search |
| `NSSpeechRecognitionUsageDescription` | Transcribe that voice search on-device |
