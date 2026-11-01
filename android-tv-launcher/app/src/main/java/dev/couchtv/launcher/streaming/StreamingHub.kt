package dev.couchtv.launcher.streaming

import android.content.Context
import android.content.Intent
import dev.couchtv.launcher.data.LauncherStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Coordinates the streaming adapters: who is signed in, what to show, what to cache, and when a
 * refresh is allowed to happen. The UI reads [snapshot] and never talks to an adapter directly.
 *
 * Refresh policy, matching `shared/streaming-adapters.md`:
 * - Continue Watching: every 60 s while the launcher is visible.
 * - Recommendation rows: every 15 min.
 * - On failure: keep the cached rows (they are marked stale only after 24 h).
 * A manual refresh from Settings ignores the clock.
 */
class StreamingHub(private val context: Context, private val store: LauncherStore) {

    @Serializable
    data class RowSet(
        val continueWatching: List<MediaItem> = emptyList(),
        val trending: List<MediaItem> = emptyList(),
        val movies: List<MediaItem> = emptyList(),
        val series: List<MediaItem> = emptyList(),
    )

    data class Snapshot(
        val rows: RowSet = RowSet(),
        val fetchedAt: Long = 0L,
        val fromCache: Boolean = false,
        val offline: Boolean = false,
        val signedIn: Set<String> = emptySet(),
    ) {
        val isEmpty: Boolean
            get() = rows.continueWatching.isEmpty() && rows.trending.isEmpty() &&
                rows.movies.isEmpty() && rows.series.isEmpty()
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cache = MediaCache(context)
    private val credentials = StreamingCredentials(context)
    private val refreshLock = Mutex()

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    /** Every service the launcher knows how to talk to. One registration line per adapter. */
    private val adapters: List<StreamingServiceAdapter> by lazy {
        listOf(NuvioAdapter(context, credentials) { NuvioEndpoints.DEFAULT_BASE })
    }

    fun adapter(id: String): StreamingServiceAdapter? = adapters.firstOrNull { it.id == id }

    fun allAdapters(): List<StreamingServiceAdapter> = adapters

    /** Reads the cache only: first frame, no network. */
    suspend fun loadCached() {
        val entry = cache.read(KEY) ?: return
        if (cache.isStale(entry.fetchedAt)) return
        val rows = runCatching { json.decodeFromString(RowSet.serializer(), entry.json) }.getOrNull() ?: return
        _snapshot.value = _snapshot.value.copy(
            rows = rows,
            fetchedAt = entry.fetchedAt,
            fromCache = true,
        )
    }

    /** Restores sessions, then refreshes if anything is signed in. Safe to call on every resume. */
    suspend fun refresh(force: Boolean = false, timeoutMs: Long = 6_000L) = refreshLock.withLock {
        val signedIn = withContext(Dispatchers.IO) {
            adapters.filter { adapter -> runCatching { adapter.restoreSession() }.getOrDefault(false) }
                .map { it.id }
                .toSet()
        }
        if (signedIn.isEmpty()) {
            _snapshot.value = Snapshot()
            return@withLock
        }

        val now = System.currentTimeMillis()
        val current = _snapshot.value
        val fresh = current.fetchedAt > 0 && now - current.fetchedAt < REFRESH_INTERVAL_MS
        if (!force && fresh && current.signedIn == signedIn) return@withLock

        val previous = current.rows
        val result = withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                RowSet(
                    continueWatching = ask(signedIn) { it.getContinueWatching(20) },
                    trending = ask(signedIn) { it.getRecommendations(StreamingServiceAdapter.Category.TRENDING, 20) },
                    movies = ask(signedIn) { it.getRecommendations(StreamingServiceAdapter.Category.MOVIES, 20) },
                    series = ask(signedIn) { it.getRecommendations(StreamingServiceAdapter.Category.SERIES, 20) },
                )
            }
        }

        if (result == null || result.continueWatching.isEmpty() && result.trending.isEmpty()
            && result.movies.isEmpty() && result.series.isEmpty()
        ) {
            // Keep what we had; say so quietly rather than emptying the screen.
            _snapshot.value = current.copy(offline = !current.isEmpty, signedIn = signedIn)
            return@withLock
        }

        _snapshot.value = Snapshot(rows = result, fetchedAt = now, fromCache = false, offline = false, signedIn = signedIn)
        runCatching { cache.write(KEY, json.encodeToString(RowSet.serializer(), result)) }
    }

    /** Interleaves the services, so one service cannot fill a whole row. */
    private suspend fun ask(
        signedIn: Set<String>,
        block: suspend (StreamingServiceAdapter) -> List<MediaItem>,
    ): List<MediaItem> {
        val active = adapters.filter { it.id in signedIn }
        val perService = active.map { adapter ->
            runCatching { block(adapter) }.getOrDefault(emptyList())
        }
        val out = ArrayList<MediaItem>()
        var index = 0
        while (true) {
            var added = false
            perService.forEach { list ->
                list.getOrNull(index)?.let { out.add(it); added = true }
            }
            if (!added) break
            index++
        }
        return out.distinctBy { it.id }
    }

    suspend fun connect(serviceId: String, credentialSet: StreamingServiceAdapter.Credentials): StreamingServiceAdapter.AuthResult {
        val adapter = adapter(serviceId) ?: return StreamingServiceAdapter.AuthResult.Invalid
        val result = adapter.authenticate(credentialSet)
        if (result is StreamingServiceAdapter.AuthResult.Success) {
            store.setNuvio(
                server = store.settings.first().nuvioServer,
                account = result.profileName,
            )
            refresh(force = true)
        }
        return result
    }

    suspend fun disconnect(serviceId: String) {
        adapter(serviceId)?.signOut()
        cache.clear()
        _snapshot.value = Snapshot()
    }

    fun accountName(serviceId: String): String? =
        (adapter(serviceId) as? NuvioAdapter)?.profileName()

    /**
     * The deep link for an item, with the documented fallback chain: the item's own URI, then the
     * app's launcher intent. Returns null only when the app is not installed, so the caller can
     * offer the install sheet instead of failing silently.
     */
    fun deepLink(item: MediaItem): Intent? {
        val adapter = adapter(item.serviceId) ?: return null
        val direct = adapter.getDeepLink(item)
        if (direct != null && direct.resolveActivity(context.packageManager) != null) return direct
        return adapter.launchIntent(context)
    }

    fun isInstalled(serviceId: String): Boolean =
        adapter(serviceId)?.isInstalled(context) ?: false

    fun packageName(serviceId: String): String? = adapter(serviceId)?.packageName

    companion object {
        private const val KEY = "rows"
        const val REFRESH_INTERVAL_MS = 15L * 60 * 1000
    }
}
