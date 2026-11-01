package dev.couchtv.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.couchtv.remote.R
import dev.couchtv.remote.net.PointerGain

/**
 * The LG-style touchpad: drag to move the cursor on the TV, tap to click, two fingers to scroll.
 *
 * Three things make it feel like hardware rather than a web page:
 *
 * * movement is converted to screen fractions by [PointerGain] before it leaves the phone, so the
 *   cursor travels the same distance on any TV;
 * * every event is sent immediately — there is no frame buffer to wait for, and the TV integrates
 *   the deltas itself;
 * * a tap is a down-up with no travel, measured in pixels, not a `detectTapGestures` waiting on a
 *   long-press timeout that would delay every click by 300 ms.
 */
@Composable
fun Touchpad(
    sensitivity: Float,
    onPointer: (Float, Float) -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onScroll: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gain = remember { PointerGain(sensitivity = sensitivity) }
    gain.sensitivity = sensitivity.toDouble()
    var touching by remember { mutableStateOf(false) }
    var lastPosition by remember { mutableStateOf<Offset?>(null) }
    var travel by remember { mutableStateOf(0f) }
    var downAt by remember { mutableStateOf(0L) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = if (touching) 0.09f else 0.045f))
            .border(1.dp, Couch.Hairline, RoundedCornerShape(22.dp))
            .pointerInput(Unit) {
                trackPad(
                    onStart = { touching = true; travel = 0f; lastPosition = null; downAt = System.currentTimeMillis() },
                    onMove = { position, size, isMultiTouch ->
                        val previous = lastPosition
                        lastPosition = position
                        if (isMultiTouch) {
                            // Two fingers: scroll, positive is down.
                            val dy = position.y - (previous ?: position).y
                            if (kotlin.math.abs(dy) > 4f) onScroll(PointerGain.scrollDelta(dy.toDouble()))
                            return@trackPad
                        }
                        if (previous == null) return@trackPad
                        val dx = position.x - previous.x
                        val dy = position.y - previous.y
                        travel += kotlin.math.abs(dx) + kotlin.math.abs(dy)
                        gain.push(dx.toDouble(), dy.toDouble(), size.width.toDouble(), size.height.toDouble())
                            ?.let { (fx, fy) -> onPointer(fx, fy) }
                    },
                    onEnd = {
                        touching = false
                        gain.release()
                        val held = System.currentTimeMillis() - downAt
                        when {
                            travel < 12f && held >= 480 -> onLongPress()
                            travel < 12f -> onClick()
                        }
                        lastPosition = null
                        travel = 0f
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = Icons.Filled.Mouse,
                contentDescription = null,
                tint = if (touching) Couch.Phosphor else Couch.Faint,
            )
            Text(
                text = stringResource(if (touching) R.string.pointing else R.string.drag_to_point),
                style = Couch.Label,
                color = if (touching) Couch.Phosphor else Couch.Faint,
            )
        }
    }
}

/**
 * Raw pointer handling, because the high-level gestures either delay a tap (tap timeout) or hide the
 * finger count. Returns on the first pointer's lift, which is what a touchpad does.
 */
private suspend fun PointerInputScope.trackPad(
    onStart: () -> Unit,
    onMove: (position: Offset, size: androidx.compose.ui.unit.IntSize, isMultiTouch: Boolean) -> Unit,
    onEnd: () -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    onStart()
    onMove(down.position, size, false)

    do {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        val pointer = pressed.firstOrNull() ?: break
        val moved = pointer.positionChange()
        if (moved != Offset.Zero || pressed.size > 1) {
            onMove(pointer.position, size, pressed.size > 1)
        }
    } while (pressed.any { it.pressed })

    onEnd()
}
