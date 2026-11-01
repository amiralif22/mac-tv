package dev.couchtv.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import dev.couchtv.launcher.util.IconTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/**
 * Everything the launcher knows about what is installed.
 *
 * One scan produces the whole list the picker and the home screen use; the verdict for each package
 * comes from [AppCompatDatabase] when it is current, and from [CompatSignals] when it is not. The
 * scan runs on [Dispatchers.IO] — it touches the package manager dozens of times — and the result is
 * cached in memory so switching to Manage Apps is instant.
 */
class AppCatalog(private val context: Context, private val compatDb: AppCompatDatabase) {

    private val packageManager: PackageManager get() = context.packageManager
    private val iconCache = LruCache<String, Drawable>(96)

    @Volatile
    private var cached: List<AppEntry> = emptyList()

    /** Everything installed, sorted the way the picker shows it. */
    suspend fun installed(force: Boolean = false): List<AppEntry> = withContext(Dispatchers.IO) {
        if (!force && cached.isNotEmpty()) return@withContext cached
        val found = mutableMapOf<String, AppEntry>()

        // 1. Anything with a launcher icon: this is what a phone or tablet home screen would show.
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

        for (category in listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)) {
            val intent = if (category == Intent.CATEGORY_LAUNCHER) launcherIntent else leanbackIntent
            val resolved = runCatching {
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }.getOrDefault(emptyList())
            for (info in resolved) {
                val packageName = info.activityInfo.packageName
                if (packageName == context.packageName) continue
                if (packageName in found) continue
                val entry = runCatching { entryFor(packageName, info.activityInfo.name) }.getOrNull() ?: continue
                found[packageName] = entry
            }
        }

        val collator = Collator.getInstance(Locale.getDefault())
        val sorted = found.values.sortedWith(
            compareBy<AppEntry> { it.category.ordinal }.thenComparator { a, b -> collator.compare(a.label, b.label) }
        )
        cached = sorted
        // The cache is a convenience; losing entries only costs a rescan, so prune lazily.
        runCatching { compatDb.pruneExcept(found.keys) }
        sorted
    }

    /** The cached list, synchronously: for first frame and for the picker's initial state. */
    fun cachedApps(): List<AppEntry> = cached

    private fun entryFor(packageName: String, activityName: String): AppEntry {
        val info = packageManager.getApplicationInfo(packageName, 0)
        val versionCode = packageVersion(packageName)
        val label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
            ?: packageName.substringAfterLast('.')

        val leanback = runCatching {
            packageManager.getLeanbackLaunchIntentForPackage(packageName) != null
        }.getOrDefault(false)
        val banner = info.banner != 0

        val stored = compatDb.get(packageName, versionCode)
        val status = stored?.status ?: CompatSignals(
            hasLeanbackLauncher = leanback,
            hasTvBanner = banner,
            hasLauncherEntry = true,
            isGame = isGame(info),
        ).evaluate().also { verdict ->
            compatDb.put(
                AppCompatDatabase.Record(
                    packageName = packageName,
                    versionCode = versionCode,
                    label = label,
                    status = verdict,
                    hasBanner = banner,
                    hasLeanback = leanback,
                    checkedAt = System.currentTimeMillis(),
                )
            )
        }

        return AppEntry(
            packageName = packageName,
            // A leanback activity is the right target on a TV even when a phone activity exists too.
            activityName = leanbackActivity(packageName) ?: activityName,
            label = label,
            category = categoryOf(packageName, info, label),
            compat = status,
            accent = accentOf(packageName),
            isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            installedAt = runCatching { packageManager.getPackageInfo(packageName, 0).firstInstallTime }
                .getOrDefault(0L),
        )
    }

    private fun leanbackActivity(packageName: String): String? = runCatching {
        packageManager.getLeanbackLaunchIntentForPackage(packageName)?.component?.className
    }.getOrNull()

    private fun packageVersion(packageName: String): Long = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }.getOrDefault(0L)

    private fun isGame(info: ApplicationInfo): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && info.category == ApplicationInfo.CATEGORY_GAME

    /** A cached icon, or the system's default. Never throws: a broken APK still gets a tile. */
    fun icon(packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }
        val drawable = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull() ?: return null
        iconCache.put(packageName, drawable)
        return drawable
    }

    /** The icon's dominant colour, for tiles drawn without artwork. */
    fun accent(packageName: String): Int = accentCache.getOrPut(packageName) {
        val drawable = icon(packageName) ?: return@getOrPut DEFAULT_ACCENT
        runCatching { IconTools.dominantColor(drawable) }.getOrDefault(DEFAULT_ACCENT)
    }

    private val accentCache = HashMap<String, Int>()
    private fun accentOf(packageName: String): Int = accent(packageName)

    /** The intent that starts the app, or null when its launcher entry disappeared. */
    fun launchIntent(entry: AppEntry): Intent? {
        val explicit = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(entry.packageName, entry.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (runCatching { packageManager.resolveActivity(explicit, 0) }.getOrNull() != null) return explicit
        return runCatching { packageManager.getLaunchIntentForPackage(entry.packageName) }
            .getOrNull()
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun isInstalled(packageName: String): Boolean = runCatching {
        packageManager.getApplicationInfo(packageName, 0)
        true
    }.getOrDefault(false)

    /** Known streaming packages, so onboarding can group sensibly before any heuristic runs. */
    private fun categoryOf(packageName: String, info: ApplicationInfo, label: String): AppCategory {
        val key = packageName.lowercase(Locale.ROOT)
        val name = label.lowercase(Locale.ROOT)
        if (STREAMING_HINTS.any { key.contains(it) || name.contains(it) }) return AppCategory.STREAMING
        if (isGame(info) || GAME_HINTS.any { key.contains(it) }) return AppCategory.GAMES
        if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return AppCategory.SYSTEM
        if (UTILITY_HINTS.any { key.contains(it) }) return AppCategory.UTILITIES
        return AppCategory.OTHER
    }

    private companion object {
        const val DEFAULT_ACCENT = 0xFF2C2C2E.toInt()

        /** Streaming services people put on a TV. Matching is on package and label substrings. */
        val STREAMING_HINTS = listOf(
            "netflix", "youtube", "prime", "disney", "hulu", "hbo", "max", "spotify", "hulu",
            "jellyfin", "plex", "kodi", "nuvio", "vlc", "twitch", "hotstar", "zee5", "sonyliv",
            "sony.liv", "emby", "crunchyroll", "paramount", "peacock", "apple.tv", "appletv", "mubi",
            "stremio", "iptv", "tubi", "rakuten", "sky", "dazn", "viaplay", "joyn", "ardmediathek",
        )
        val GAME_HINTS = listOf("game", "xbox", "stadia", "luna", "steam", "retroarch", "emulator")
        val UTILITY_HINTS = listOf(
            "browser", "chrome", "firefox", "file", "settings", "vpn", "keyboard", "launcher",
            "store", "play", "calc", "clock", "camera",
        )
    }
}
