package dev.couchtv.launcher.streaming

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import dev.couchtv.launcher.streaming.StreamingServiceAdapter.AuthResult

/**
 * Nuvio, the first streaming integration.
 *
 * It reads the signed-in user's own library — nothing is downloaded, scraped or played here — and
 * hands the launcher rows of [MediaItem]s plus a deep link into the official app, which is exactly
 * how the Mac app treats a streaming service (its own player, its own sign-in).
 *
 * All paths live in [NuvioEndpoints] and every field name in a DTO in this file, so pointing the
 * adapter at a self-hosted Nuvio is a settings change, not a code change.
 */
class NuvioAdapter(
    private val context: Context,
    private val credentials: StreamingCredentials,
    private val baseUrl: () -> String,
) : StreamingServiceAdapter {

    override val id: String = SERVICE_ID
    override val displayName: String = "Nuvio"
    override val packageName: String = NuvioEndpoints.PACKAGE

    override val capabilities: Set<StreamingServiceAdapter.Capability> = setOf(
        StreamingServiceAdapter.Capability.CONTINUE_WATCHING,
        StreamingServiceAdapter.Capability.RECOMMENDATIONS,
        StreamingServiceAdapter.Capability.TRENDING,
        StreamingServiceAdapter.Capability.NEW_RELEASES,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun endpoints(): NuvioEndpoints =
        NuvioEndpoints(baseUrl().ifBlank { NuvioEndpoints.DEFAULT_BASE })

    // ---- Authentication -------------------------------------------------------------------------

    override suspend fun authenticate(credentialSet: StreamingServiceAdapter.Credentials): AuthResult =
        withContext(Dispatchers.IO) {
            val password = credentialSet as? StreamingServiceAdapter.Credentials.Password
                ?: return@withContext AuthResult.Invalid
            runCatching {
                val body = buildString {
                    append("{\"email\":").append(json.encodeToString(String.serializer(), password.user))
                    append(",\"password\":").append(json.encodeToString(String.serializer(), password.password))
                    append("}")
                }
                val (code, response) = request(
                    method = "POST",
                    url = endpoints().login,
                    body = body,
                    token = null,
                )
                when {
                    code in 200..299 -> {
                        val session = json.decodeFromString(NuvioSession.serializer(), response)
                        if (session.accessToken.isBlank()) return@runCatching AuthResult.Invalid
                        storeSession(session)
                        AuthResult.Success(session.profile?.name ?: password.user)
                    }

                    code == 401 || code == 403 -> AuthResult.Invalid
                    code == 429 -> AuthResult.NetworkError("Too many attempts. Try again in a minute.")
                    else -> AuthResult.NetworkError("Nuvio answered $code.")
                }
            }.getOrElse { error ->
                AuthResult.NetworkError(error.readableMessage())
            }
        }

    override suspend fun restoreSession(): Boolean = withContext(Dispatchers.IO) {
        val token = credentials.get(id, KEY_ACCESS) ?: return@withContext false
        runCatching {
            val (code, _) = request("GET", endpoints().me, null, token)
            if (code in 200..299) return@runCatching true
            if (code == 401) return@runCatching refresh()
            false
        }.getOrDefault(false)
    }

    /** Access tokens are short-lived; the refresh token is the long-lived half. */
    private suspend fun refresh(): Boolean {
        val refreshToken = credentials.get(id, KEY_REFRESH) ?: return false
        return runCatching {
            val body = "{\"refreshToken\":${json.encodeToString(String.serializer(), refreshToken)}}"
            val (code, response) = request("POST", endpoints().refresh, body, null)
            if (code !in 200..299) return@runCatching false
            val session = json.decodeFromString(NuvioSession.serializer(), response)
            storeSession(session)
            true
        }.getOrDefault(false)
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        credentials.clear(id)
    }

    override suspend fun isSignedIn(): Boolean =
        credentials.get(id, KEY_ACCESS)?.isNotBlank() == true

    private fun storeSession(session: NuvioSession) {
        credentials.put(id, KEY_ACCESS, session.accessToken)
        session.refreshToken?.let { credentials.put(id, KEY_REFRESH, it) }
        session.profile?.name?.let { credentials.put(id, KEY_PROFILE, it) }
    }

    fun profileName(): String? = credentials.get(id, KEY_PROFILE)

    // ---- Content --------------------------------------------------------------------------------

    override suspend fun getContinueWatching(limit: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        fetchItems(endpoints().playback(limit)) { response ->
            val page = json.decodeFromString(NuvioPlaybackResponse.serializer(), response)
            page.items.mapNotNull { NuvioMapper.toMediaItem(it, id) }
                .filter { (it.progress ?: 0f) > 0.005f && (it.progress ?: 0f) < 0.985f }
                .take(limit)
        }
    }

    override suspend fun getRecommendations(
        category: StreamingServiceAdapter.Category,
        limit: Int,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val endpoint = endpoints().rows(category)
        fetchItems(endpoint) { response ->
            val rows = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(NuvioRow.serializer()), response)
            rows.asSequence()
                .flatMap { it.items.asSequence() }
                .mapNotNull { NuvioMapper.toMediaItem(it, id) }
                // One request fills several launcher rows; filter here so Movies stays movies.
                .filter { item ->
                    when (category) {
                        StreamingServiceAdapter.Category.MOVIES -> item.kind == MediaItem.Kind.MOVIE
                        StreamingServiceAdapter.Category.SERIES -> item.kind == MediaItem.Kind.SERIES ||
                            item.kind == MediaItem.Kind.EPISODE
                        else -> true
                    }
                }
                .distinctBy { it.id }
                .take(limit)
                .toList()
        }
    }

    /** Runs a GET with the stored token, refreshing once on 401. */
    private suspend fun fetchItems(url: String, parse: (String) -> List<MediaItem>): List<MediaItem> {
        val token = credentials.get(id, KEY_ACCESS) ?: return emptyList()
        return runCatching {
            var (code, body) = request("GET", url, null, token)
            if (code == 401 && refresh()) {
                val refreshed = credentials.get(id, KEY_ACCESS) ?: return emptyList()
                val second = request("GET", url, null, refreshed)
                code = second.first
                body = second.second
            }
            if (code !in 200..299) return@runCatching emptyList()
            parse(body)
        }.getOrDefault(emptyList())
    }

    // ---- Opening an item ------------------------------------------------------------------------

    override fun getDeepLink(item: MediaItem): Intent? {
        val uri = item.deepLink ?: return null
        return runCatching {
            Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.getOrNull()
    }

    /** Play Store when the app is missing; the launcher shows a sheet rather than failing. */
    fun installIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$packageName"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // ---- HTTP -----------------------------------------------------------------------------------

    private fun request(method: String, url: String, body: String?, token: String?): Pair<Int, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TimeUnit.SECONDS.toMillis(8).toInt()
            readTimeout = TimeUnit.SECONDS.toMillis(12).toInt()
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CouchTV/${android.os.Build.MODEL}")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            connection.disconnect()
        }
    }

    private fun Throwable.readableMessage(): String = when (this) {
        is java.net.UnknownHostException -> "Can't reach Nuvio. Check the connection."
        is java.net.SocketTimeoutException -> "Nuvio took too long to answer."
        is IOException -> "Network error."
        else -> message ?: "Something went wrong."
    }

    companion object {
        const val SERVICE_ID = "nuvio"
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_PROFILE = "profile"
    }
}

/** Every Nuvio URL in one place. Change these to point at a self-hosted server. */
data class NuvioEndpoints(val base: String = DEFAULT_BASE) {
    private val root get() = base.trimEnd('/')

    val login get() = "$root/auth/login"
    val refresh get() = "$root/auth/refresh"
    val me get() = "$root/auth/me"
    fun playback(limit: Int) = "$root/sync/playback?limit=$limit"
    fun rows(category: StreamingServiceAdapter.Category) = when (category) {
        StreamingServiceAdapter.Category.TRENDING -> "$root/catalog/rows?row=trending"
        StreamingServiceAdapter.Category.MOVIES -> "$root/catalog/rows?row=for_you&type=movie"
        StreamingServiceAdapter.Category.SERIES -> "$root/catalog/rows?row=for_you&type=series"
        StreamingServiceAdapter.Category.NEW -> "$root/catalog/rows?row=new"
        StreamingServiceAdapter.Category.FOR_YOU -> "$root/catalog/rows?row=for_you"
    }

    companion object {
        const val DEFAULT_BASE = "https://api.nuvio.app/api/v1"
        const val PACKAGE = "com.nuvio.app"
    }
}

// ---- Wire formats -------------------------------------------------------------------------------
// Every field is optional with a sane default: a server adding or dropping a key must never crash the
// launcher, and the mapper drops anything it cannot make sense of.

@Serializable
data class NuvioSession(
    @SerialName("accessToken") val accessToken: String = "",
    @SerialName("refreshToken") val refreshToken: String? = null,
    @SerialName("profile") val profile: NuvioProfile? = null,
)

@Serializable
data class NuvioProfile(val id: String = "", val name: String = "")

@Serializable
data class NuvioPlaybackResponse(val items: List<NuvioItem> = emptyList())

@Serializable
data class NuvioRow(
    val id: String = "",
    val title: String = "",
    @SerialName("items") val items: List<NuvioItem> = emptyList(),
)

@Serializable
data class NuvioItem(
    val id: String = "",
    val type: String = "movie",
    val title: String = "",
    val name: String? = null,
    val year: Int? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val accent: String? = null,
    val episode: NuvioEpisode? = null,
    @SerialName("positionMs") val positionMs: Long? = null,
    @SerialName("durationMs") val durationMs: Long? = null,
    @SerialName("updatedAt") val updatedAt: Long? = null,
)

@Serializable
data class NuvioEpisode(
    val id: String? = null,
    val season: Int? = null,
    val number: Int? = null,
    val title: String? = null,
)

/** Pure mapping, so the awkward cases have unit tests and no Android types. */
object NuvioMapper {

    fun toMediaItem(item: NuvioItem, serviceId: String): MediaItem? {
        val title = item.title.ifBlank { item.name.orEmpty() }.trim()
        if (title.isBlank() || item.id.isBlank()) return null

        val kind = when (item.type.lowercase()) {
            "movie", "film" -> MediaItem.Kind.MOVIE
            "series", "show", "tv" -> MediaItem.Kind.SERIES
            "episode" -> MediaItem.Kind.EPISODE
            "live", "channel" -> MediaItem.Kind.LIVE
            else -> MediaItem.Kind.MOVIE
        }

        val progress = progressOf(item.positionMs, item.durationMs)
        val episodeLabel = episodeLabel(item)
        return MediaItem(
            id = "$serviceId:${item.id}",
            serviceId = serviceId,
            title = title,
            subtitle = subtitleFor(item, progress),
            kind = kind,
            posterUrl = item.poster?.takeIf { it.startsWith("http") },
            backdropUrl = item.backdrop?.takeIf { it.startsWith("http") },
            progress = progress,
            episodeLabel = episodeLabel,
            deepLink = deepLinkFor(item, kind),
            accent = item.accent?.normaliseHex(),
            addedAt = item.updatedAt ?: 0L,
        )
    }

    fun progressOf(position: Long?, duration: Long?): Float? {
        if (position == null || duration == null || duration <= 0L) return null
        return (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    fun episodeLabel(item: NuvioItem): String? {
        val episode = item.episode ?: return null
        val season = episode.season
        val number = episode.number
        if (season == null || number == null) return episode.title
        val prefix = "S$season E$number"
        val name = episode.title?.takeIf { it.isNotBlank() }
        return if (name != null) "$prefix · $name" else prefix
    }

    /** "S2 E4 · 42 min left" for something in progress, "2023" for something watched later. */
    fun subtitleFor(item: NuvioItem, progress: Float?): String? {
        val episode = episodeLabel(item)
        val remaining = remainingLabel(item.positionMs, item.durationMs)
        return when {
            episode != null && remaining != null -> "$episode · $remaining"
            episode != null -> episode
            remaining != null -> remaining
            item.year != null -> item.year.toString()
            else -> null
        }
    }

    fun remainingLabel(position: Long?, duration: Long?): String? {
        if (position == null || duration == null || duration <= position) return null
        val minutes = ((duration - position) / 60_000L).coerceAtLeast(1L)
        return when {
            minutes < 60 -> "$minutes min left"
            else -> "${minutes / 60} h ${minutes % 60} min left"
        }
    }

    /** The URI the official Nuvio app understands. */
    fun deepLinkFor(item: NuvioItem, kind: MediaItem.Kind): String? {
        if (item.id.isBlank()) return null
        val type = when (kind) {
            MediaItem.Kind.MOVIE -> "movie"
            MediaItem.Kind.SERIES, MediaItem.Kind.EPISODE -> "series"
            MediaItem.Kind.LIVE -> "live"
        }
        val builder = StringBuilder("nuvio://open?id=").append(percentEncode(item.id)).append("&type=").append(type)
        item.episode?.id?.let { builder.append("&episode=").append(percentEncode(it)) }
        return builder.toString()
    }

    /**
     * RFC 3986 encoding for a URI query value. Hand-rolled rather than `Uri.encode` so the mapper
     * stays a pure function: it is unit-tested on the JVM, where `android.net.Uri` is a stub.
     */
    internal fun percentEncode(value: String): String {
        val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val out = StringBuilder(value.length)
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val char = byte.toInt().toChar()
            if (char in unreserved) {
                out.append(char)
            } else {
                out.append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
        return out.toString()
    }

    private fun String.normaliseHex(): String? {
        val cleaned = trim()
        val withHash = if (cleaned.startsWith("#")) cleaned else "#$cleaned"
        return if (Regex("^#[0-9a-fA-F]{6}$").matches(withHash)) withHash else null
    }
}
