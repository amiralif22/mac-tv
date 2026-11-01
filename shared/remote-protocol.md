# Remote protocol v1

The contract between **Couch TV** (the Android TV launcher, which acts as the TV) and the two
companion remote apps (`remote-android`, `remote-ios`). It is JSON over a single WebSocket
connection, with mDNS for discovery. One connection carries input, state and pairing.

Reference implementations:

| Side | File |
|---|---|
| TV server | `android-tv-launcher/app/src/main/java/dev/couchtv/launcher/remote/RemoteWebSocketServer.kt` |
| Android client | `remote-android/app/src/main/java/dev/couchtv/remote/net/RemoteClient.kt` |
| iOS client | `remote-ios/CouchRemote/Net/RemoteConnection.swift` |
| Shared Kotlin codec | `android-tv-launcher/app/src/main/java/dev/couchtv/launcher/remote/Protocol.kt` (the Android remote keeps a mirror at `remote-android/.../net/Protocol.kt`) |

Anything not written here is not part of v1. Unknown message types **must be ignored** by both ends
(forward compatibility), and unknown keys in a known message must be ignored too.

---

## 1. Discovery

The TV advertises a DNS-SD service while the launcher is in the foreground (and while the screen is
off, so the remote can wake it):

| Field | Value |
|---|---|
| Service type | `_couchremote._tcp` |
| Instance name | `Couch TV · <device name>` (human readable, UTF-8) |
| Port | the WebSocket port, chosen once and persisted (default `8765`) |
| TXT `id` | stable device UUID, lowercase, no braces |
| TXT `name` | human-readable TV name, e.g. `Living room` |
| TXT `v` | protocol version, `1` |
| TXT `pair` | `1` if the TV is currently showing a pairing code, else `0` |
| TXT `model` | e.g. `Android TV`, `Tablet`, `Phone` |
| TXT `path` | always `/remote` |

Clients fall back to manual entry: `<ip or hostname>:<port>`. IPv4 and IPv6 literals are accepted
(`[fe80::1]:8765`). A client must show discovered TVs by name, and allow re-discovery without
restarting.

The Mac TV app advertises `_mactv-remote._tcp` over HTTP, which is a different protocol; clients may
mention it, but v1 of these apps talks `_couchremote._tcp` only.

## 2. Connection

```
ws://<host>:<port>/remote
```

No query parameters. Nothing sensitive goes in a URL (it ends up in logs). TLS is not used on the
LAN; the token is the capability, and pairing codes are short-lived.

All text frames are UTF-8 JSON objects with a top-level `"t"` (type). Binary frames are not used in
v1. Max frame size accepted by the TV: 64 KiB; larger frames close the connection with code `1009`.

## 3. Handshake

1. Client connects and sends `hello` immediately (within 5 s or the TV closes the socket).
2. TV answers exactly one of `welcome`, `pair-required`, or `error`.

```jsonc
// client → tv
{ "t": "hello", "v": 1, "id": "0b6f…", "name": "iPhone 15", "platform": "ios",
  "app": "Couch Remote/1.0 (build 42)", "token": "ab12…" }   // token omitted on first ever connect

// tv → client (already paired)
{ "t": "welcome", "v": 1, "device": "9f2c…", "token": "ab12…",
  "tv": { "name": "Living room", "model": "Android TV", "w": 1920, "h": 1080 },
  "caps": ["dpad", "pointer", "text", "voice", "apps", "media", "power", "volume"] }

// tv → client (needs pairing)
{ "t": "pair-required", "device": "9f2c…", "tvName": "Living room", "pin": 6 }

// tv → client (rejected)
{ "t": "error", "code": "auth", "message": "Unknown or expired token" }
```

Error codes: `auth` (bad/expired token), `busy` (too many connections), `version` (protocol too old),
`pin` (wrong pairing code, with `"attempts": n`), `internal`.

`token` in `welcome` may be a *new* token when the client presented a valid one: the TV rotates
tokens on every successful handshake (sliding expiry), so clients must persist the latest value.
Tokens are opaque, 32 URL-safe characters, stored on the TV as SHA-256 digests only.

### Pairing

The TV shows a six-digit code in Settings › Remote and on first run. It rotates every 5 minutes.

```jsonc
// client → tv
{ "t": "pair", "pin": "123456", "name": "iPhone 15", "platform": "ios", "id": "0b6f…" }

// tv → client
{ "t": "paired", "token": "ab12…", "device": "9f2c…", "tv": {…}, "caps": […] }

// or, on a wrong or expired code
{ "t": "error", "code": "pin", "message": "That code didn't work", "attempts": 2 }
```

After 5 wrong codes from one address in 10 minutes the TV answers `busy` for 10 minutes. A paired
client is remembered by its `id`; the TV's device list can revoke it, which closes its sockets.

## 4. Input events (client → TV)

Every input message is fire-and-forget. The TV must never reply to input unless it is an error worth
knowing about, and must not rate-limit below 120 messages/second.

### 4.1 Keys

```jsonc
{ "t": "key", "k": "up" }
{ "t": "key", "k": "ok", "hold": true }      // long press / key-repeat hint
```

`k` is one of:

| Group | Values |
|---|---|
| Navigation | `up` `down` `left` `right` `ok` `back` `home` `menu` `info` `settings` `search` |
| Media | `play` `pause` `playpause` `stop` `rewind` `forward` `next` `prev` |
| Volume | `volup` `voldown` `mute` |
| System | `power` (toggle screen on/off), `wake`, `sleep` |

The TV maps each to the equivalent Android `KeyEvent` (`KEYCODE_DPAD_*`, `KEYCODE_ENTER`,
`KEYCODE_BACK`, `KEYCODE_HOME`, `KEYCODE_MEDIA_*`, `KEYCODE_VOLUME_*`, `KEYCODE_POWER`) and injects it
into whichever window has focus — the launcher itself, or the foreground streaming app when Android
allows it. Unknown keys are ignored.

### 4.2 Pointer (LG-style cursor)

The remote sends **relative** movement, normalised so the same finger distance moves the same distance
on any TV. `dx`/`dy` are fractions of screen width/height per event; `x`/`y` are never sent, so packet
loss cannot make the cursor jump.

```jsonc
{ "t": "pointer", "dx": 0.0031, "dy": -0.0012, "ts": 1730000000123 }
{ "t": "pointer", "batch": [ [0.0031, -0.0012, 1730000000123],
                             [0.0028, -0.0008, 1730000000140] ] }
```

- Clients send at **60 Hz** (16 ms) or batch up to 5 samples into one `batch` message per frame. A
  `batch` and single `pointer` must not be mixed in the same message.
- The TV integrates deltas into a cursor position in `[0,1]²`, applies a user sensitivity multiplier
  (default 1.0, range 0.4–2.5), clamps to the screen edges, and draws a cursor of 28 dp.
- The cursor auto-hides after 4 s of no pointer input, and immediately after `click` or `key`.
- While the cursor is visible, `ok` activates the element under it if the TV can hit-test; otherwise
  it behaves like a normal D-pad select. Pointer mode never steals focus until the first move.

```jsonc
{ "t": "click" }                                   // click at the cursor
{ "t": "scroll", "dy": 120 }                        // wheel-like, px-equivalent; positive = down
{ "t": "longpress" }                                // context click at the cursor
```

Two-finger swipe on the touchpad is translated by the **client** into `scroll` messages, never into
pointer moves.

### 4.3 Text

```jsonc
{ "t": "text", "v": "hello world" }   // ≤ 500 chars per message; appended as typed
{ "t": "backspace", "n": 1 }          // repeatable, default 1, max 32
{ "t": "submit" }                     // IME action / Enter
```

Voice search is transcribed **on the phone** and sent as `text`, so no audio ever crosses the network:

```jsonc
{ "t": "voice", "text": "the bear", "final": true }
```

### 4.4 Launching apps and content

```jsonc
{ "t": "launch", "pkg": "com.netflix.mediaclient" }
{ "t": "command", "a": "quicksettings" }        // open the overlay
{ "t": "command", "a": "screensaver" }
{ "t": "command", "a": "search", "q": "dune" }  // opens search pre-filled
```

`launch` only works for packages the user selected in the launcher (or system packages with a leanback
launcher intent). The TV answers with `error`/`not-allowed` otherwise.

### 4.5 Keep-alive and timing

```jsonc
{ "t": "ping", "ts": 1730000000123 }
{ "t": "pong", "ts": 1730000000123, "tv": 1730000000124 }
```

Clients ping every 15 s when idle; the TV drops a socket after 45 s of silence. The client computes
round-trip time from `ts` and shows it in the connection indicator (good < 80 ms, fair < 200 ms).

## 5. State (TV → client)

Sent after `welcome`/`paired`, then on change — at most 10 Hz, coalesced. Clients must treat every
field as optional and merge, never replace wholesale.

```jsonc
{ "t": "state", "screen": "home",              // home | apps | search | settings | overlay | saver | other
  "focus": { "kind": "app", "label": "Netflix" },
  "media": { "playing": true, "title": "The Bear", "subtitle": "S2 E4 · 00:31:12",
             "positionMs": 1872000, "durationMs": 2400000, "app": "com.netflix.mediaclient" },
  "volume": 0.42, "muted": false,
  "pointer": true,                             // cursor currently visible
  "pin": false }                               // TV is showing a pairing code
```

`media` is `null` when nothing is playing. `screen` lets the remote show contextual controls (e.g.
media transport only while something plays).

### 5.1 Apps list

```jsonc
{ "t": "apps", "apps": [ { "pkg": "com.netflix.mediaclient", "name": "Netflix",
                            "color": "#E50914", "fav": true } ] }
```

Icons are fetched over HTTP, not WebSocket: `GET http://<host>:<port>/icon?pkg=<package>` returns a
PNG (density-aware, ≤ 96×96) or 404. The list is stable-sorted by the user's home-screen order. The
TV pushes `apps` when it changes and after `welcome`.

### 5.2 Transient messages

```jsonc
{ "t": "toast", "text": "Volume 40%" }
{ "t": "event", "name": "paired-elsewhere" }    // this device was revoked, close the socket
{ "t": "error", "code": "not-allowed", "message": "Netflix isn't on the home screen" }
```

## 6. HTTP surface (same port)

The WebSocket server also answers plain HTTP on the same port for things that are simpler as files:

| Method + path | Response |
|---|---|
| `GET /` | a small status page: TV name, protocol version, pairing state |
| `GET /info` | `{ "id", "name", "model", "v", "pin": bool }` |
| `GET /icon?pkg=…` | PNG app icon, `Cache-Control: public, max-age=86400` |
| `GET /upload` | the APK upload page (sideloading) |
| `POST /upload` | multipart APK body; returns `{ "ok": true, "path": "…" }` |
| `GET /health` | `{ "ok": true }` |

HTTP requests never require the token (they are LAN-local and read-only except `/upload`, which
requires the pairing PIN in the `X-Pairing-Pin` header).

## 7. Limits and failure behaviour

| Thing | Value |
|---|---|
| Connections | 8 concurrent, then `error`/`busy` |
| Handshake timeout | 5 s |
| Idle timeout | 45 s (ping keeps it alive) |
| Input rate | ≤ 240 msg/s per client; excess is dropped, not queued |
| Pointer batch | ≤ 8 samples, ≤ 64 KiB |
| Text | ≤ 500 chars per message, ≤ 4 KiB total buffered |
| Pairing attempts | 5 per address per 10 min |

A client that violates limits is closed with `1008` (policy) after one `error` message. Reconnection
uses exponential backoff: 0.5 s, 1 s, 2 s, 4 s, 8 s, capped at 10 s, with ±20 % jitter, and immediate
retry on app foreground.

## 8. Versioning

`v` is an integer. A TV that receives `hello.v > 1` answers `error`/`version` and closes. A TV must
accept `hello.v == 1` forever. New optional message types are additive; fields may be added, never
repurposed or removed, within v1.
