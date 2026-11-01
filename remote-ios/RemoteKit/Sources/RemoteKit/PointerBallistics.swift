import Foundation

/// Turns a finger's travel in points into the fractional deltas the TV integrates.
///
/// The touchpad must feel like webOS: a short flick crosses a quarter of the screen, a slow drag is
/// precise, and nothing ever accelerates so hard that a small movement overshoots a tile. So:
///
/// * **Gain** — a full screen-width drag moves the cursor about 1.15 screens, which puts the far
///   corner one comfortable sweep away.
/// * **Smoothing** — a light exponential filter removes the jitter of a thumb resting on glass; the
///   factor is high enough that the lag stays under one frame.
/// * **Dead zone** — sub-point noise is discarded rather than sent, which keeps the TV's socket quiet
///   while the thumb is still.
///
/// All of it is pure arithmetic, so it is unit-tested (`PointerBallisticsTests`) without a device.
/// The touchpad's size in points. A local type rather than `CGSize` so this file — and the tests —
/// build with Foundation alone, on any platform CI runs.
public struct PadSize: Equatable, Sendable {
    public var width: Double
    public var height: Double

    public init(width: Double, height: Double) {
        self.width = width
        self.height = height
    }
}

public struct PointerBallistics: Sendable {
    /// Fraction of the screen travelled per point of finger travel, before sensitivity.
    public var gain: Double
    /// 0…1 smoothing factor: 1 sends raw movement, 0 would freeze the cursor.
    public var smoothing: Double
    /// Travel below this many points is treated as noise.
    public var deadZone: Double
    /// User preference from the settings slider (0.4…2.5 on the TV, mirrored here).
    public var sensitivity: Double

    private var residualX: Double = 0
    private var residualY: Double = 0
    private var smoothedX: Double = 0
    private var smoothedY: Double = 0

    public init(gain: Double = 1.15, smoothing: Double = 0.55, deadZone: Double = 0.6, sensitivity: Double = 1.0) {
        self.gain = gain
        self.smoothing = smoothing.clamped(to: 0.05...1)
        self.deadZone = max(0, deadZone)
        self.sensitivity = sensitivity.clamped(to: 0.4...2.5)
    }

    /// Feeds a finger movement in points and returns a screen-fraction delta, or `nil` when the
    /// movement is only noise.
    ///
    /// `screenSize` is the touchpad's own size in points: normalising by it means the same physical
    /// swipe moves the cursor the same distance on a 5-inch phone and a 13-inch tablet.
    public mutating func push(dx pointsX: Double, dy pointsY: Double, screenSize: PadSize) -> (dx: Double, dy: Double)? {
        guard screenSize.width > 0, screenSize.height > 0 else { return nil }
        guard pointsX.isFinite, pointsY.isFinite else { return nil }

        // The filter runs before the dead zone so tiny movements still inform the average.
        smoothedX = smoothing * pointsX + (1 - smoothing) * smoothedX
        smoothedY = smoothing * pointsY + (1 - smoothing) * smoothedY

        residualX += smoothedX
        residualY += smoothedY

        guard abs(residualX) >= deadZone || abs(residualY) >= deadZone else { return nil }

        let dx = residualX / screenSize.width * gain * sensitivity
        let dy = residualY / screenSize.height * gain * sensitivity
        residualX = 0
        residualY = 0
        smoothedX = 0
        smoothedY = 0
        return (dx.clamped(to: -1...1), dy.clamped(to: -1...1))
    }

    /// Called when the finger lifts, so a stale residual cannot bleed into the next gesture.
    public mutating func release() {
        residualX = 0
        residualY = 0
        smoothedX = 0
        smoothedY = 0
    }

    /// A two-finger swipe is scroll, not pointer movement: the TV expects pixel-equivalents with
    /// positive meaning "down". One point of finger travel is three pixels of content, which matches
    /// the launcher's row height.
    public static func scrollDelta(points dy: Double) -> Double {
        guard dy.isFinite else { return 0 }
        return (dy * 3).clamped(to: -4000...4000)
    }
}
