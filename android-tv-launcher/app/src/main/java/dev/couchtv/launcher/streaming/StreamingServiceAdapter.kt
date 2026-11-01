package dev.couchtv.launcher.streaming

import android.content.Context
import android.content.Intent

/**
 * The seam between the launcher and a streaming service.
 *
 * Adding a service means writing one implementation of this interface and registering it in
 * `StreamingHub.builtIn` — nothing in the UI changes. The contract is documented in
 * `shared/streaming-adapters.md`; the shape here mirrors the Mac app's approach of opening a
 * service's own player rather than re-implementing it.
 *
 * Rules every adapter follows:
 * - Never throw to the caller: return an empty list or an [AuthResult] failure.
 * - Never block the main thread: every method is `suspend` (or pure).
 * - Never cache secrets itself: credentials go through the platform keystore.
 * - Return items even when the official app is missing; the launcher offers to install it.
 */
interface StreamingServiceAdapter {

    /** Stable id: storage keys, cache files, pins. */
    val id: String

    /** Shown in Settings and in the connect sheet. */
    val displayName: String

    /** The official app's package name. */
    val packageName: String

    /** Which rows this adapter can fill, so the launcher hides rows that would stay empty. */
    val capabilities: Set<Capability>

    suspend fun authenticate(credentials: Credentials): AuthResult

    /** Restores a stored session and returns true when it is usable. */
    suspend fun restoreSession(): Boolean

    suspend fun signOut()

    /** True when a session exists and has not been revoked. */
    suspend fun isSignedIn(): Boolean

    suspend fun getContinueWatching(limit: Int = 20): List<MediaItem>

    suspend fun getRecommendations(category: Category, limit: Int = 20): List<MediaItem>

    /** Where this item opens in the official app, or null when the item cannot be opened. */
    fun getDeepLink(item: MediaItem): Intent?

    fun isInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        }.getOrDefault(false)

    /** The app's own launch intent, for the "open it" fallback. */
    fun launchIntent(context: Context): Intent? = runCatching {
        context.packageManager.getLaunchIntentForPackage(packageName)
    }.getOrNull()

    enum class Capability { CONTINUE_WATCHING, RECOMMENDATIONS, TRENDING, NEW_RELEASES, SEARCH }

    enum class Category { TRENDING, MOVIES, SERIES, NEW, FOR_YOU }

    /** How the service signs in. */
    sealed class Credentials {
        data class Password(val user: String, val password: String, val server: String? = null) : Credentials()
        data class Token(val accessToken: String, val refreshToken: String? = null) : Credentials()
        /** Some services prefer a code shown on the TV and entered on a phone. */
        data class DeviceCode(val code: String, val server: String? = null) : Credentials()
    }

    sealed class AuthResult {
        data class Success(val profileName: String) : AuthResult()
        data object NeedsTwoFactor : AuthResult()
        data object Invalid : AuthResult()
        data class NetworkError(val message: String) : AuthResult()
    }
}
