# The Mac-TV Extended ecosystem

Four products, one design language, one remote protocol. **The original Mac TV app is unchanged**: not
one file under `Sources/`, `Assets/`, `Tests/`, `Packages/`, `Icons/`, `docs/`, `scripts/`,
`Info.plist`, `Package.swift`, `build.sh` or `README.md` was modified, and its CI workflow
(`.github/workflows/build.yml`) still runs exactly as before.

> This file exists because `README.md` belongs to the Mac app and was deliberately left alone. Read it
> after the Mac README: it describes what was added *beside* that app.

| # | Product | Where | What it is |
|---|---|---|---|
| 1 | **Mac TV** | `Sources/`, `Package.swift` | The original macOS app. Read-only here. |
| 2 | **Couch TV** | `android-tv-launcher/` | A launcher for Android TV, tablets and phones. Kotlin, Compose, Leanback. |
| 3 | **Couch Remote (iOS)** | `remote-ios/` | SwiftUI remote, iOS 15+, generated Xcode project. |
| 4 | **Couch Remote (Android)** | `remote-android/` | Compose remote, API 24+. |
| 5 | **Website** | `website/` | Next.js 14 static site: `/`, `/features`, `/download`, `/docs`. |

Shared, and the only documents that constrain the code:

| File | Contract |
|---|---|
| `shared/remote-protocol.md` | Remote protocol v1: discovery, handshake, pairing, input, state, HTTP surface, limits |
| `shared/streaming-adapters.md` | The `StreamingServiceAdapter` interface, the Nuvio reference, the rules |
| `ARCHITECTURE.md` | How the Mac app works and the design tokens every other product inherits |

## The shape of it

```
                 ┌──────────────────────────────┐
   HDMI          │  Couch TV (Android launcher) │
  ──────────     │  · app picker, rows, search  │
                 │  · APK sideload + upload page│
                 │  · streaming adapters        │
                 │  · WebSocket + mDNS server   │
                 └───────────┬──────────────────┘
                             │ ws://tv:8765/remote   (LAN only)
                ┌────────────┴────────────┐
                │                         │
     ┌──────────▼─────────┐    ┌──────────▼─────────┐
     │ Couch Remote (iOS) │    │ Couch Remote (Droid)│
     └────────────────────┘    └─────────────────────┘

   Mac TV (macOS) is its own app, its own HTTP remote (`_mactv-remote._tcp`);
   it shares the design language, not the wire protocol.
```

## Build and run, per product

```bash
# Mac TV — untouched, still the same two commands
swift build && ./build.sh

# Android launcher
cd android-tv-launcher
gradle :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Android remote
cd remote-android
gradle :app:testDebugUnitTest :app:assembleDebug

# iOS remote (the project file is generated)
cd remote-ios/RemoteKit && swift test
cd .. && xcodegen generate && open CouchRemote.xcodeproj

# Website
cd website
npm ci && npm run build && npm start
```

CI mirrors those commands: `.github/workflows/android.yml`, `remote-remotes.yml`, `website.yml`, and
the Mac app's own `.github/workflows/build.yml`. The Android job also fails the build if the APK exceeds
30 MB.

## What was built, and why it is shaped that way

### 1. The launcher starts empty

The first launch is a picker of every installed app, grouped by category, with Select all and Deselect
all. Only what the user ticked reaches the home screen; Settings › Manage Apps is the same screen. A
new package that appears later is offered once — "New app detected: *X*. Add to home screen?" — and
never added silently.

Rows are built from what exists: status row, pins, Continue Watching, Trending, your apps,
recommended films and series, then notifications if enabled. A row with nothing in it is not drawn,
which is why a fresh install is honest rather than empty.

### 2. Compatibility is a policy, not a guess

`CompatSignals` → `TvCompat`, a pure function with tests:

1. a leanback launcher entry is proof;
2. a TV banner **and** a launcher entry is strong evidence;
3. referencing the Leanback library counts, except for games;
4. a required touchscreen means touch-only;
5. the user's own correction wins over all of it.

`UNKNOWN` shows a quiet `?` badge. Only `TOUCH_ONLY` shows the red `!` and offers the Android TV build.
Verdicts are stored per package **and** version, so each app is judged once.

### 3. APKs arrive three ways

Pick a file on the TV, drop one on the upload page (`http://tv-ip:8770`), or `adb install`. All three
end in the system's own session installer, so nothing is silently replaced, and each ends with "Add
*X* to your home screen?".

### 4. Streaming is an interface with one implementation

`StreamingServiceAdapter` — authenticate, `getContinueWatching`, `getRecommendations`, `getDeepLink`,
`isInstalled` — plus a cache. Nuvio is the reference: posters, progress, episode labels, deep links,
and an install prompt when the app is missing. The mapper is pure and unit-tested, which is the only
reason a mapper is ever correct.

### 5. The remote is three layers

| Layer | TV (Kotlin) | Android | iOS |
|---|---|---|---|
| Transport | `RemoteWebSocketServer` | `RemoteClient` | `RemoteConnection` |
| Protocol | `Protocol.kt` | `Protocol.kt` (mirror) | `RemoteKit/Wire.swift` (`Wire`) |
| Feel | `Cursor` + `InputRouter` | `Touchpad` + `PointerGain` | `TouchpadView` + `PointerBallistics` |

The touchpad arithmetic is deliberately identical on both phones: 1.15 gain, 0.55 smoothing, a 0.6 px
dead zone, sensitivity clamped to 0.4–2.5. Both are unit-tested to the same numbers, so a gesture feels
the same on an iPhone and a Pixel — and if it stops feeling the same, a test fails.

### 6. The website is static, and stays that way

Four prerendered routes, no client-side data fetching, no cookies, no analytics. The hero TV, the app
tiles and the picker demo are DOM and CSS rather than screenshots, so the page is sharp on any display,
loads in one round trip, and cannot go stale relative to the app. `next build` output is checked in CI:
`index.html`, `features.html`, `download.html`, `docs.html` must all exist.

## Design tokens (shared by all four)

| Token | Value |
|---|---|
| Reference canvas | 1920 × 1080, scaled (`TVCanvas` on macOS, `tvDp()` on Android, padded stacks on iOS) |
| Type scale | 76 / 57 / 48 / 38 / 31 / 29 / 25 / 23 pt, mapped to `sp` and `pt` |
| Motion | 200 ms press, 320 ms focus/navigation, 350 ms sheet |
| Focus | scale 1.10–1.14, radius 22, white platter with dark text |
| Canvas colours | `#1B1E27` → `#0A0B0E` wallpaper; glass white 7–14 % |
| Accents | phosphor `#3DDC84`, warning `#FF453A`, brass `#C69A52`, cream `#ECE0C6`, ink `#1D1A17` |
| Tiles | 250 × 150, spacing 28, row gap 80 |

## Honest limits

- **Android will not let one app inject keys into another.** The remote fully controls Couch TV; inside
  other apps, the optional accessibility service can tap, scroll, type and go Back/Home. Volume and wake
  always work; screen-off needs device-owner rights. All three are documented rather than hidden.
- **Streaming rows need the service's own app.** Tapping a title deep-links into it; nothing is
  downloaded, proxied or hosted.
- **Nothing here has been run on a device yet.** There is no JDK, Gradle, Xcode or Android SDK in the
  environment this was written in, so the first real compile is CI's. The pure-Kotlin/Swift pieces are
  covered by tests precisely because they are the parts that could be verified without a device.
- **Discovery falls back to typing an address**, because guest Wi-Fi often blocks multicast.

## Tests

| Suite | What it pins |
|---|---|
| `android-tv-launcher/app/src/test` | Protocol codec, focus engine, pairing arithmetic, Nuvio mapper, TV-compatibility policy |
| `remote-android/app/src/test` | Client protocol codec (both directions, plus junk), pointer gain |
| `remote-ios/RemoteKit/Tests` | Client protocol, pointer ballistics, pairing input |

Everything in those suites is pure: no device, no emulator, no simulator.

## Licence

MIT, the same as the Mac app — see `LICENSE`. App names and icons belong to their owners; this project
ships no content and proxies nothing.
