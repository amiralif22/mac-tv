# Streaming adapters

The launcher's home screen shows content from services the user has connected. A service is
supported by writing one **adapter**: a class that answers "what should I show?" and "how do I open
this?". Everything else — caching, rows, focus, artwork, deep-link fallback, error surfaces — belongs
to the launcher.

## 1. The contract

Kotlin (the launcher's own interface, `streaming/StreamingServiceAdapter.kt`), with the equivalent
shape in each implementation:

```kotlin
interface StreamingServiceAdapter {
    /** Stable id, used for storage keys and cache filenames: "nuvio". */
    val id: String
    /** Shown in Settings and on the "Connect" row. */
    val displayName: String
    /** Package name of the official app, for isInstalled() and the "install it?" prompt. */
    val packageName: String
    /** What this adapter can do, so the UI can hide rows it will never fill. */
    val capabilities: Set<Capability>

    suspend fun authenticate(credentials: Credentials): AuthResult
    suspend fun restoreSession(): Boolean
    suspend fun signOut()

    suspend fun getContinueWatching(limit: Int = 20): List<MediaItem>
    suspend fun getRecommendations(category: Category, limit: Int = 20): List<MediaItem>

    /** Deep link into the official app at this item, or null if the item can't be opened. */
    fun getDeepLink(item: MediaItem): Intent?
    fun isInstalled(context: Context): Boolean

    /** Optional: what's playing, for the remote's contextual controls. */
    suspend fun nowPlaying(): NowPlaying? = null

    enum class Capability { CONTINUE_WATCHING, RECOMMENDATIONS, TRENDING, NEW_RELEASES, SEARCH }
    enum class Category { TRENDING, MOVIES, SERIES, NEW, FOR_YOU }
}
```

`Credentials` is a sealed class (`Token(accessToken, refreshToken?)`, `Password(user, password)`,
`DeviceCode(...)`), so an adapter can support whichever flow the service offers without leaking that
into the UI.

`AuthResult` is `Success(profileName)`, `NeedsTwoFactor`, `Invalid`, or `NetworkError(cause)` — never a
thrown exception to the caller.

### `MediaItem`

```kotlin
data class MediaItem(
    val id: String,                 // adapter-local, stable: "nuvio:tt0903747"
    val serviceId: String,          // "nuvio"
    val title: String,
    val subtitle: String? = null,   // "S2 E4 · Tailgate" or "2023 · 1h 59m"
    val kind: Kind,                 // MOVIE, SERIES, EPISODE, LIVE
    val posterUrl: String? = null,  // 2:3 portrait
    val backdropUrl: String? = null,
    val progress: Float? = null,    // 0..1, continue-watching only
    val episodeLabel: String? = null,
    val deepLink: String? = null,   // adapter's own URI; the launcher also asks getDeepLink()
    val accent: String? = null      // "#RRGGBB", used for locally drawn placeholder art
)
```

The launcher renders a graceful placeholder when `posterUrl` is null (deterministic gradient from
`id` + `accent`), so a slow or offline service never produces a hole in the row.

## 2. Reference implementation: Nuvio

Nuvio is the first adapter. Its surface, as used by the launcher, is:

| Concern | Approach |
|---|---|
| Account | Email + password sign-in through the user's own Nuvio account, stored in Android Keystore (`EncryptedSharedPreferences`); refresh token rotated on each launch. Where the service offers a device-code or web sign-in, `authenticate(DeviceCode)` is used instead and the TV shows the code plus a QR. |
| Continue Watching | `GET /api/v1/sync/playback?limit=50` — items with `positionMs`/`durationMs`; mapped to `progress` and `subtitle` (`S2 E4 · 42 min left`). |
| Rows | `GET /api/v1/catalog/rows` for Trending, For You, New. Movies and Series are filtered client-side by `kind` so one request fills two rows. |
| Artwork | TMDB-backed poster/backdrop URLs supplied by the service; Coil caches them on disk (200 MB budget). |
| Deep link | `nuvio://open?id=<contentId>&type=<movie|series>&episode=<id>` with a fallback chain: `nuvio://details/<id>` → https URL in the Play Store page if the app is missing. |
| Not installed | The row still renders from cache; tapping shows "Nuvio isn't installed" with `Install` (Play Store) and `Sideload APK` actions. |

Because the real endpoints and auth flow of a given account vary by deployment (self-hosted vs
hosted), `NuvioAdapter` keeps every endpoint in one `NuvioEndpoints` object and every field name in a
`@Serializable` DTO. Point it at a self-hosted base URL (Settings › Streaming › Nuvio › Server) and the
same adapter works; that is the only "customisation" a user needs.

**Nothing is scraped, downloaded or played by the launcher.** Adapters read library metadata for the
signed-in user and deep-link into the official app, exactly as the Mac app opens a service's own web
player. A service that has no public API simply has no adapter.

## 3. Adding a service

1. Create `streaming/<Service>Adapter.kt` implementing `StreamingServiceAdapter`.
2. Add DTOs (`@Serializable`) and an `Endpoints` object; keep parsing defensive — a missing field is
   `null`, not a crash.
3. Register it in `StreamingHub.builtIn(context)`.
4. Add its strings to `res/values/strings.xml` (no literals in code).
5. Add a test under `app/src/test/java/.../streaming/` that feeds a recorded JSON fixture through the
   mapper and asserts `MediaItem`s (see `android-tv-launcher/app/src/test/java/dev/couchtv/launcher/streaming/NuvioMapperTest.kt`).

A service that only needs "open the app" is not an adapter: it is just an app the user picks in
onboarding.

## 4. Caching and refresh

```
StreamingHub
 ├── cache: MediaCache        (per adapter+category JSON in DataStore, timestamps)
 ├── refresh policy           (Continue Watching: 60 s + on resume; rows: 15 min; manual pull-to-refresh)
 ├── concurrency               (per-adapter Mutex, one in-flight request per category)
 └── failure modes             (stale cache served with a subtle "offline" dot; never a modal)
```

The home screen composes only from cache on first frame, then updates in place. A row whose adapter is
signed out disappears; a row whose adapter failed keeps its last good data for 24 h.

## 5. Deep-link fallback rules

1. `Intent` with the adapter's scheme, `FLAG_ACTIVITY_NEW_TASK`, where the adapter built it.
2. If `resolveActivity` is null, try the adapter's https fallback.
3. If neither resolves, launch the app's main launcher intent.
4. If the app isn't installed: show a sheet offering the Play Store and the sideload screen, and keep
   the item on screen.

Never crash, never show a raw `ActivityNotFoundException`, and always log the attempted URI at debug
level only (no titles, no user ids).
