package dev.couchtv.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvCompatDetectorTest {

    @Test
    fun `a leanback launcher entry is proof`() {
        assertEquals(
            TvCompat.TV_OPTIMISED,
            CompatSignals(hasLeanbackLauncher = true).evaluate(),
        )
    }

    @Test
    fun `a TV banner on a launcher app is strong evidence`() {
        assertEquals(
            TvCompat.TV_OPTIMISED,
            CompatSignals(hasTvBanner = true, hasLauncherEntry = true).evaluate(),
        )
    }

    @Test
    fun `linking leanback counts, except for games`() {
        assertEquals(
            TvCompat.TV_OPTIMISED,
            CompatSignals(referencesLeanbackLibrary = true).evaluate(),
        )
        assertEquals(
            TvCompat.UNKNOWN,
            CompatSignals(referencesLeanbackLibrary = true, isGame = true).evaluate(),
        )
    }

    @Test
    fun `requiring a touchscreen means touch only`() {
        assertEquals(TvCompat.TOUCH_ONLY, CompatSignals(requiresTouchscreen = true).evaluate())
    }

    @Test
    fun `the user's correction wins over every heuristic`() {
        assertEquals(
            TvCompat.TOUCH_ONLY,
            CompatSignals(hasLeanbackLauncher = true, userMarkedTouchOnly = true).evaluate(),
        )
    }

    @Test
    fun `silence is unknown, and only touch-only apps are warned about`() {
        val verdict = CompatSignals().evaluate()
        assertEquals(TvCompat.UNKNOWN, verdict)
        assertFalse(verdict.needsRemoteWarning())
        assertTrue(TvCompat.TOUCH_ONLY.needsRemoteWarning())
        assertTrue(TvCompat.TOUCH_ONLY.shouldOfferTvVersion())
        assertFalse(TvCompat.TV_OPTIMISED.needsRemoteWarning())
    }
}
