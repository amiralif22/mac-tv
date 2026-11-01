package dev.couchtv.launcher.ui

/** D-pad directions, in the order a remote sends them. */
enum class Direction { UP, DOWN, LEFT, RIGHT }

/**
 * A place in a stack of rows: which row, and which item within it.
 * Row 0 is the status row (search, quick settings, settings), then content rows below it.
 */
data class FocusPos(val row: Int, val col: Int = 0)

/**
 * The launcher's focus arithmetic, as pure functions.
 *
 * Compose's built-in focus traversal is fine for forms, but a TV launcher needs exact, predictable
 * behaviour that survives rows appearing, disappearing and changing length under the cursor — and it
 * needs to be testable without a device. So focus is a value here, and every screen renders from it:
 *
 * - Left and right clamp at the row's ends (tvOS behaviour; the row scrolls instead of wrapping).
 * - Up and down skip empty rows and keep the column where possible, clamped to the target's length,
 *   so leaving a six-item row for a three-item row lands on the third item rather than nothing.
 * - Sizes can change at any time (a streaming row finishing loading); [clamp] is applied after every
 *   update, so focus can never point past the end.
 */
object FocusEngine {

    fun move(pos: FocusPos, direction: Direction, sizes: List<Int>, wrap: Boolean = false): FocusPos {
        if (sizes.isEmpty()) return FocusPos(0, 0)
        val start = clamp(pos, sizes)
        return when (direction) {
            Direction.LEFT -> {
                val size = sizes[start.row]
                when {
                    size <= 0 -> start
                    start.col > 0 -> start.copy(col = start.col - 1)
                    wrap -> start.copy(col = size - 1)
                    else -> start
                }
            }

            Direction.RIGHT -> {
                val size = sizes[start.row]
                when {
                    size <= 0 -> start
                    start.col < size - 1 -> start.copy(col = start.col + 1)
                    wrap -> start.copy(col = 0)
                    else -> start
                }
            }

            Direction.UP -> vertical(start, sizes, -1)
            Direction.DOWN -> vertical(start, sizes, +1)
        }
    }

    private fun vertical(from: FocusPos, sizes: List<Int>, delta: Int): FocusPos {
        var row = from.row + delta
        while (row in sizes.indices) {
            val size = sizes[row]
            if (size > 0) return FocusPos(row, from.col.coerceAtMost(size - 1))
            row += delta
        }
        return from
    }

    /** Brings a position back inside the current content, keeping the row when it still exists. */
    fun clamp(pos: FocusPos, sizes: List<Int>): FocusPos {
        if (sizes.isEmpty()) return FocusPos(0, 0)
        val row = pos.row.coerceIn(0, sizes.lastIndex)
        if (sizes[row] == 0) {
            val nearest = nearestNonEmpty(row, sizes)
            return FocusPos(nearest, 0)
        }
        return FocusPos(row, pos.col.coerceIn(0, sizes[row] - 1))
    }

    private fun nearestNonEmpty(row: Int, sizes: List<Int>): Int {
        for (distance in 1..sizes.size) {
            val below = row + distance
            if (below in sizes.indices && sizes[below] > 0) return below
            val above = row - distance
            if (above in sizes.indices && sizes[above] > 0) return above
        }
        return row
    }

    /**
     * The first item a row should show so that [col] is visible, given how many fit on screen.
     * Keeps the focused item near the left edge once the row starts scrolling, and never past the
     * end: the row stops with its last item flush right, as tvOS does.
     */
    fun windowFirst(col: Int, visible: Int, total: Int): Int {
        if (total <= 0) return 0
        if (visible <= 0) return col.coerceIn(0, total - 1)
        if (total <= visible) return 0
        return (col - visible / 3).coerceIn(0, total - visible)
    }
}
