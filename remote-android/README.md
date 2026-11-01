# Couch Remote for Android

The Compose remote for **Couch TV**, the Android TV launcher in this repository. It finds the TV with
Android's own NSD stack, pairs once with a six-digit code, and then behaves like hardware: D-pad, a
touchpad that moves a real cursor on the TV, transport controls, volume, keyboard and voice.

Minimum Android 7.0 (API 24). Dark only, because it is used in the dark, with the same tokens as the
launcher and the Mac app.

## Build

```bash
cd remote-android
gradle :app:assembleDebug        # or ./gradlew if the wrapper exists
gradle :app:testDebugUnitTest    # protocol and pointer arithmetic
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build installs beside a release build (`applicationIdSuffix .debug`), so both can be compared
on one phone.

## What is where

| Path | What it does |
|---|---|
| `app/src/main/java/dev/couchtv/remote/MainActivity.kt` | One activity, one Compose tree |
| `.../ui/RemoteRoot.kt` | Three states: find a TV, pair, drive it |
| `.../ui/RemoteScreen.kt` | Touchpad, D-pad, transport, system row, sheets |
| `.../ui/Touchpad.kt` | Raw pointer handling: pointer gain, two-finger scroll, tap and long-press |
| `.../ui/Sheets.kt` | App shortcuts (icons over HTTP from the TV) and the keyboard |
| `.../net/Protocol.kt` | Client half of remote protocol v1 |
| `.../net/RemoteClient.kt` | WebSocket client: handshake, pairing, reconnect with backoff, 15 s ping |
| `.../net/Discovery.kt` | NSD discovery plus manual `host:port` entry |
| `.../net/TokenVault.kt` | Pairing tokens encrypted with an Android Keystore key; framing is unit-tested |
| `.../net/PointerGain.kt` | The touchpad arithmetic, pure Kotlin, unit-tested |

`shared/remote-protocol.md` is the specification. `Protocol.kt` is a deliberate copy of the TV's codec
rather than a shared module: the two apps ship on their own schedules, and the specification — not a
Gradle dependency — is the contract.

## Behaviour worth knowing

- **Relative pointer.** Every movement is converted to a fraction of the screen before it is sent; the
  TV integrates the deltas, so a dropped frame is a small delay rather than a jumping cursor.
- **Tap without a timeout.** A tap is a down-up with under 12 px of travel, so a click does not wait for
  a long-press timeout the way `detectTapGestures` would.
- **Two fingers scroll.** Translated into `scroll` messages on the phone, never into pointer moves.
- **Reconnect politely.** 0.5 → 10 s exponential backoff with jitter, immediate retry on foreground,
  and no hammering while a pairing code is expected.
- **Voice never leaves the phone.** `RecognizerIntent` runs in the system's recognition app; only text
  is sent to the TV, and this app holds no microphone permission at all.

## Permissions, and why

| Permission | Why |
|---|---|
| `INTERNET` | The WebSocket to the TV on the LAN |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Notice when the Wi-Fi changes so the link can recover |
| `CHANGE_WIFI_MULTICAST_STATE` | mDNS discovery only; the lock is held while browsing and released after |

No storage, no location, no microphone, no analytics, nothing backed up.

## Tests

`app/src/test` covers the pieces that are pure on purpose: the protocol codec (every frame in both
directions, plus junk input), the pointer arithmetic (gain, dead zone, clamping, jitter), manual address
parsing (IPv4, hostnames, bracketed and bare IPv6, broken ports) and the token framing that puts an
AES-GCM initialisation vector in front of its ciphertext. They run on the JVM without a device; anything
touching the Keystore or NSD needs real hardware and is exercised by hand.
