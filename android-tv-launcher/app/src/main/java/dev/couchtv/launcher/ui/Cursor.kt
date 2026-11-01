package dev.couchtv.launcher.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas

/**
 * The LG-style pointer: a registry of everything on screen that can be clicked, plus the cursor's
 * position.
 *
 * A real hit-test would need access to Compose's layout internals; instead each interactive card
 * registers its bounds and its actions while pointer mode is on. The cursor then knows exactly what
 * is underneath it, can move focus there (so the TV still looks like a TV — the focused card lifts as
 * the cursor passes over it), and can activate it on tap. Registration costs one layout callback per
 * card and only exists while the phone is in touchpad mode.
 */
class CursorRegistry {
    data class Target(
        val id: String,
        val bounds: Rect,
        val onFocus: () -> Unit,
        val onActivate: () -> Unit,
        val onLongPress: (() -> Unit)? = null,
    )

    private val targets = LinkedHashMap<String, Target>()

    fun register(target: Target) {
        targets[target.id] = target
    }

    fun unregister(id: String) {
        targets.remove(id)
    }

    fun clear() = targets.clear()

    /** The smallest card under the point: nested cards win over the row behind them. */
    fun targetAt(point: Offset): Target? = targets.values
        .filter { it.bounds.contains(point) }
        .minByOrNull { it.bounds.width * it.bounds.height }

    fun isEmpty(): Boolean = targets.isEmpty()
}

val LocalCursorRegistry = compositionLocalOf<CursorRegistry?> { null }

/** True while the phone remote is in touchpad mode; cards register themselves only then. */
val LocalPointerMode = compositionLocalOf { false }

/**
 * Registers this composable as a cursor target. No-op unless pointer mode is on, so the D-pad and
 * touch paths pay nothing for it.
 */
@Composable
fun Modifier.cursorTarget(
    id: String,
    onFocus: () -> Unit,
    onActivate: () -> Unit,
    onLongPress: (() -> Unit)? = null,
): Modifier {
    val registry = LocalCursorRegistry.current ?: return this
    val pointerMode = LocalPointerMode.current
    if (!pointerMode) return this
    return this.onGloballyPositioned { coordinates ->
        registry.register(
            CursorRegistry.Target(
                id = id,
                bounds = coordinates.boundsInRoot(),
                onFocus = onFocus,
                onActivate = onActivate,
                onLongPress = onLongPress,
            )
        )
    }
}

/** The visible pointer: a soft white ring with a dot, 28 dp across, like LG's magic remote. */
@Composable
fun CursorPointer(position: Offset, hovering: Boolean, visible: Boolean) {
    if (!visible) return
    Canvas(Modifier.size(28.dp, 28.dp)) {
        val radius = size.minDimension / 2f
        drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = radius, center = center + Offset(0f, 2f))
        drawCircle(
            color = Color.White.copy(alpha = if (hovering) 0.95f else 0.85f),
            radius = radius * if (hovering) 0.95f else 0.75f,
            center = center,
        )
        drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = radius * 0.28f, center = center)
    }
}
