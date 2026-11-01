package dev.couchtv.remote.net

import kotlin.math.abs

/**
 * Turns finger travel in pixels into the fractional deltas the TV integrates.
 *
 * Same arithmetic as the iOS remote (`remote-ios/RemoteKit/Sources/RemoteKit/PointerBallistics.swift`),
 * so a gesture feels identical on either phone:
 *
 * * gain 1.15 — a full-width swipe moves the cursor about 1.15 screens;
 * * light exponential smoothing — kills the jitter of a thumb resting on glass without visible lag;
 * * a dead zone — sub-pixel noise is never sent, which keeps the socket quiet while the thumb is still.
 *
 * Pure Kotlin, no Android types: it is unit-tested on the JVM (`PointerGainTest`).
 */
class PointerGain(
    private val gain: Double = 1.15,
    private var smoothing: Double = 0.55,
    private val deadZone: Double = 0.6,
    sensitivity: Float = 1.0f,
) {
    var sensitivity: Double = sensitivity.toDouble().coerceIn(0.4, 2.5)
        set(value) { field = value.coerceIn(0.4, 2.5) }

    private var residualX = 0.0
    private var residualY = 0.0
    private var smoothedX = 0.0
    private var smoothedY = 0.0

    /** Returns a screen-fraction delta, or null when the movement was only noise. */
    fun push(dxPoints: Double, dyPoints: Double, widthPx: Double, heightPx: Double): Pair<Float, Float>? {
        if (widthPx <= 0 || heightPx <= 0) return null
        if (!dxPoints.isFinite() || !dyPoints.isFinite()) return null

        smoothedX = smoothing * dxPoints + (1 - smoothing) * smoothedX
        smoothedY = smoothing * dyPoints + (1 - smoothing) * smoothedY
        residualX += smoothedX
        residualY += smoothedY

        if (abs(residualX) < deadZone && abs(residualY) < deadZone) return null

        val dx = residualX / widthPx * gain * sensitivity
        val dy = residualY / heightPx * gain * sensitivity
        release()
        return dx.coerceIn(-1.0, 1.0).toFloat() to dy.coerceIn(-1.0, 1.0).toFloat()
    }

    /** Drops residual movement when the finger lifts, so it cannot bleed into the next gesture. */
    fun release() {
        residualX = 0.0
        residualY = 0.0
        smoothedX = 0.0
        smoothedY = 0.0
    }

    companion object {
        /** Two-finger travel becomes scroll, in pixel-equivalents, positive meaning "down". */
        fun scrollDelta(dyPoints: Double): Float = (dyPoints * 3).coerceIn(-4000.0, 4000.0).toFloat()
    }
}
