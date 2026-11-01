package dev.couchtv.launcher.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager

/**
 * Which layout a device wants, and how big its canvas is.
 *
 * The Mac app lays everything out on a 1920×1080 point canvas and scales it to the window. Android
 * TV boxes and tablets report about 960×540 dp for that same frame, so [canvasWidthDp] is 960 at
 * reference and every tvOS size from `Typography.swift` is halved and then scaled by
 * [scale]. One set of composables therefore looks right on a 40-inch TV and a 10-inch tablet.
 */
data class DeviceProfile(
    val kind: Kind,
    val canvasWidthDp: Float,
    val canvasHeightDp: Float,
    val hasDpad: Boolean,
    val isTouchPrimary: Boolean,
) {
    enum class Kind { LEANBACK, TABLET, PHONE }

    /** 1.0 on a 1080p Android TV; 0.6 on a small tablet in portrait. */
    val scale: Float get() = (canvasWidthDp / REFERENCE_WIDTH_DP).coerceIn(0.55f, 2.4f)

    /** True when labels should always be visible (touch devices) rather than only on focus. */
    val alwaysShowLabels: Boolean get() = kind != Kind.LEANBACK

    companion object {
        const val REFERENCE_WIDTH_DP = 960f

        fun of(context: Context): DeviceProfile {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val metrics = DisplayMetrics().also { displayMetrics ->
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay?.getRealMetrics(displayMetrics)
            }
            val configuration = context.resources.configuration
            val wDp = metrics.widthPixels / metrics.density
            val hDp = metrics.heightPixels / metrics.density
            val shortest = minOf(wDp, hDp)

            val leanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                context.packageManager.hasSystemFeature("android.hardware.type.television") ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
            val hasDpad = configuration.navigation != Configuration.NAVIGATION_NONAV &&
                configuration.navigation != Configuration.NAVIGATION_UNDEFINED
            val touch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

            val kind = when {
                leanback || (shortest >= 600f && !touch) -> Kind.LEANBACK
                shortest >= 600f -> Kind.TABLET
                else -> Kind.PHONE
            }

            // In portrait the canvas is the shortest side, so rows still fit; landscape uses width.
            val landscape = wDp >= hDp
            return DeviceProfile(
                kind = kind,
                canvasWidthDp = if (landscape) wDp else hDp,
                canvasHeightDp = if (landscape) hDp else wDp,
                hasDpad = hasDpad || leanback,
                isTouchPrimary = touch && !leanback,
            )
        }
    }
}

/** The overscan-safe inset Android TV recommends, from Leanback's own guidelines. */
fun View.applyTvSafeInsets() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return
    val safe = runCatching { androidx.leanback.system.Settings.getInstance(context).safeInsetLeft }.getOrDefault(0)
    if (safe <= 0) return
    setPadding(safe, safe, safe, safe)
}
