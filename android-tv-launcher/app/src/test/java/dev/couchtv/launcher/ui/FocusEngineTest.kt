package dev.couchtv.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Focus is the difference between a launcher that feels like a TV and one that feels broken, so the
 * arithmetic is tested more thoroughly than anything else in the app.
 */
class FocusEngineTest {

    private val sizes = listOf(3, 0, 6, 2)

    @Test
    fun `left and right clamp at the row's ends`() {
        assertEquals(FocusPos(0, 1), FocusEngine.move(FocusPos(0, 2), Direction.LEFT, sizes))
        assertEquals(FocusPos(0, 2), FocusEngine.move(FocusPos(0, 2), Direction.RIGHT, sizes))
        assertEquals(FocusPos(0, 0), FocusEngine.move(FocusPos(0, 0), Direction.LEFT, sizes))
    }

    @Test
    fun `left and right wrap only when asked`() {
        assertEquals(FocusPos(0, 2), FocusEngine.move(FocusPos(0, 0), Direction.LEFT, sizes, wrap = true))
        assertEquals(FocusPos(0, 0), FocusEngine.move(FocusPos(0, 2), Direction.RIGHT, sizes, wrap = true))
    }

    @Test
    fun `down skips empty rows and keeps the column where it can`() {
        assertEquals(FocusPos(2, 2), FocusEngine.move(FocusPos(0, 2), Direction.DOWN, sizes))
        assertEquals(FocusPos(0, 2), FocusEngine.move(FocusPos(2, 2), Direction.UP, sizes))
    }

    @Test
    fun `the column is clamped when the next row is shorter`() {
        assertEquals(FocusPos(3, 1), FocusEngine.move(FocusPos(2, 5), Direction.DOWN, sizes))
    }

    @Test
    fun `up and down stop at the edges`() {
        assertEquals(FocusPos(0, 0), FocusEngine.move(FocusPos(0, 0), Direction.UP, sizes))
        assertEquals(FocusPos(3, 0), FocusEngine.move(FocusPos(3, 0), Direction.DOWN, sizes))
    }

    @Test
    fun `a shrunken list cannot leave focus past the end`() {
        assertEquals(FocusPos(0, 1), FocusEngine.clamp(FocusPos(9, 9), listOf(2)))
        assertEquals(FocusPos(2, 0), FocusEngine.clamp(FocusPos(1, 4), listOf(4, 0, 3)))
        assertEquals(FocusPos(0, 0), FocusEngine.clamp(FocusPos(0, 0), emptyList()))
    }

    @Test
    fun `the scroll window keeps the focused item visible without running past the end`() {
        assertEquals(0, FocusEngine.windowFirst(0, 6, 20))
        assertEquals(0, FocusEngine.windowFirst(2, 6, 20))
        assertEquals(14, FocusEngine.windowFirst(19, 6, 20))
        assertEquals(0, FocusEngine.windowFirst(1, 6, 4))
    }
}
