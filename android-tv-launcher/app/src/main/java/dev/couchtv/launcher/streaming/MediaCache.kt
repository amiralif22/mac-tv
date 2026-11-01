package dev.couchtv.launcher.streaming

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.mediaCacheStore: DataStore<Preferences> by preferencesDataStore(name = "media_cache")

/**
 * The last good answer from every streaming service, on disk.
 *
 * The home screen must paint in under half a second, which rules out waiting for a network call.
 * So the hub reads this cache first, paints, and refreshes behind the glass. If the refresh fails the
 * cached rows stay, with a small "offline" note, for up to [STALE_AFTER_MS]; after that they are
 * dropped so a forgotten account cannot show last year's library.
 */
class MediaCache(private val context: Context) {

    data class Entry(val json: String, val fetchedAt: Long)

    suspend fun read(key: String): Entry? = runCatching {
        val prefs = context.mediaCacheStore.data.first()
        val json = prefs[stringPreferencesKey(key)] ?: return null
        val at = prefs[longPreferencesKey("$key.at")] ?: 0L
        Entry(json, at)
    }.getOrNull()

    suspend fun write(key: String, json: String) {
        runCatching {
            context.mediaCacheStore.edit { prefs ->
                prefs[stringPreferencesKey(key)] = json
                prefs[longPreferencesKey("$key.at")] = System.currentTimeMillis()
            }
        }
    }

    suspend fun clear() {
        runCatching { context.mediaCacheStore.edit { it.clear() } }
    }

    fun isStale(fetchedAt: Long): Boolean =
        fetchedAt == 0L || System.currentTimeMillis() - fetchedAt > STALE_AFTER_MS

    companion object {
        /** A day: long enough for a weekend offline, short enough not to lie for a week. */
        const val STALE_AFTER_MS = 24L * 60 * 60 * 1000
    }
}
