package dev.couchtv.remote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The remote's palette and type, taken from the same tokens as the launcher and the Mac app: the
 * charcoal canvas, the phosphor accent, the white-on-dark type. Dark is the only mode — this app is
 * used in the dark, and a white remote at night is its own bug.
 */
object Couch {
    val Canvas = Color(0xFF07080B)
    val Panel = Color(0xFF0F1116)
    val Raised = Color(0xFF161920)
    val Ink = Color(0xFFF5F5F7)
    val Dim = Color(0xFF9EA3AF)
    val Faint = Color(0xFF6E7482)
    val Phosphor = Color(0xFF3DDC84)
    val Warning = Color(0xFFFF453A)
    val Hairline = Color(0x1FFFFFFF)
    val Glass = Color(0x0DFFFFFF)

    fun title(size: Int = 22) = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = size.sp,
        letterSpacing = (-0.5).sp,
        color = Ink,
    )

    val Label = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        color = Faint,
    )

    val Mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = Faint,
    )
}

@Composable
fun CouchTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Couch.Phosphor,
        onPrimary = Color.Black,
        background = Couch.Canvas,
        onBackground = Couch.Ink,
        surface = Couch.Panel,
        onSurface = Couch.Ink,
        surfaceVariant = Couch.Raised,
        onSurfaceVariant = Couch.Dim,
        error = Couch.Warning,
    )

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(bodyLarge = Couch.title(15), labelSmall = Couch.Label),
        content = content,
    )
}
