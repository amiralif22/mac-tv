package dev.couchtv.launcher.data

/**
 * What we can learn about an app's TV-friendliness, without running it.
 *
 * Deliberately a plain data class and a pure function: the heuristics are the part most likely to be
 * wrong on some box in some living room, so they are unit-tested (`TvCompatDetectorTest`) and can be
 * explained to the user.
 */
data class CompatSignals(
    /** The package has an activity with the LEANBACK_LAUNCHER category: made for Android TV. */
    val hasLeanbackLauncher: Boolean = false,
    /** The application declares a TV banner (Android TV's home-screen artwork). */
    val hasTvBanner: Boolean = false,
    /** The package has any launcher activity at all. */
    val hasLauncherEntry: Boolean = true,
    /** The manifest requires a touchscreen, which a TV does not have. */
    val requiresTouchscreen: Boolean = false,
    /** The APK references the Leanback support library. Only knowable for APK files we can read. */
    val referencesLeanbackLibrary: Boolean = false,
    /** It is a game: usable on a TV, but almost never with a D-pad alone. */
    val isGame: Boolean = false,
    /** The user told us it doesn't work with the remote (stored per package). */
    val userMarkedTouchOnly: Boolean = false,
) {
    /**
     * The decision, in the order that matters:
     *
     * 1. A leanback entry is proof — that category exists *only* for TV launchers.
     * 2. A TV banner plus a launcher entry is strong evidence (Google Play requires the banner for TV
     *    listings, so TV builds almost always carry one).
     * 3. Referencing the Leanback library is good evidence, but a phone app can link it too.
     * 4. A required touchscreen on a device with none, or the user telling us, means touch-only.
     * 5. Anything else is unknown: we show the badge, and the swap prompt only on TOUCH_ONLY.
     */
    fun evaluate(): TvCompat = when {
        userMarkedTouchOnly -> TvCompat.TOUCH_ONLY
        hasLeanbackLauncher -> TvCompat.TV_OPTIMISED
        hasTvBanner && hasLauncherEntry -> TvCompat.TV_OPTIMISED
        referencesLeanbackLibrary && hasLauncherEntry && !isGame -> TvCompat.TV_OPTIMISED
        requiresTouchscreen -> TvCompat.TOUCH_ONLY
        isGame -> TvCompat.UNKNOWN
        else -> TvCompat.UNKNOWN
    }

    companion object {
        /**
         * Markers searched for inside an APK's manifest and code. Leanback apps advertise the
         * category in their manifest's string pool; a build that merely links the library usually
         * still keeps its class names. Both are cheap to find in the file's bytes.
         */
        val LEANBACK_MARKERS = listOf(
            "LEANBACK_LAUNCHER",
            "androidx/leanback",
            "androidx.leanback",
            "android.software.leanback",
        )
    }
}

/**
 * Takes the raw signals and says whether the app is worth showing a warning badge for.
 * Kept next to the enum so the UI never re-implements the policy.
 */
fun TvCompat.needsRemoteWarning(): Boolean = this == TvCompat.TOUCH_ONLY

fun TvCompat.shouldOfferTvVersion(): Boolean = this == TvCompat.TOUCH_ONLY
