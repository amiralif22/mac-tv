import XCTest
@testable import RemoteKit

/// The touchpad's feel is arithmetic, so it can be tested: a swipe must move a predictable distance,
/// noise must not move the cursor at all, and nothing may escape the screen.
final class PointerBallisticsTests: XCTestCase {

    private let pad = PadSize(width: 350, height: 500)

    func testAScreenWideSweepCrossesAboutOneScreen() {
        var ballistics = PointerBallistics(smoothing: 1, deadZone: 0, sensitivity: 1)
        var total = 0.0
        for _ in 0..<35 {
            if let delta = ballistics.push(dx: 10, dy: 0, screenSize: pad) {
                total += delta.dx
            }
        }
        // 350 pt of finger travel across a 350 pt pad, 1.15 gain.
        XCTAssertEqual(total, 1.15, accuracy: 0.02)
    }

    func testJitterIsSmoothedAwayAndSlowTravelAccumulates() {
        // A thumb resting on glass wobbles; the filter keeps that under the dead zone.
        var jitter = PointerBallistics(smoothing: 0.55, deadZone: 0.6, sensitivity: 1)
        for index in 0..<40 {
            let direction = index.isMultiple(of: 2) ? 0.5 : -0.5
            XCTAssertNil(jitter.push(dx: direction, dy: 0, screenSize: pad))
        }

        // A deliberate slow drag is below the threshold per frame, but is never lost.
        var slow = PointerBallistics(smoothing: 1, deadZone: 0.6, sensitivity: 1)
        let emitted = (0..<10).compactMap { _ in slow.push(dx: 0.1, dy: 0, screenSize: pad) }
        XCTAssertEqual(emitted.count, 1)
    }

    func testDeltasNeverLeaveTheUnitSquare() {
        var ballistics = PointerBallistics(smoothing: 1, deadZone: 0, sensitivity: 2.5)
        for delta in [
            ballistics.push(dx: 4000, dy: -4000, screenSize: pad),
            ballistics.push(dx: -4000, dy: 4000, screenSize: pad),
        ].compactMap({ $0 }) {
            XCTAssertLessThanOrEqual(abs(delta.dx), 1)
            XCTAssertLessThanOrEqual(abs(delta.dy), 1)
        }
    }

    func testReleaseDropsResidualMovement() {
        var ballistics = PointerBallistics(smoothing: 1, deadZone: 5, sensitivity: 1)
        XCTAssertNil(ballistics.push(dx: 4, dy: 4, screenSize: pad))
        ballistics.release()
        XCTAssertNil(ballistics.push(dx: 4, dy: 4, screenSize: pad))
    }

    func testSensitivityIsClampedToTheProtocolRange() {
        XCTAssertEqual(PointerBallistics(sensitivity: 9).sensitivity, 2.5)
        XCTAssertEqual(PointerBallistics(sensitivity: 0).sensitivity, 0.4)
    }

    func testZeroSizedPadIsIgnored() {
        var ballistics = PointerBallistics()
        XCTAssertNil(ballistics.push(dx: 10, dy: 10, screenSize: PadSize(width: 0, height: 0)))
    }

    func testScrollIsBounded() {
        XCTAssertEqual(PointerBallistics.scrollDelta(points: 10), 30)
        XCTAssertEqual(PointerBallistics.scrollDelta(points: .infinity), 0)
        XCTAssertLessThanOrEqual(PointerBallistics.scrollDelta(points: 1_000_000), 4000)
    }
}
