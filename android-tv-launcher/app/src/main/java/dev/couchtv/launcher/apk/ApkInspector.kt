package dev.couchtv.launcher.apk

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import dev.couchtv.launcher.data.CompatSignals
import dev.couchtv.launcher.data.TvCompat
import java.io.File
import java.io.FileInputStream

/**
 * Reads an APK *file* before it is installed: what it is, what it is called, and whether it was built
 * for a TV.
 *
 * PackageManager can describe an APK without installing it (`getPackageArchiveInfo`), but it cannot
 * tell us about intent categories — and the LEANBACK_LAUNCHER category is the one piece of hard
 * evidence that a build is TV-native. So we do both:
 *
 * 1. `getPackageArchiveInfo` for the package name, label, version, banner flag and icon (pointing
 *    `sourceDir` at the file makes label/icon loading work for an uninstalled APK).
 * 2. A byte scan of the archive for the leanback markers. A binary AndroidManifest stores its string
 *    pool as UTF-8 (or UTF-16 on older toolchains), so looking for `LEANBACK_LAUNCHER` and
 *    `androidx/leanback` in the raw bytes finds both a leanback launcher entry and a build that links
 *    the support library, without an AXML parser.
 *
 * The result is a [CompatSignals] run through the same policy the installed-app scanner uses, so a
 * sideloaded APK and an installed one are judged identically.
 */
class ApkInspector(private val context: Context) {

    data class ApkInfo(
        val file: File,
        val packageName: String,
        val label: String,
        val versionName: String?,
        val versionCode: Long,
        val icon: Drawable?,
        val compat: TvCompat,
        val signals: CompatSignals,
        val sizeBytes: Long,
    ) {
        /** The question the launcher asks after an install. */
        val isTvReady: Boolean get() = compat == TvCompat.TV_OPTIMISED
    }

    fun inspect(file: File): ApkInfo? {
        if (!file.isFile || file.length() == 0L) return null
        val packageManager = context.packageManager
        val flags = PackageManager.GET_META_DATA or
            (if (Build.VERSION.SDK_INT >= 33) PackageManager.GET_ACTIVITIES else 0)
        val archive = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        }.getOrNull() ?: return null
        val appInfo = archive.applicationInfo ?: return null

        // Without these, loading a label or icon from an archive throws.
        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath

        val label = runCatching { appInfo.loadLabel(packageManager)?.toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: archive.packageName.substringAfterLast('.')

        val markers = scanForLeanback(file)
        val signals = CompatSignals(
            // An archive cannot be asked for its leanback intent, so the marker scan stands in.
            hasLeanbackLauncher = markers.leanbackLauncher,
            hasTvBanner = appInfo.banner != 0,
            hasLauncherEntry = true,
            referencesLeanbackLibrary = markers.leanbackLibrary,
            isGame = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME,
        )

        return ApkInfo(
            file = file,
            packageName = archive.packageName,
            label = label,
            versionName = archive.versionName,
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archive.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archive.versionCode.toLong()
            },
            icon = runCatching { appInfo.loadIcon(packageManager) }.getOrNull(),
            compat = if (signals.hasTvBanner && signals.hasLeanbackLauncher) {
                TvCompat.TV_OPTIMISED
            } else {
                signals.evaluate()
            },
            signals = signals,
            sizeBytes = file.length(),
        )
    }

    data class Markers(val leanbackLauncher: Boolean, val leanbackLibrary: Boolean)

    /**
     * Streams the archive once, looking for four byte patterns. An APK is read in 1 MB chunks with a
     * small overlap so a marker straddling a chunk boundary is still found; the scan stops as soon as
     * both answers are known.
     */
    fun scanForLeanback(file: File): Markers {
        var launcher = false
        var library = false
        // Each marker, in both encodings a manifest string pool can use.
        val needles: List<Pair<String, ByteArray>> = listOf(
            "LEANBACK_LAUNCHER",
            "androidx/leanback",
            "androidx.leanback",
            "android.software.leanback",
        ).flatMap { marker ->
            listOf(
                marker to marker.toByteArray(Charsets.UTF_8),
                marker to marker.toByteArray(Charsets.UTF_16LE),
            )
        }
        val chunkSize = 1 shl 20
        val overlap = 64
        runCatching {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(chunkSize + overlap)
                var carry = 0
                while (true) {
                    val read = input.read(buffer, carry, chunkSize)
                    if (read <= 0) break
                    val total = carry + read
                    for ((marker, needle) in needles) {
                        if (contains(buffer, total, needle)) {
                            if (marker == "LEANBACK_LAUNCHER") launcher = true else library = true
                        }
                    }
                    if (launcher && library) break
                    carry = minOf(overlap, total)
                    System.arraycopy(buffer, total - carry, buffer, 0, carry)
                }
            }
        }
        return Markers(launcher, library)
    }

    private fun contains(haystack: ByteArray, length: Int, needle: ByteArray): Boolean {
        if (needle.isEmpty() || length < needle.size) return false
        val last = length - needle.size
        var index = 0
        while (index <= last) {
            if (haystack[index] == needle[0]) {
                var matched = true
                for (offset in 1 until needle.size) {
                    if (haystack[index + offset] != needle[offset]) {
                        matched = false
                        break
                    }
                }
                if (matched) return true
            }
            index++
        }
        return false
    }
}
