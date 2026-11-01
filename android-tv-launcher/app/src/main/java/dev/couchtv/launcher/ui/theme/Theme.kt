package dev.couchtv.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.couchtv.launcher.data.ThemeMode
import dev.couchtv.launcher.util.DeviceProfile

/**
 * Couch TV's design tokens, translated from the Mac app so the two feel like one family.
 *
 * Sources of truth on the Mac side: `Typography.swift` (`TVText`), `Views.swift` (`Wallpaper`,
 * `Artwork`, `tvGlass`, `focusPlatter`, `Motion`). Colours are here; type is in [CouchType]; motion
 * durations in [Motion].
 *
 * Everything is expressed in tvOS points and divided by two, because a 1080p Android TV reports a
 * 960 dp canvas where macOS reports 1920 pt. [DeviceProfile.scale] finishes the job on smaller
 * screens, so one number works at every size.
 */
@Immutable
data class CouchPalette(
    val isDark: Boolean,
    val canvas: Color,
    val canvasTop: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val glass: Color,
    val glassStrong: Color,
    val hairline: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val accent: Color,
    val accentDeep: Color,
    val brass: Color,
    val danger: Color,
    val warning: Color,
    val focusPlatter: Color,
    val focusText: Color,
)

/** Dark first, like the Mac app: a TV in a dark room is the normal case. */
val DarkPalette = CouchPalette(
    isDark = true,
    // Wallpaper: radial #1B1E27 → #0A0B0E
    canvas = Color(0xFF0A0B0E),
    canvasTop = Color(0xFF1B1E27),
    surface = Color(0xFF111318),
    surfaceRaised = Color(0xFF181B22),
    glass = Color(0x14FFFFFF),
    glassStrong = Color(0x24FFFFFF),
    hairline = Color(0x2EFFFFFF),
    text = Color(0xF5FFFFFF),
    textDim = Color(0x9EFFFFFF),
    textFaint = Color(0x66FFFFFF),
    accent = Color(0xFF3DDC84),
    accentDeep = Color(0xFF13704A),
    brass = Color(0xFFC69A52),
    danger = Color(0xFFFF6B6B),
    warning = Color(0xFFFFC15E),
    focusPlatter = Color(0xFFFFFFFF),
    focusText = Color(0xFF0A0B0E),
)

/** Light mode uses the site's cream and ink, and the phosphor green for the accent. */
val LightPalette = CouchPalette(
    isDark = false,
    canvas = Color(0xFFF6F1E7),
    canvasTop = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF1EADC),
    glass = Color(0x0F1D1A17),
    glassStrong = Color(0x1A1D1A17),
    hairline = Color(0x241D1A17),
    text = Color(0xFF1D1A17),
    textDim = Color(0xB35B5247),
    textFaint = Color(0x7A5B5247),
    accent = Color(0xFF1F7A4A),
    accentDeep = Color(0xFF145434),
    brass = Color(0xFF7E5E2E),
    danger = Color(0xFFB3261E),
    warning = Color(0xFF8A5A00),
    focusPlatter = Color(0xFF1D1A17),
    focusText = Color(0xFFF6F1E7),
)

/** tvOS's type scale, in points (Mac) — halved for Android's 960 dp canvas at the call site. */
object CouchType {
    const val title1 = 76f
    const val title2 = 57f
    const val title3 = 48f
    const val headline = 38f
    const val callout = 31f
    const val body = 29f
    const val caption1 = 25f
    const val caption2 = 23f

    /** The same sizes as Android TV pixels: 23 pt → 11.5 sp at reference density. */
    @Composable
    fun size(points: Float): androidx.compose.ui.unit.TextUnit =
        (points / 2f * LocalDeviceProfile.current.scale).sp

    @Composable
    fun style(
        points: Float,
        weight: FontWeight = FontWeight.Medium,
        color: Color = LocalPalette.current.text,
    ): TextStyle = TextStyle(
        color = color,
        fontSize = size(points),
        fontWeight = weight,
        lineHeight = size(points) * 1.2f,
    )
}

/** Motion on the Mac is `.smooth(duration:)`; Android's equivalent curve is the standard easing. */
object Motion {
    /** Focus moving between tiles: 0.32 s on the Mac, 200 ms for the 1.0 → 1.1 lift. */
    const val focusLiftMs = 200
    const val navigateMs = 320
    const val appearMs = 350
    const val pressMs = 120

    /** The Apple TV lift: 1.0 → 1.1, no bounce. */
    const val focusScale = 1.1f
}

/** Shape scale. The Mac's 22 pt tile radius becomes 11 dp, and so on, consistently. */
object CouchShapes {
    val tile: Dp = 11.dp
    val card: Dp = 8.dp
    val panel: Dp = 22.dp
    val pill: Dp = 999.dp
}

val LocalPalette = staticCompositionLocalOf { DarkPalette }
val LocalDeviceProfile = staticCompositionLocalOf {
    DeviceProfile(
        kind = DeviceProfile.Kind.LEANBACK,
        canvasWidthDp = DeviceProfile.REFERENCE_WIDTH_DP,
        canvasHeightDp = 540f,
        hasDpad = true,
        isTouchPrimary = false,
    )
}

/** A tvOS-style backdrop: the app's colours across the screen, dimmed at the edges. */
@Composable
fun backdropBrush(palette: CouchPalette = LocalPalette.current): Brush = Brush.radialGradient(
    colors = listOf(palette.canvasTop, palette.canvas),
    radius = 1600f,
)

@Composable
fun CouchTheme(
    mode: ThemeMode = ThemeMode.DARK,
    profile: DeviceProfile? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    val device = profile ?: LocalDeviceProfile.current

    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF06210F),
            background = palette.canvas,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = palette.textDim,
            error = palette.danger,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.canvas,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = palette.textDim,
            error = palette.danger,
        )
    }

    CompositionLocalProvider(LocalPalette provides palette, LocalDeviceProfile provides device) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** dp in the app's own units: points from the Mac divided by two, scaled for the screen. */
@Composable
fun tvDp(points: Float): Dp = with(LocalDensity.current) {
    (points / 2f * LocalDeviceProfile.current.scale).dp
}
