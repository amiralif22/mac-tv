package dev.couchtv.launcher.streaming

import kotlinx.serialization.Serializable

/**
 * One thing a streaming service wants to show on the home screen.
 *
 * Deliberately flat and serialisable: it is cached as JSON, so a cold boot can paint the whole home
 * screen from disk before any network call finishes. `posterUrl` may be null — the launcher draws its
 * own artwork from `accent` rather than leaving a gap.
 */
@Serializable
data class MediaItem(
    /** Stable within the adapter: "nuvio:tt0903747". */
    val id: String,
    val serviceId: String,
    val title: String,
    /** "S2 E4 · 42 min left" or "2023 · 1h 59m". */
    val subtitle: String? = null,
    val kind: Kind = Kind.MOVIE,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    /** 0..1 for Continue Watching only. */
    val progress: Float? = null,
    val episodeLabel: String? = null,
    /** What the adapter wants to open, if it is not a plain app launch. */
    val deepLink: String? = null,
    /** "#RRGGBB" brand colour, used when there is no poster. */
    val accent: String? = null,
    val addedAt: Long = 0L,
) {
    enum class Kind { MOVIE, SERIES, EPISODE, LIVE }
}

/** A titled row of items, ready for the home screen. */
data class StreamingRow(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
    val serviceId: String? = null,
)
