package dev.couchtv.remote.net

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The touchpad's feel is arithmetic, so it can be tested: a sweep must move a predictable distance,
 * noise must move nothing, and nothing may escape the screen.
 */
class PointerGainTest {

    @Test
    fun `a full-width sweep crosses about one screen`() {
        val gain = PointerGain(gain = 1.15, smoothing = 1.0, deadZone = 0.0, sensitivity = 1.0f)
        var total = 0f
        repeat(35) {
            val delta = gain.push(10.0, 0.0, widthPx = 350.0, heightPx = 500.0)
            total += delta?.first ?: 0f
        }
        assertEquals(1.15f, total, 0.02f)
    }

    @Test
    fun `jitter is smoothed away, slow travel accumulates`() {
        // A thumb resting on glass wobbles; the filter keeps that well under the dead zone.
        val jitter = PointerGain(smoothing = 0.55, deadZone = 0.6, sensitivity = 1.0f)
        repeat(40) { index ->
            val direction = if (index % 2 == 0) 0.5 else -0.5
            assertNull(jitter.push(direction, 0.0, 350.0, 500.0))
        }

        // A deliberate slow drag is sub-dead-zone per frame, but is never lost: it accumulates.
        val slow = PointerGain(smoothing = 1.0, deadZone = 0.6, sensitivity = 1.0f)
        val deltas = (1..10).mapNotNull { slow.push(0.1, 0.0, 350.0, 500.0) }
        assertEquals(1, deltas.size)
    }

    @Test
    fun `deltas stay inside the unit square`() {
        val gain = PointerGain(smoothing = 1.0, deadZone = 0.0, sensitivity = 2.5f)
        listOf(4000.0 to -4000.0, -4000.0 to 4000.0).forEach { (dx, dy) ->
            val delta = gain.push(dx, dy, 350.0, 500.0)
            assertNotNull(delta)
            assertTrue(abs(delta!!.first) <= 1f)
            assertTrue(abs(delta.second) <= 1f)
        }
    }

    @Test
    fun `release drops residual movement`() {
        val gain = PointerGain(smoothing = 1.0, deadZone = 5.0)
        assertNull(gain.push(4.0, 4.0, 350.0, 500.0))
        gain.release()
        assertNull(gain.push(4.0, 4.0, 350.0, 500.0))
    }

    @Test
    fun `sensitivity is clamped to the protocol range`() {
        assertEquals(2.5, PointerGain(sensitivity = 9f).sensitivity, 0.0001)
        assertEquals(0.4, PointerGain(sensitivity = 0f).sensitivity, 0.0001)
    }

    @Test
    fun `zero sized pad and infinite values are ignored`() {
        val gain = PointerGain()
        assertNull(gain.push(10.0, 10.0, 0.0, 0.0))
        assertNull(gain.push(Double.NaN, 1.0, 350.0, 500.0))
    }

    @Test
    fun `scroll is bounded both ways`() {
        assertEquals(30f, PointerGain.scrollDelta(10.0), 0.0001f)
        assertEquals(4000f, PointerGain.scrollDelta(1_000_000.0), 0.0001f)
        assertEquals(-4000f, PointerGain.scrollDelta(-1_000_000.0), 0.0001f)
    }
}
