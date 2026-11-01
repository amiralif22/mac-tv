import SwiftUI
import UIKit
import RemoteKit

/// The LG-style touchpad: drag to move a cursor on the TV, tap to click, hold to context-click, two
/// fingers to scroll.
///
/// It is a UIKit view rather than a SwiftUI gesture for three reasons, all of them latency:
///
/// * `UIPanGestureRecognizer` reports `numberOfTouches`, so one finger and two fingers can be told
///   apart without guessing;
/// * a `CADisplayLink` flushes accumulated samples exactly once per frame, which is what makes the
///   cursor smooth rather than bursty;
/// * nothing in the gesture path touches SwiftUI state, so no view invalidation happens mid-drag.
struct TouchpadView: View {
    @ObservedObject var model: RemoteModel
    @State private var touching = false

    private var connection: RemoteConnection { model.connection }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Color.white.opacity(touching ? 0.09 : 0.045))
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .strokeBorder(Theme.hairline, lineWidth: 1)

            VStack(spacing: 6) {
                Image(systemName: "cursorarrow.motionlines")
                    .font(.system(size: 18, weight: .light))
                Text(touching ? "Pointing" : "Drag to point")
                    .font(Theme.label)
                    .tracking(0.6)
            }
            .foregroundColor(touching ? Theme.phosphor : Theme.faint)
            .animation(Theme.press, value: touching)
            .allowsHitTesting(false)

            TouchpadSurface(
                sensitivity: model.sensitivity,
                onTouchChanged: { active in
                    touching = active
                    if active, model.hapticsEnabled { Haptics.tap() }
                },
                onPointer: { samples in connection.pointer(samples: samples) },
                onClick: {
                    connection.click()
                    if model.hapticsEnabled { Haptics.press() }
                },
                onLongPress: {
                    connection.longPress()
                    if model.hapticsEnabled { Haptics.press() }
                },
                onScroll: { dy in connection.scroll(dy: dy) }
            )
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
        .accessibilityElement()
        .accessibilityLabel("Touchpad")
        .accessibilityHint("Drag to move the pointer, tap to click")
    }
}

/// The UIKit half: recognisers in, protocol samples out.
private struct TouchpadSurface: UIViewRepresentable {
    var sensitivity: Double
    var onTouchChanged: (Bool) -> Void
    var onPointer: ([Wire.PointerSample]) -> Void
    var onClick: () -> Void
    var onLongPress: () -> Void
    var onScroll: (Double) -> Void

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.isMultipleTouchEnabled = true
        context.coordinator.attach(to: view, sensitivity: sensitivity)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.sensitivity = sensitivity
        context.coordinator.callbacks = self
    }

    func makeCoordinator() -> Coordinator { Coordinator(callbacks: self) }

    final class Coordinator: NSObject {
        var callbacks: TouchpadSurface
        var sensitivity: Double

        private var ballistics = PointerBallistics()
        private var pending: [Wire.PointerSample] = []
        private var displayLink: CADisplayLink?
        private var lastPoint: CGPoint?
        private var scrollBaseline: CGFloat?
        private weak var pad: UIView?

        init(callbacks: TouchpadSurface) {
            self.callbacks = callbacks
            self.sensitivity = callbacks.sensitivity
            super.init()
        }

        func attach(to view: UIView, sensitivity: Double) {
            self.pad = view
            self.sensitivity = sensitivity
            ballistics.sensitivity = sensitivity

            let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan))
            pan.minimumNumberOfTouches = 1
            pan.maximumNumberOfTouches = 2
            view.addGestureRecognizer(pan)

            // The hold must be added before the tap, so the tap can be made to wait for it.
            let hold = UILongPressGestureRecognizer(target: self, action: #selector(handleHold))
            hold.minimumPressDuration = 0.45
            view.addGestureRecognizer(hold)

            let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap))
            tap.require(toFail: hold)
            view.addGestureRecognizer(tap)
        }

        @objc private func handlePan(_ recognizer: UIPanGestureRecognizer) {
            guard let pad else { return }
            let location = recognizer.location(in: pad)

            switch recognizer.state {
            case .began:
                ballistics.sensitivity = sensitivity
                ballistics.release()
                lastPoint = location
                callbacks.onTouchChanged(true)
                startDisplayLink()

            case .changed:
                if recognizer.numberOfTouches >= 2 {
                    // Two fingers: scroll, with a baseline reset so lifting one finger is not a jump.
                    let baseline = scrollBaseline ?? location.y
                    let dy = location.y - baseline
                    scrollBaseline = baseline
                    if abs(dy) > 4 {
                        callbacks.onScroll(PointerBallistics.scrollDelta(points: Double(dy)))
                        scrollBaseline = location.y
                    }
                    lastPoint = location
                    return
                }
                scrollBaseline = nil

                guard let last = lastPoint else {
                    lastPoint = location
                    return
                }
                let size = PadSize(width: Double(pad.bounds.width), height: Double(pad.bounds.height))
                let delta = ballistics.push(
                    dx: Double(location.x - last.x),
                    dy: Double(location.y - last.y),
                    screenSize: size
                )
                lastPoint = location
                if let delta {
                    pending.append(Wire.PointerSample(dx: delta.dx, dy: delta.dy))
                }

            case .ended, .cancelled, .failed:
                flush()
                stopDisplayLink()
                ballistics.release()
                lastPoint = nil
                scrollBaseline = nil
                callbacks.onTouchChanged(false)

            default:
                break
            }
        }

        @objc private func handleTap() { callbacks.onClick() }
        @objc private func handleHold(_ recognizer: UILongPressGestureRecognizer) {
            guard recognizer.state == .began else { return }
            callbacks.onLongPress()
        }

        /// One battery tick per frame, and one protocol frame per tick at most.
        private func startDisplayLink() {
            guard displayLink == nil else { return }
            let link = CADisplayLink(target: self, selector: #selector(flush))
            link.preferredFramesPerSecond = 60
            link.add(to: .main, forMode: .common)
            displayLink = link
        }

        private func stopDisplayLink() {
            displayLink?.invalidate()
            displayLink = nil
        }

        @objc func flush() {
            guard !pending.isEmpty else { return }
            let batch = Array(pending.suffix(Wire.maxPointerBatch))
            pending.removeAll(keepingCapacity: true)
            callbacks.onPointer(batch)
        }

        deinit {
            displayLink?.invalidate()
        }
    }
}
