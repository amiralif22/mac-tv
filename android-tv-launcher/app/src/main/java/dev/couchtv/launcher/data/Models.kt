package dev.couchtv.launcher.data

/**
 * A thing the launcher can show. Kept free of Android types wherever the UI does not need them, so
 * the picker, the focus engine and the tests can work on plain data.
 */
data class AppEntry(
    /** Package name; the identity the user's choices are stored against. */
    val packageName: String,
    /** The activity a launcher intent starts. */
    val activityName: String,
    val label: String,
    val category: AppCategory,
    val compat: TvCompat,
    /** The icon's dominant colour, as 0xAARRGGBB, used for locally drawn tiles and placeholder art. */
    val accent: Int,
    val isSystem: Boolean,
    val installedAt: Long,
) {
    val id: String get() = packageName
}

enum class AppCategory { STREAMING, GAMES, UTILITIES, SYSTEM, OTHER }

/** How well an installed build behaves with a D-pad. Stored per package in [AppCompatDatabase]. */
enum class TvCompat {
    /** Has a leanback launcher entry or a TV banner: made for a remote. */
    TV_OPTIMISED,

    /** A touch-first build on a TV. Usable, but the user should be offered the TV APK. */
    TOUCH_ONLY,

    /** Nothing in the manifest told us either way. */
    UNKNOWN,
}

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** Everything the user can change. Persisted by [LauncherStore]; no credentials here. */
data class LauncherSettings(
    val onboarded: Boolean = false,
    val theme: ThemeMode = ThemeMode.DARK,
    val saverDelayMinutes: Int = 5,
    val saverClock: Boolean = true,
    val remoteEnabled: Boolean = true,
    val remotePort: Int = 8765,
    val autostart: Boolean = false,
    val notificationsRow: Boolean = true,
    val parentalEnabled: Boolean = false,
    val lockedPackages: Set<String> = emptySet(),
    val nuvioServer: String = "",
    val nuvioAccount: String? = null,
) {
    /** Locking is only meaningful when a PIN exists; keeps the UI honest. */
    val parentalActive: Boolean get() = parentalEnabled && lockedPackages.isNotEmpty()
}

/** One card on the home screen, whichever screen draws it. */
data class NotificationItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
)
