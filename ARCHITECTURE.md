# Architecture

This document describes the **Mac TV** app that already lives in this repository, and how the
Android ecosystem added beside it fits in. It is written from a read-only reading of the Mac
sources: **no file under `Sources/`, `Assets/`, `Tests/`, `Packages/`, `Icons/`, `docs/`,
`scripts/`, `Info.plist`, `Package.swift` or `build.sh` was changed by this work.**

---

## 1. What the existing app is

Mac TV turns a Mac into an Apple TV–style box. It shows a tvOS-like home screen, opens streaming
services in their official web players, plays local/NAS/Jellyfin video, and offers the phone as a
remote over the LAN.

| | |
|---|---|
| Language | Swift 6 (SwiftUI + AppKit + WebKit + AVFoundation) |
| Build | Swift Package Manager (`Package.swift`), `./build.sh` produces `build/Mac TV.app` |
| Platform | macOS 14+, universal (arm64 + x86_64) |
| Bundle id | `local.couch.tv` |
| Distribution | DMG, ad-hoc/local code signing, no App Store sandbox |
| Tests | `swift test --disable-xctest` (Swift Testing) + Node tests for the injected web player |

### Entry point

`Sources/MacTV/main.swift` builds an `NSApplication` programmatically (no storyboard). `AppDelegate`
owns the single window, a `TVController`, and a `RemoteServer`; it also implements hidden
`--selftest-*` / `--export-remote` CLI modes used by CI and by screenshot QA.

### Module map (the files that matter to us)

| File | Responsibility |
|---|---|
| `TVController.swift` | The single source of truth: focus, mode (home/movies/player), sheets, volume, toasts, remote commands, launch/quit of streaming apps, sleep timer. |
| `Views.swift` | Home screen: `TVCanvas` scaling, `Wallpaper`, `Artwork`, `AppBanner` (Top Shelf), `AppGrid`, `GlassDock`, `AppTile`, `Motion` (animation tokens), `tvGlass` / `focusPlatter` modifiers. |
| `TileArt.swift` | Generates the 5:3 dock tile from any square icon: reads the brand ground, finds the mark, recentres it, paints the ground outward. |
| `Catalog.swift` | The built-in app list (Netflix, YouTube, Prime Video, JioHotstar, Apple TV, Spotify, YouTube Music) with accent/deep colours and web URLs. |
| `AppLibrary.swift` | User-added websites, Mac apps and Steam games, stored as JSON in `~/Library/Application Support/Mac TV`. |
| `TopShelf.swift` | Apple TV–style Top Shelf feed, from TVmaze's free streaming schedule and Apple's public music charts; cached daily. |
| `RemoteServer.swift` | A hand-rolled HTTP/1.1 server on `NWListener` that serves the phone page, the WebSocket-ish command queue and JSON routes. |
| `RemotePage.swift` | The entire phone remote as one HTML+CSS+JS string (clickpad, touchpad, keyboard, app list, volume). |
| `PairingStore.swift` | Device tokens: SHA-256 digests only, guest tokens expire. |
| `MoviesScreen.swift`, `MoviePlayer.swift`, `MovieLibrary.swift`, `Jellyfin.swift`, `NetworkShares.swift`, `Matroska*.swift` | Local/Jellyfin library, MKV demuxing, playback, Continue Watching. |
| `ScreenSaver.swift`, `Ambient.swift` | Apple aerials / photos / drifting colour screensaver with a lock-screen clock. |
| `ControlCenter.swift`, `SettingsPanel.swift`, `Setup.swift`, `AppOrganizer.swift`, `Profiles.swift` | The control layer: glass control centre, settings tree, first-run flow (welcome → apps → remote → privacy → done), app re-ordering, per-person profiles. |
| `SpatialNav.swift`, `AutoPlay.swift` | Injected JS: D-pad focus ring for mouse-built websites; skip-intro / next-episode pressing. |
| `Analytics.swift` | Every telemetry event; opt-in, never content. |

### The design language we must inherit

These are the tokens the Android sibling is built from (all lifted from the Mac sources):

**Canvas.** tvOS canvas of 1920×1080 points, scaled to fill any window (`TVCanvas`). Nothing is
denser than the TV type scale: `TVText` runs 76 / 57 / 48 / 38 / 31 / 29 / 25 / 23 pt, all Medium,
Bold when emphasised, never below 23 pt. Motion: `.smooth(duration: 0.32)` for focus and
navigation, `0.35` for sheets, `0.18` for presses.

**Colour.**

| Token | Value | Use |
|---|---|---|
| Wallpaper | radial `#1B1E27` → `#0A0B0E` | home background |
| Glass | white 7–14 %, blur 28 px, saturate 170 % | dock, pills, control centre |
| Focus platter | solid `#FFFFFF` with black text | any focused control |
| Phosphor / accent | `#3DDC84` (also the app's setup accent) | "on" switches, live LEDs, brand light |
| Warning | `#FF453A` / `#FF8A80` | destructive, failures |
| Brass (site) | `#C69A52`, dark `#7E5E2E` | the marketing site and icon |
| Cream / ink (site) | `#ECE0C6` / `#1D1A17` | the marketing site |
| Cabinet | `#2B2A2E` | the CRT shell on the site |

**Shape.** App tiles are 250×150 (5:3) with a 22 pt continuous radius; a focused tile scales 1.14,
lifts 10 pt, casts a soft shadow and gains a bright top edge. Rows are 80 pt apart. The dock is
1728×220 with a 48 pt radius.

**Voice.** Sentence-case, plain, concrete copy. "Use your phone as a remote", "Choose your apps",
"Nothing about what was watched ever shows here." No exclamation marks, no marketing shout.

### Remote control protocol (as it exists)

The Mac's remote is deliberately tiny and HTTP-based: the page `POST`s `{"a":"up"}`, `{"a":"ok"}`,
`{"a":"pointer","dx":…,"dy":…}`, `{"a":"text","t":"…"}`, `{"a":"launch","id":"…"}` to `/command`;
`RemoteCommand` in `TVController.swift` parses roughly 25 such actions. Pairing uses a six-digit code
rotated every five minutes; tokens are stored as digests. Guest tokens are limited to a safe subset.

The Android ecosystem keeps the *same vocabulary* but carries it over a real WebSocket (see
`shared/remote-protocol.md`), because the pointer remote needs 60 Hz and a persistent state channel.

---

## 2. What this work adds

Everything new lives beside the Mac app in its own directory. Nothing new is compiled into, or
imported by, the Swift target.

```
mac-tv/
├── Sources/ Assets/ Tests/ …        # existing Mac app — untouched
├── android-tv-launcher/             # Kotlin + Compose TV launcher (the sibling app)
├── remote-android/                  # Compose phone remote
├── remote-ios/                      # SwiftUI phone remote
├── website/                         # Next.js 14 site (Vercel)
├── shared/                          # protocol + adapter contracts, platform-neutral
├── .github/workflows/               # existing macos job, untouched; new android/web jobs
├── ARCHITECTURE.md                  # this file
└── ECOSYSTEM.md                      # ecosystem entry point (README.md left byte-identical)
```

### Layer map of the Android launcher

```
MainActivity ─┬─ OnboardingActivity-ish route (first run: choose apps)
              ├─ HomeScreen (Compose)
              ├─ Settings / Manage apps / Sideload / Search / Screensaver
              └─ RemoteServerHost (WebSocket + mDNS + pointer overlay)

data/        LauncherStore (DataStore), InstalledApps (PackageManager), AppCompat (Room-free SQLite),
             PinsStore, ParentalStore, MediaCache (DataStore JSON + Coil disk cache)
streaming/   StreamingServiceAdapter (contract) → NuvioAdapter → StreamingHub (cache + refresh)
apk/         ApkInspector (TV-optimised?), ApkInstaller (PackageInstaller sessions), UploadServer
remote/      RemoteWebSocketServer (Java-WebSocket), MdnsAdvertiser (NsdManager), InputRouter
ui/          theme/ (CouchTheme tokens mirrored from the Mac), components/ (Row, PosterCard, Tile),
             home/, onboarding/, settings/, search/, saver/, overlay/
```

The Android app is a **HOME launcher** (`android.intent.category.HOME`), so it can be set as the
device's default home screen on TV, tablet and phone. Screen and input class are detected at runtime
(`ScreenProfile`): `LEANBACK` for ≥960 dp with a D-pad, `TABLET` for ≥600 dp touch, `PHONE` otherwise.
The same composables serve all three; only spacing, tile width and whether labels are always visible
change.

### Streaming integration

`StreamingServiceAdapter` is the seam. Nuvio is the first implementation; the contract is written so a
new service is one file plus one registration. Adapters never draw UI: they return `MediaItem`s and
`Intent`s, and the launcher owns presentation, caching and focus. Details and the reference Nuvio
surface are in `shared/streaming-adapters.md`.

### Remote protocol

One JSON-over-WebSocket contract, three implementations (launcher server, Android client, iOS client),
documented once in `shared/remote-protocol.md`. The TV side integrates pointer deltas into a normalised
cursor at display rate and injects key events through a single `InputRouter`, so the TV's own screens
and third-party apps behave the same whether the event came from the D-pad, a Bluetooth remote, the
phone remote, or a controller.

### Performance intent

Home screen composes from cached data only (`LauncherStore` + `MediaCache` primed at boot), artwork is
generated locally from brand colours (no network on first paint), images come through Coil with a
memory/disk cache, and rows use `LazyRow`s with stable keys. Target: first frame < 500 ms on a 2 GB
Amlogic box, 60 fps scrolling, < 150 MB RSS, APK < 30 MB (no ExoPlayer, no Ktor Netty).

### Deliberate non-goals

- No tracking, no accounts, no server of our own — same promise as the Mac app.
- No content hosting or aggregation: adapters talk to the user's own accounts, and the launcher
  deep-links into the official app.
- No modification of the Mac app, its design tokens, its workflows or its README.
