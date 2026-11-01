package dev.couchtv.launcher.ui.screens

import android.view.KeyEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.couchtv.launcher.ui.LauncherViewModel
import dev.couchtv.launcher.ui.UiKeys
import dev.couchtv.launcher.ui.components.PillButton
import dev.couchtv.launcher.ui.components.glass
import dev.couchtv.launcher.ui.components.panel
import dev.couchtv.launcher.ui.theme.tvDp
import dev.couchtv.launcher.ui.theme.CouchShapes
import dev.couchtv.launcher.ui.theme.CouchType
import dev.couchtv.launcher.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.couchtv.launcher.R
import androidx.compose.ui.res.stringResource

/**
 * The quick settings panel: the few things people change with the TV on.
 *
 * Glass over the home screen, opened by long-pressing Home (or the remote's Menu), dismissed by Back
 * or by choosing Close. Every tile is reachable with the D-pad; left and right move between them.
 */
@Composable
fun QuickSettingsOverlay(vm: LauncherViewModel, state: LauncherViewModel.UiState, onClose: () -> Unit) {
    val palette = LocalPalette.current
    var focus by remember { mutableIntStateOf(0) }
    var sleepMinutes by remember { mutableStateOf<Int?>(null) }

    data class Tile(val label: String, val value: () -> String, val action: () -> Unit)

    val tiles = listOf(
        Tile(stringResource(R.string.settings_network), { state.network.label }) { vm.refreshNetwork() },
        Tile(stringResource(R.string.quick_volume), { "${(vm.inputRouter.volumeFraction() * 100).toInt()}%" }) { vm.inputRouter.adjustVolume(1) },
        Tile(stringResource(R.string.quick_sleep_timer), { sleepMinutes?.let { "$it min" } ?: "Off" }) {
            sleepMinutes = when (sleepMinutes) {
                null -> 30
                30 -> 60
                60 -> 90
                else -> null
            }
        },
        Tile(stringResource(R.string.quick_pointer), { if (state.pointerMode) "On" else "Off" }) { vm.togglePointerMode() },
        Tile(stringResource(R.string.quick_screen_off), { "" }) { vm.inputRouter.sleepDevice() },
        Tile(stringResource(R.string.close), { "" }, onClose),
    )

    DisposableEffect(Unit) {
        UiKeys.handler = { event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                false
            } else when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { focus = (focus - 1).coerceAtLeast(0); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { focus = (focus + 1).coerceAtMost(tiles.lastIndex); true }
                KeyEvent.KEYCODE_DPAD_UP -> { focus = (focus - 1).coerceAtLeast(0); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { focus = (focus + 1).coerceAtMost(tiles.lastIndex); true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { tiles[focus].action(); true }
                else -> false
            }
        }
        onDispose { UiKeys.handler = null }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Column(
            Modifier
                .padding(top = tvDp(150f), end = tvDp(96f))
                .glass(palette, RoundedCornerShape(CouchShapes.panel), strong = true)
                .padding(tvDp(28f)),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                stringResource(R.string.quick_settings),
                style = CouchType.style(CouchType.caption2, FontWeight.Bold, palette.textFaint),
            )
            Spacer(Modifier.height(tvDp(16f)))
            tiles.forEachIndexed { index, tile ->
                Row(
                    Modifier
                        .padding(vertical = tvDp(6f))
                        .width(tvDp(760f))
                        .clip(RoundedCornerShape(CouchShapes.pill))
                        .background(if (focus == index) palette.focusPlatter else Color.Transparent)
                        .padding(horizontal = tvDp(24f), vertical = tvDp(14f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = tile.label,
                        style = CouchType.style(
                            CouchType.caption1,
                            FontWeight.Medium,
                            if (focus == index) palette.focusText else palette.text,
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = tile.value(),
                        style = CouchType.style(
                            CouchType.caption1,
                            FontWeight.Bold,
                            if (focus == index) palette.focusText else palette.accent,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(tvDp(12f)))
            Text(
                text = stringResource(R.string.quick_settings_hint),
                style = CouchType.style(CouchType.caption2, FontWeight.Normal, palette.textFaint),
            )
        }
    }
}

/**
 * The screen saver: slowly drifting colour and light, with the clock the Mac app shows.
 * Any input dismisses it — the view model clears it on the first key, tap or pointer move.
 */
@Composable
fun ScreensaverOverlay(state: LauncherViewModel.UiState) {
    val palette = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "saver")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(48_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = Date()
        }
    }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now)

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        palette.accentDeep.copy(alpha = 0.55f),
                        palette.brass.copy(alpha = 0.18f),
                        palette.canvas,
                    ),
                    center = Offset(280f + 1500f * drift, 220f + 420f * drift),
                    radius = 1900f,
                )
            )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        radius = 2200f,
                    )
                )
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = tvDp(120f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.settings.saverClock) {
                Text(
                    text = date,
                    style = CouchType.style(CouchType.callout, FontWeight.Medium, Color.White.copy(alpha = 0.8f)),
                )
                Text(
                    text = time,
                    style = CouchType.style(CouchType.title1 * 2f, FontWeight.Bold, Color.White.copy(alpha = 0.92f)),
                )
            }
        }
        Text(
            text = stringResource(R.string.saver_hint),
            style = CouchType.style(CouchType.caption2, FontWeight.Medium, Color.White.copy(alpha = 0.35f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = tvDp(60f)),
        )
    }
}

/** A single decision, asked plainly: a title, a sentence, and two buttons. */
@Composable
fun PromptPanel(
    title: String,
    body: String,
    primary: String,
    secondary: String?,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)?,
) {
    val palette = LocalPalette.current
    var focus by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        UiKeys.handler = { event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                false
            } else when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { focus = 0; true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { focus = if (secondary != null) 1 else 0; true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (focus == 1 && onSecondary != null) onSecondary() else onPrimary()
                    true
                }
                else -> false
            }
        }
        onDispose { UiKeys.handler = null }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(tvDp(1400f))
                .panel(palette, RoundedCornerShape(CouchShapes.panel))
                .padding(tvDp(44f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = CouchType.style(CouchType.title3, FontWeight.Bold, palette.text),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(tvDp(14f)))
            Text(
                text = body,
                style = CouchType.style(CouchType.caption1, FontWeight.Normal, palette.textDim),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(tvDp(28f)))
            Row(horizontalArrangement = Arrangement.spacedBy(tvDp(18f))) {
                PillButton(label = primary, focused = focus == 0, prominent = true, onClick = onPrimary)
                if (secondary != null && onSecondary != null) {
                    PillButton(label = secondary, focused = focus == 1, onClick = onSecondary)
                }
            }
        }
    }
}

/** A quiet glass pill for confirmations; it removes itself. */
@Composable
fun ToastOverlay(text: String, onDismissed: () -> Unit) {
    val palette = LocalPalette.current
    LaunchedEffect(text) {
        delay(2_400)
        onDismissed()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = text,
            style = CouchType.style(CouchType.caption1, FontWeight.Medium, palette.text),
            modifier = Modifier
                .padding(bottom = tvDp(64f))
                .glass(palette, RoundedCornerShape(CouchShapes.pill), strong = true)
                .border(1.dp, palette.hairline, RoundedCornerShape(CouchShapes.pill))
                .padding(horizontal = tvDp(40f), vertical = tvDp(18f)),
        )
    }
}
