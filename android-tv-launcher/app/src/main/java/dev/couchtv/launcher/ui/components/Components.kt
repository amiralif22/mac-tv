package dev.couchtv.launcher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import dev.couchtv.launcher.data.AppEntry
import dev.couchtv.launcher.data.TvCompat
import dev.couchtv.launcher.data.needsRemoteWarning
import dev.couchtv.launcher.streaming.MediaItem
import dev.couchtv.launcher.ui.theme.Motion
import dev.couchtv.launcher.ui.theme.tvDp
import dev.couchtv.launcher.ui.theme.CouchPalette
import dev.couchtv.launcher.ui.theme.CouchShapes
import dev.couchtv.launcher.ui.theme.CouchType
import dev.couchtv.launcher.ui.theme.LocalPalette

/**
 * The launcher's shared pieces: tiles, posters, rows and the frosted glass they sit on.
 *
 * The look is inherited from the Mac app: artwork with a hairline rim, a focused tile that lifts 10 %
 * and gains a bright top edge, a solid white platter for a focused *control*, and text that never
 * drops below the tvOS caption size. Nothing animates for longer than 320 ms or bounces.
 */

/** The Mac's `tvGlass`: a translucent fill, a bright top edge, a hairline rim. */
fun Modifier.glass(
    palette: CouchPalette = LocalPalette.current,
    shape: Shape = RoundedCornerShape(CouchShapes.panel),
    strong: Boolean = false,
): Modifier = this
    .clip(shape)
    .background(if (strong) palette.glassStrong else palette.glass, shape)
    .border(1.dp, palette.hairline, shape)

/** A panel, for sheets and cards: opaque enough to read text on, over any artwork. */
fun Modifier.panel(palette: CouchPalette = LocalPalette.current, shape: Shape = RoundedCornerShape(CouchShapes.panel)): Modifier =
    this
        .clip(shape)
        .background(palette.surface.copy(alpha = if (palette.isDark) 0.94f else 0.97f), shape)
        .border(1.dp, palette.hairline, shape)
        .shadow(elevation = 24.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.4f))

/** 1.0 → 1.1 over 200 ms with an ease-out curve, exactly as specified for focus. */
@Composable
fun focusScale(focused: Boolean, base: Float = 1f): Float {
    val target = if (focused) base * Motion.focusScale else base
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = Motion.focusLiftMs, easing = FastOutSlowInEasing),
        label = "focusScale",
    )
    return animated
}

/** A shimmering block, shown while artwork loads. No spinner: it never jumps. */
@Composable
fun Shimmer(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(CouchShapes.card)) {
    val palette = LocalPalette.current
    val base = if (palette.isDark) Color(0xFF1C1F26) else Color(0xFFE7E0D3)
    val highlight = if (palette.isDark) Color(0xFF262A33) else Color(0xFFF3EDE2)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset.Zero,
                    end = Offset(600f, 600f),
                )
            )
    )
}

/**
 * An app on the home screen: a 5:3 tile like the Mac's dock, drawn from the app's own icon colour,
 * with the name underneath (on focus, or always on touch devices).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    entry: AppEntry,
    focused: Boolean,
    showLabel: Boolean,
    pinned: Boolean = false,
    icon: android.graphics.drawable.Drawable?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val scale = focusScale(focused)
    val shape = RoundedCornerShape(tvDp(22f))
    val accent = Color(entry.accent)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .width(tvDp(250f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(5f / 3f)
                .clip(shape)
                // The Mac's TileArt paints the icon's ground across the tile; here it is a gradient
                // of that ground, so the tile reads as the app's colour family even at a glance.
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.55f).compositeOver(palette.surface),
                            accent.copy(alpha = 0.22f).compositeOver(palette.surface),
                        )
                    )
                )
                .border(
                    width = if (focused) tvDp(3f) else tvDp(2f),
                    color = if (focused) palette.focusPlatter.copy(alpha = 0.9f) else palette.hairline,
                    shape = shape,
                )
        ) {
            if (icon != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(icon).crossfade(true).build(),
                    contentDescription = entry.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(0.46f),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = entry.label.take(1).uppercase(),
                        style = CouchType.style(CouchType.headline, FontWeight.Bold, palette.text),
                    )
                }
            }

            // A soft glare across the top of a focused tile: tvOS light, not a carnival.
            if (focused) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(tvDp(60f))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)
                            )
                        )
                )
            }

            if (pinned) {
                Text(
                    text = "★",
                    color = palette.brass,
                    style = CouchType.style(CouchType.caption1, FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(tvDp(10f)),
                )
            }

            if (entry.compat.needsRemoteWarning()) {
                CompatBadge(entry.compat, Modifier.align(Alignment.BottomStart).padding(tvDp(8f)))
            }
        }

        if (showLabel) {
            Spacer(Modifier.height(tvDp(10f)))
            Text(
                text = entry.label,
                style = CouchType.style(CouchType.caption1, FontWeight.Medium, palette.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = tvDp(6f)),
            )
        }
    }
}

/** The small warning that an app was built for touch, not for a remote. */
@Composable
fun CompatBadge(compat: TvCompat, modifier: Modifier = Modifier) {
    if (compat == TvCompat.TV_OPTIMISED) return
    val palette = LocalPalette.current
    val (background, label) = when (compat) {
        TvCompat.TOUCH_ONLY -> palette.warning.copy(alpha = 0.92f) to "!"
        TvCompat.UNKNOWN -> palette.glassStrong to "?"
        TvCompat.TV_OPTIMISED -> return
    }
    Box(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .padding(horizontal = tvDp(12f), vertical = tvDp(2f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CouchType.style(CouchType.caption2, FontWeight.Bold, Color.Black.copy(alpha = 0.8f)),
        )
    }
}

/**
 * A content card for streaming rows: 2:3 poster, a progress line for Continue Watching, and the
 * episode or year underneath when focused.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaPoster(
    item: MediaItem,
    focused: Boolean,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val scale = focusScale(focused)
    val shape = RoundedCornerShape(tvDp(14f))
    val accent = Color(item.accent ?: 0xFF3A3F4A.toInt())

    Column(
        modifier = modifier
            .scale(scale)
            .width(tvDp(200f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.65f).compositeOver(palette.surface),
                            accent.copy(alpha = 0.18f).compositeOver(palette.canvas),
                        )
                    )
                )
                .border(
                    width = if (focused) tvDp(3f) else tvDp(2f),
                    color = if (focused) palette.focusPlatter.copy(alpha = 0.9f) else palette.hairline,
                    shape = shape,
                )
        ) {
            if (item.posterUrl != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    loading = { Shimmer(Modifier.fillMaxSize(), shape) },
                    error = { PosterFallback(item, Modifier.fillMaxSize()) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PosterFallback(item, Modifier.fillMaxSize())
            }

            // Episode/time label sits on the artwork, like Apple TV's Continue Watching.
            if (item.subtitle != null) {
                Text(
                    text = item.subtitle,
                    style = CouchType.style(CouchType.caption2, FontWeight.Medium, Color.White.copy(alpha = 0.9f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(tvDp(10f))
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(tvDp(8f)))
                        .padding(horizontal = tvDp(8f), vertical = tvDp(2f)),
                )
            }

            if (item.progress != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(tvDp(8f))
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(item.progress.coerceIn(0f, 1f))
                            .fillMaxSize()
                            .background(palette.accent)
                    )
                }
            }
        }

        Text(
            text = item.title,
            style = CouchType.style(
                CouchType.caption1,
                FontWeight.Medium,
                if (focused) palette.text else palette.textDim,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = tvDp(10f), start = tvDp(4f), end = tvDp(4f)),
        )
    }
}

/** Poster art we could not fetch: the title over its own colour, never an empty hole. */
@Composable
private fun PosterFallback(item: MediaItem, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(modifier.padding(tvDp(16f)), contentAlignment = Alignment.Center) {
        Text(
            text = item.title,
            style = CouchType.style(
                CouchType.caption1,
                FontWeight.Bold,
                if (palette.isDark) Color.White.copy(alpha = 0.85f) else palette.text,
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The title above a row. 31 pt on the Mac, so 15.5 sp at reference density. */
@Composable
fun RowHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val palette = LocalPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = tvDp(18f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = CouchType.style(CouchType.callout, FontWeight.Bold, palette.text))
        Spacer(Modifier.width(tvDp(16f)))
        if (trailing != null) trailing()
    }
}

/**
 * A control on the fridge-door glass: translucent at rest, a solid platter with dark text when
 * focused. That inversion is the Mac app's signature (`focusPlatter`) and reads at ten feet.
 */
@Composable
fun PillButton(
    label: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(CouchShapes.pill)
    val scale = focusScale(focused, base = 1f)
    Row(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(
                when {
                    focused -> palette.focusPlatter
                    prominent -> palette.accent.copy(alpha = 0.18f)
                    else -> palette.glass
                }
            )
            .border(1.dp, if (focused) Color.Transparent else palette.hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = tvDp(44f), vertical = tvDp(22f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tvDp(14f)),
    ) {
        leading?.invoke()
        Text(
            text = label,
            style = CouchType.style(
                CouchType.caption1,
                FontWeight.Bold,
                if (focused) palette.focusText else palette.text,
            ),
        )
    }
}

/** A tiny label chip: network name, "offline", a new-app count. */
@Composable
fun MetaChip(text: String, modifier: Modifier = Modifier, accent: Color? = null) {
    val palette = LocalPalette.current
    Text(
        text = text,
        style = CouchType.style(CouchType.caption2, FontWeight.Medium, accent ?: palette.textDim),
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(CouchShapes.pill))
            .background(accent?.copy(alpha = 0.16f) ?: palette.glass)
            .border(1.dp, palette.hairline, RoundedCornerShape(CouchShapes.pill))
            .padding(horizontal = tvDp(24f), vertical = tvDp(10f)),
    )
}

/** Reads better on bright artwork; used under the header clock. */
fun Color.onDark(): Color = if (luminance() > 0.5f) Color.Black else Color.White
