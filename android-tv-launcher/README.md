# Couch TV — Android TV launcher

The Android sibling of Mac TV: a home screen for Android TV, tablets and phones that shows only the
apps you chose, adds your streaming library to it, and can be driven entirely by a phone.

> **Mac TV itself is untouched.** Nothing in `Sources/`, `Assets/` or `Package.swift` is imported or
> modified by this app; they are separate codebases that share a design language and a protocol.

## Build

Requirements: JDK 17, Android SDK 34. Open the folder in Android Studio, or:

```bash
# once, if you want the wrapper (Android Studio generates it too)
gradle wrapper --gradle-version 8.9

./gradlew :app:assembleDebug        # build/Couch TV debug APK
./gradlew :app:testDebugUnitTest    # protocol, focus, pairing, mapper, compat tests
./gradlew :app:lintDebug
```

The debug APK installs beside a release build (`applicationIdSuffix .debug`), which makes it possible
to compare the two on one TV.

## Install on a TV

1. `adb connect <tv-ip>:5555` (enable *Network debugging* in the TV's developer options), then
   `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
2. Open **Couch TV** once, choose your apps, and press **Set up my home screen**.
3. Press Home and pick Couch TV as the default launcher (or set it with
   `adb shell cmd package set-home-activity dev.couchtv.launcher/.MainActivity`).
4. Settings › Phone remote shows a six-digit code for Couch Remote.

## What is in here

| Folder | What it does |
|---|---|
| `data/` | Installed-app scan, the user's choices (DataStore), TV-compatibility database (SQLite) |
| `ui/` | Compose UI: home rows, the app picker, settings, search, overlays, focus engine, pointer |
| `streaming/` | The `StreamingServiceAdapter` contract, the Nuvio adapter, cache and refresh policy |
| `apk/` | APK inspection (is it TV-ready?), session-based installs, the upload server |
| `remote/` | WebSocket server, pairing, mDNS, input routing, accessibility bridge, foreground service |

Design tokens live in `ui/theme/Theme.kt` and are ported from the Mac app's `Typography.swift` and
`Views.swift`; see `../ARCHITECTURE.md` for the mapping.

## Permissions and why

| Permission | Why |
|---|---|
| `QUERY_ALL_PACKAGES` | Show the installed apps so the user can choose; no list leaves the device |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Streaming metadata and the local remote server |
| `ACCESS_FINE_LOCATION` | Only to read the Wi-Fi name; refuse it and the UI still works |
| `REQUEST_INSTALL_PACKAGES` | Sideloading an APK the user picked |
| `RECEIVE_BOOT_COMPLETED` | The opt-in "start on boot" setting |
| `WRITE_SETTINGS`, `BLUETOOTH_CONNECT` | Brightness and Bluetooth tiles in quick settings |
| `FOREGROUND_SERVICE` | Keep the remote listening while another app is on screen |

The accessibility and notification-listener services are **off unless the user turns them on**, read
nothing into storage, and are only used for what their descriptions say.

## Limits, stated plainly

- Android does not let a normal app inject D-pad keys into another app. The remote therefore fully
  controls Couch TV, and — with the optional accessibility service — can tap, scroll, type, and go
  Back/Home elsewhere. Volume and wake always work; turning the screen off needs Couch TV to be the
  device owner.
- Streaming rows need the service's official app installed; tapping a title deep-links into it.
- Nuvio endpoints are in `NuvioEndpoints`; point them at your server if you self-host.

## Tests

`app/src/test` covers the parts that are pure on purpose: the remote protocol codec, the focus
engine, pairing arithmetic, the Nuvio mapper and the TV-compatibility policy. They run on the JVM,
without a device.
