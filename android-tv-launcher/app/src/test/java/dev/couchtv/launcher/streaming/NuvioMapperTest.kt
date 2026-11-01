package dev.couchtv.launcher.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mapper is where a streaming API's shape meets the launcher's; it must be forgiving. Every case
 * here is a real shape: a movie, a series with a next episode, something half-watched, and the
 * rubbish a server can return at 2 a.m.
 */
class NuvioMapperTest {

    @Test
    fun `a movie maps to a poster card`() {
        val item = NuvioMapper.toMediaItem(
            NuvioItem(
                id = "tt1",
                type = "movie",
                title = "Dune",
                year = 2021,
                poster = "https://cdn/poster.jpg",
                accent = "#C69A52",
            ),
            serviceId = "nuvio",
        )!!
        assertEquals("nuvio:tt1", item.id)
        assertEquals("Dune", item.title)
        assertEquals(MediaItem.Kind.MOVIE, item.kind)
        assertEquals("2021", item.subtitle)
        assertEquals("#C69A52", item.accent)
        assertNull(item.progress)
    }

    @Test
    fun `a series in progress keeps the episode and the time left`() {
        val item = NuvioMapper.toMediaItem(
            NuvioItem(
                id = "tt2",
                type = "series",
                title = "The Bear",
                episode = NuvioEpisode(id = "e4", season = 2, number = 4, title = "Tailgate"),
                positionMs = 1_800_000,
                durationMs = 2_400_000,
            ),
            serviceId = "nuvio",
        )!!
        assertEquals(MediaItem.Kind.SERIES, item.kind)
        assertEquals("S2 E4 · Tailgate", item.episodeLabel)
        assertEquals("S2 E4 · Tailgate · 10 min left", item.subtitle)
        assertEquals(0.75f, item.progress!!, 0.001f)
        assertTrue(item.deepLink!!.startsWith("nuvio://open?id=tt2"))
        assertTrue(item.deepLink!!.contains("episode=e4"))
    }

    @Test
    fun `progress is only offered for a real duration`() {
        assertNull(NuvioMapper.progressOf(null, 100))
        assertNull(NuvioMapper.progressOf(10, null))
        assertNull(NuvioMapper.progressOf(10, 0))
        assertEquals(0.5f, NuvioMapper.progressOf(50, 100)!!, 0.001f)
        assertEquals(1f, NuvioMapper.progressOf(500, 100)!!, 0.001f)
    }

    @Test
    fun `titles and ids are required, everything else is optional`() {
        assertNull(NuvioMapper.toMediaItem(NuvioItem(id = "", title = "No id"), "nuvio"))
        assertNull(NuvioMapper.toMediaItem(NuvioItem(id = "x", title = "  "), "nuvio"))

        val bare = NuvioMapper.toMediaItem(NuvioItem(id = "x", title = "Bare"), "nuvio")!!
        assertEquals(null, bare.posterUrl)
        assertEquals(null, bare.subtitle)
        assertEquals(null, bare.progress)
    }

    @Test
    fun `non-http poster urls are dropped so coil never sees them`() {
        val item = NuvioMapper.toMediaItem(
            NuvioItem(id = "x", title = "Odd", poster = "file:///etc/passwd"),
            "nuvio",
        )!!
        assertNull(item.posterUrl)
    }

    @Test
    fun `remaining time reads naturally`() {
        assertEquals("42 min left", NuvioMapper.remainingLabel(0, 42 * 60_000))
        assertEquals("1 h 5 min left", NuvioMapper.remainingLabel(0, 65 * 60_000))
        assertNull(NuvioMapper.remainingLabel(100, 100))
        assertNull(NuvioMapper.remainingLabel(null, 100))
    }
}
