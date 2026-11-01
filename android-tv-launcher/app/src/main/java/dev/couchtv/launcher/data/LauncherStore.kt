package dev.couchtv.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher")

/**
 * The launcher's memory: which apps the user chose (in order), their favourites, and every setting.
 *
 * Ordering matters — the home screen draws the dock in the order the user picked — so the choice is
 * stored as one newline-joined string rather than a set. Writes are serialised by DataStore, which
 * means a remote command and a settings tap arriving together cannot lose one another.
 *
 * No credentials are stored here. The Nuvio token lives in EncryptedSharedPreferences (see
 * `streaming/NuvioCredentials.kt`).
 */
class LauncherStore(private val context: Context) {

    private object Keys {
        val selected = stringPreferencesKey("selected_apps")
        val onboarded = booleanPreferencesKey("onboarded")
        val theme = stringPreferencesKey("theme")
        val saverDelay = intPreferencesKey("saver_delay_minutes")
        val saverClock = booleanPreferencesKey("saver_clock")
        val remoteEnabled = booleanPreferencesKey("remote_enabled")
        val remotePort = intPreferencesKey("remote_port")
        val autostart = booleanPreferencesKey("autostart")
        val notificationsRow = booleanPreferencesKey("notifications_row")
        val parentalEnabled = booleanPreferencesKey("parental_enabled")
        val parentalPin = stringPreferencesKey("parental_pin_digest")
        val locked = stringSetPreferencesKey("locked_packages")
        val pins = stringPreferencesKey("pinned_items")
        val nuvioServer = stringPreferencesKey("nuvio_server")
        val nuvioAccount = stringPreferencesKey("nuvio_account")
        val seenPackages = stringSetPreferencesKey("seen_packages")
    }

    private val store get() = context.launcherDataStore

    /** The chosen apps, in home-screen order. */
    val selectedApps: Flow<List<String>> = store.data.map { prefs ->
        prefs[Keys.selected]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    }

    /** Favourites: app package names, and `service:contentId` for streaming items. */
    val pins: Flow<List<String>> = store.data.map { prefs ->
        prefs[Keys.pins]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    }

    val settings: Flow<LauncherSettings> = store.data.map { prefs ->
        LauncherSettings(
            onboarded = prefs[Keys.onboarded] ?: false,
            theme = runCatching { ThemeMode.valueOf(prefs[Keys.theme] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
            saverDelayMinutes = (prefs[Keys.saverDelay] ?: 5).coerceIn(1, 60),
            saverClock = prefs[Keys.saverClock] ?: true,
            remoteEnabled = prefs[Keys.remoteEnabled] ?: true,
            remotePort = (prefs[Keys.remotePort] ?: 8765).coerceIn(1024, 65535),
            autostart = prefs[Keys.autostart] ?: false,
            notificationsRow = prefs[Keys.notificationsRow] ?: true,
            parentalEnabled = prefs[Keys.parentalEnabled] ?: false,
            lockedPackages = prefs[Keys.locked] ?: emptySet(),
            nuvioServer = prefs[Keys.nuvioServer] ?: "",
            nuvioAccount = prefs[Keys.nuvioAccount],
        )
    }

    /** Packages the user has already been offered after an install, so the prompt appears once. */
    val seenPackages: Flow<Set<String>> = store.data.map { it[Keys.seenPackages] ?: emptySet() }

    suspend fun snapshot(): LauncherSettings = settings.first()

    suspend fun setSelected(packages: List<String>) {
        store.edit { it[Keys.selected] = packages.distinct().joinToString("\n") }
    }

    suspend fun toggleSelected(packageName: String) {
        store.edit { prefs ->
            val current = prefs[Keys.selected]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
            val next = if (packageName in current) current - packageName else current + packageName
            prefs[Keys.selected] = next.joinToString("\n")
        }
    }

    suspend fun reorder(from: Int, to: Int) {
        store.edit { prefs ->
            val current = (prefs[Keys.selected]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()).toMutableList()
            if (from !in current.indices || to !in current.indices) return@edit
            val item = current.removeAt(from)
            current.add(to, item)
            prefs[Keys.selected] = current.joinToString("\n")
        }
    }

    suspend fun setOnboarded(value: Boolean) = store.edit { it[Keys.onboarded] = value }

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }

    suspend fun setSaverDelay(minutes: Int) = store.edit { it[Keys.saverDelay] = minutes.coerceIn(1, 60) }

    suspend fun setSaverClock(on: Boolean) = store.edit { it[Keys.saverClock] = on }

    suspend fun setRemoteEnabled(enabled: Boolean) = store.edit { it[Keys.remoteEnabled] = enabled }

    suspend fun setRemotePort(port: Int) = store.edit { it[Keys.remotePort] = port.coerceIn(1024, 65535) }

    suspend fun setAutostart(enabled: Boolean) = store.edit { it[Keys.autostart] = enabled }

    suspend fun setNotificationsRow(enabled: Boolean) = store.edit { it[Keys.notificationsRow] = enabled }

    suspend fun setParentalEnabled(enabled: Boolean) = store.edit { it[Keys.parentalEnabled] = enabled }

    /** The PIN is stored as a salted SHA-256 digest, never in the clear. */
    suspend fun setParentalPin(digest: String?) = store.edit { prefs ->
        if (digest == null) prefs.remove(Keys.parentalPin) else prefs[Keys.parentalPin] = digest
    }

    suspend fun parentalPinDigest(): String? = store.data.first()[Keys.parentalPin]

    suspend fun setLocked(packages: Set<String>) = store.edit { it[Keys.locked] = packages }

    suspend fun toggleLocked(packageName: String) = store.edit { prefs ->
        val current = prefs[Keys.locked] ?: emptySet()
        prefs[Keys.locked] = if (packageName in current) current - packageName else current + packageName
    }

    suspend fun togglePin(id: String) = store.edit { prefs ->
        val current = prefs[Keys.pins]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        val next = if (id in current) current - id else current + id
        prefs[Keys.pins] = next.joinToString("\n")
    }

    suspend fun markSeen(packageName: String) = store.edit { prefs ->
        prefs[Keys.seenPackages] = (prefs[Keys.seenPackages] ?: emptySet()) + packageName
    }

    suspend fun setNuvio(server: String, account: String?) = store.edit { prefs ->
        prefs[Keys.nuvioServer] = server
        if (account == null) prefs.remove(Keys.nuvioAccount) else prefs[Keys.nuvioAccount] = account
    }

    /** Settings › Reset: forget the choices, keep the streaming sign-in. */
    suspend fun resetHome() = store.edit { prefs ->
        prefs.remove(Keys.selected)
        prefs.remove(Keys.onboarded)
        prefs.remove(Keys.pins)
    }
}
